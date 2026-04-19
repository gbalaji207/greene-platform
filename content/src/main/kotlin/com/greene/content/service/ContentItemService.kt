package com.greene.content.service

import com.greene.content.config.ContentProperties
import com.greene.content.domain.ContentFile
import com.greene.content.domain.FileRole
import com.greene.content.domain.ItemType
import com.greene.content.domain.LibraryStatus
import com.greene.content.domain.NodeType
import com.greene.content.dto.InlineImageUploadResponse
import com.greene.content.dto.SaveArticleContentRequest
import com.greene.content.dto.SaveArticleContentResponse
import com.greene.content.repository.ContentFileRepository
import com.greene.content.repository.ContentItemDetailsRepository
import com.greene.content.repository.ContentLibraryRepository
import com.greene.content.repository.ContentNodeRepository
import com.greene.core.exception.PlatformException
import com.greene.core.storage.StorageService
import com.greene.core.util.ImageTypeDetector
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ContentItemService(
    private val contentNodeRepository: ContentNodeRepository,
    private val contentLibraryRepository: ContentLibraryRepository,
    private val contentItemDetailsRepository: ContentItemDetailsRepository,
    private val contentFileRepository: ContentFileRepository,
    private val storageService: StorageService,
    private val contentProperties: ContentProperties,
) {

    @Transactional
    fun saveArticleContent(
        nodeId: UUID,
        request: SaveArticleContentRequest,
        actor: UUID,
    ): SaveArticleContentResponse {

        // Step 1 — load node
        val node = contentNodeRepository.findById(nodeId)
            .orElseThrow {
                PlatformException(
                    "NODE_NOT_FOUND",
                    "Content node with id $nodeId not found",
                    HttpStatus.NOT_FOUND
                )
            }

        // Step 2 — load library; guard against ARCHIVED
        val library = contentLibraryRepository.findById(node.libraryId)
            .orElseThrow { IllegalStateException("Library ${node.libraryId} missing for node $nodeId") }

        if (library.status == LibraryStatus.ARCHIVED) {
            throw PlatformException(
                "LIBRARY_ARCHIVED",
                "Cannot modify content in an archived library",
                HttpStatus.UNPROCESSABLE_ENTITY
            )
        }

        // Step 3 — validate node type: must be ITEM/ARTICLE
        val itemDetails = if (node.nodeType == NodeType.ITEM) {
            contentItemDetailsRepository.findByNodeId(nodeId)
        } else null

        if (node.nodeType != NodeType.ITEM || itemDetails?.itemType != ItemType.ARTICLE) {
            throw PlatformException(
                "NODE_TYPE_MISMATCH",
                "Content save is only supported for ARTICLE nodes",
                HttpStatus.UNPROCESSABLE_ENTITY
            )
        }

        // Step 4 — convert to bytes
        val bytes = request.htmlContent!!.toByteArray(Charsets.UTF_8)

        // Step 5 — size check
        val maxBytes = contentProperties.maxArticleSizeKb * 1024L
        if (bytes.size > maxBytes) {
            throw PlatformException(
                "FILE_TOO_LARGE",
                "Article content exceeds the maximum allowed size of ${contentProperties.maxArticleSizeKb} KB",
                HttpStatus.PAYLOAD_TOO_LARGE
            )
        }

        // Step 6 — upload to object storage
        val fileKey = "content/${nodeId}/primary.html"
        storageService.upload(fileKey, bytes, "text/html")

        // Step 7 — upsert content_files
        val existing = contentFileRepository.findByNodeIdAndFileRole(nodeId, FileRole.PRIMARY)
        if (existing != null) {
            // Delete the old row so createdAt refreshes on the new insert
            contentFileRepository.delete(existing)
            contentFileRepository.flush()
        }
        val contentFile = ContentFile(
            nodeId = nodeId,
            fileKey = fileKey,
            mimeType = "text/html",
            sizeBytes = bytes.size.toLong(),
            fileRole = FileRole.PRIMARY,
            sortOrder = 0,
        )
        val savedFile = contentFileRepository.save(contentFile)

        // Step 8 — update summary if provided
        if (request.summary != null) {
            itemDetails.summary = request.summary
            contentItemDetailsRepository.save(itemDetails)
        }

        // Step 9 — touch node.updatedAt
        node.updatedAt = OffsetDateTime.now()
        contentNodeRepository.save(node)

        // Step 10 — return response
        return SaveArticleContentResponse(
            nodeId = nodeId,
            hasFile = true,
            updatedAt = savedFile.createdAt,
        )
    }

    fun uploadInlineImage(nodeId: UUID, file: MultipartFile): InlineImageUploadResponse {

        // Step 1 — detect image type from magic bytes
        val header = file.inputStream.use { it.readNBytes(8) }
        val contentType = ImageTypeDetector.detect(header)
            ?: throw PlatformException(
                code = "INVALID_FILE_TYPE",
                message = "Only image/jpeg, image/png, and image/gif files are accepted",
                httpStatus = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            )

        // Step 2 — size guard (checked after type so the type error surfaces first)
        if (file.size > contentProperties.maxImageSizeKb * 1024) {
            throw PlatformException(
                code = "FILE_TOO_LARGE",
                message = "Image size must not exceed ${contentProperties.maxImageSizeKb} KB",
                httpStatus = HttpStatus.PAYLOAD_TOO_LARGE,
            )
        }

        // Step 3 — load node
        val node = contentNodeRepository.findById(nodeId)
            .orElseThrow {
                PlatformException(
                    code = "NODE_NOT_FOUND",
                    message = "Node not found",
                    httpStatus = HttpStatus.NOT_FOUND,
                )
            }

        // Step 4 — load item details and validate ARTICLE type
        val itemDetails = if (node.nodeType == NodeType.ITEM)
            contentItemDetailsRepository.findByNodeId(node.id)
        else null

        if (node.nodeType != NodeType.ITEM || itemDetails?.itemType != ItemType.ARTICLE) {
            throw PlatformException(
                code = "NODE_TYPE_MISMATCH",
                message = "Inline image upload is only supported for ARTICLE nodes",
                httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }

        // Step 5 — load library; guard against ARCHIVED
        val library = contentLibraryRepository.findById(node.libraryId)
            .orElseThrow { IllegalStateException("Library ${node.libraryId} missing for node $nodeId") }

        if (library.status == LibraryStatus.ARCHIVED) {
            throw PlatformException(
                code = "LIBRARY_ARCHIVED",
                message = "Cannot modify content in an archived library",
                httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }

        // Step 6 — generate storage key and upload
        val ext = contentType.second
        val key = "content/$nodeId/images/${UUID.randomUUID()}.$ext"
        storageService.upload(key, file.bytes, contentType.first)

        // Step 7 — persist content_files row (INSERT, not upsert)
        val saved = contentFileRepository.save(
            ContentFile(
                nodeId    = nodeId,
                fileKey   = key,
                mimeType  = contentType.first,
                sizeBytes = file.size,
                fileRole  = FileRole.INLINE_IMAGE,
                sortOrder = 0,
            )
        )

        // Step 8 — return response
        return InlineImageUploadResponse(
            fileId    = saved.id,
            fileKey   = key,
            mimeType  = contentType.first,
            sizeBytes = file.size,
        )
    }
}


