package com.greene.content.service

import com.greene.content.config.ContentProperties
import com.greene.content.domain.ContentFile
import com.greene.content.domain.ContentItemDetails
import com.greene.content.domain.ContentLibrary
import com.greene.content.domain.ContentNode
import com.greene.content.domain.FileRole
import com.greene.content.domain.ItemType
import com.greene.content.domain.LibraryStatus
import com.greene.content.domain.NodeStatus
import com.greene.content.domain.NodeType
import com.greene.content.dto.SaveArticleContentRequest
import com.greene.content.repository.ContentFileRepository
import com.greene.content.repository.ContentItemDetailsRepository
import com.greene.content.repository.ContentLibraryRepository
import com.greene.content.repository.ContentNodeRepository
import com.greene.core.exception.PlatformException
import com.greene.core.storage.StorageService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class ContentItemServiceTest {

    @MockK private lateinit var contentNodeRepository: ContentNodeRepository
    @MockK private lateinit var contentLibraryRepository: ContentLibraryRepository
    @MockK private lateinit var contentItemDetailsRepository: ContentItemDetailsRepository
    @MockK private lateinit var contentFileRepository: ContentFileRepository
    @MockK private lateinit var storageService: StorageService

    // contentProperties is a data class — use a real instance rather than a mock
    private val contentProperties = ContentProperties(maxArticleSizeKb = 500, maxImageSizeKb = 2048)

    private lateinit var service: ContentItemService

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val actorId   = UUID.fromString("aa000000-0000-0000-0000-000000000001")
    private val libraryId = UUID.fromString("dd000000-0000-0000-0000-000000000001")
    private val nodeId    = UUID.fromString("ee000000-0000-0000-0000-000000000001")
    private val now       = OffsetDateTime.now()

    private fun buildNode(
        nodeType: NodeType = NodeType.ITEM,
    ) = ContentNode(
        id        = nodeId,
        libraryId = libraryId,
        nodeType  = nodeType,
        title     = "Week 1 Watering Guide",
        depth     = 1,
        status    = NodeStatus.DRAFT,
        createdAt = now,
        updatedAt = now,
    )

    private fun buildLibrary(status: LibraryStatus = LibraryStatus.PUBLISHED) =
        ContentLibrary(
            id        = libraryId,
            name      = "Microgreens Basics",
            status    = status,
            createdBy = actorId,
            createdAt = now,
            updatedAt = now,
        )

    private fun buildItemDetails(itemType: ItemType = ItemType.ARTICLE) =
        ContentItemDetails(
            id       = UUID.fromString("ff000000-0000-0000-0000-000000000001"),
            nodeId   = nodeId,
            itemType = itemType,
        )

    private fun buildContentFile() = ContentFile(
        nodeId    = nodeId,
        fileKey   = "content/$nodeId/primary.html",
        mimeType  = "text/html",
        sizeBytes = 100L,
        fileRole  = FileRole.PRIMARY,
        sortOrder = 0,
    )

    private fun validRequest(summary: String? = null) = SaveArticleContentRequest(
        htmlContent = "<h1>Week 1</h1><p>Water twice daily.</p>",
        summary     = summary,
    )

    /** Stubs the full happy-path chain. Individual tests override as needed. */
    private fun stubHappyPath(
        itemType: ItemType    = ItemType.ARTICLE,
        libraryStatus: LibraryStatus = LibraryStatus.PUBLISHED,
        existingFile: ContentFile? = null,
    ) {
        every { contentNodeRepository.findById(nodeId) } returns Optional.of(buildNode())
        every { contentLibraryRepository.findById(libraryId) } returns Optional.of(buildLibrary(libraryStatus))
        every { contentItemDetailsRepository.findByNodeId(nodeId) } returns buildItemDetails(itemType)
        every { contentFileRepository.findByNodeIdAndFileRole(nodeId, FileRole.PRIMARY) } returns existingFile
        if (existingFile != null) {
            justRun { contentFileRepository.delete(existingFile) }
            justRun { contentFileRepository.flush() }
        }
        every { contentFileRepository.save(any()) } answers { firstArg() }
        every { contentItemDetailsRepository.save(any()) } answers { firstArg() }
        every { contentNodeRepository.save(any()) } answers { firstArg() }
        justRun { storageService.upload(any(), any(), any()) }
    }

    @BeforeEach
    fun setUp() {
        service = ContentItemService(
            contentNodeRepository        = contentNodeRepository,
            contentLibraryRepository     = contentLibraryRepository,
            contentItemDetailsRepository = contentItemDetailsRepository,
            contentFileRepository        = contentFileRepository,
            storageService               = storageService,
            contentProperties            = contentProperties,
        )
    }

    // ── Test 1 — Happy path ───────────────────────────────────────────────────

    @Test
    fun `saveArticleContent - valid ARTICLE node under size limit - uploads and returns hasFile=true`() {
        stubHappyPath()

        val response = service.saveArticleContent(nodeId, validRequest(), actorId)

        assertTrue(response.hasFile)
        assertEquals(nodeId, response.nodeId)
        verify(exactly = 1) {
            storageService.upload("content/$nodeId/primary.html", any(), "text/html")
        }
        verify(exactly = 1) { contentFileRepository.save(any()) }
    }

    // ── Test 2 — Node not found ───────────────────────────────────────────────

    @Test
    fun `saveArticleContent - node not found - throws NODE_NOT_FOUND 404`() {
        every { contentNodeRepository.findById(nodeId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            service.saveArticleContent(nodeId, validRequest(), actorId)
        }

        assertEquals("NODE_NOT_FOUND",   ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
        assertEquals("Content node with id $nodeId not found", ex.message)
    }

    // ── Test 3 — Library archived ─────────────────────────────────────────────

    @Test
    fun `saveArticleContent - library is ARCHIVED - throws LIBRARY_ARCHIVED 422`() {
        stubHappyPath(libraryStatus = LibraryStatus.ARCHIVED)

        val ex = assertThrows<PlatformException> {
            service.saveArticleContent(nodeId, validRequest(), actorId)
        }

        assertEquals("LIBRARY_ARCHIVED",              ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Cannot modify content in an archived library", ex.message)
    }

    // ── Test 4 — Node is a FOLDER ─────────────────────────────────────────────

    @Test
    fun `saveArticleContent - node is FOLDER - throws NODE_TYPE_MISMATCH 422`() {
        every { contentNodeRepository.findById(nodeId) } returns
            Optional.of(buildNode(nodeType = NodeType.FOLDER))
        every { contentLibraryRepository.findById(libraryId) } returns
            Optional.of(buildLibrary())

        val ex = assertThrows<PlatformException> {
            service.saveArticleContent(nodeId, validRequest(), actorId)
        }

        assertEquals("NODE_TYPE_MISMATCH",            ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Content save is only supported for ARTICLE nodes", ex.message)
    }

    // ── Test 5 — Node is a VIDEO ITEM ─────────────────────────────────────────

    @Test
    fun `saveArticleContent - node is VIDEO ITEM - throws NODE_TYPE_MISMATCH 422`() {
        every { contentNodeRepository.findById(nodeId) } returns Optional.of(buildNode())
        every { contentLibraryRepository.findById(libraryId) } returns Optional.of(buildLibrary())
        every { contentItemDetailsRepository.findByNodeId(nodeId) } returns buildItemDetails(ItemType.VIDEO)

        val ex = assertThrows<PlatformException> {
            service.saveArticleContent(nodeId, validRequest(), actorId)
        }

        assertEquals("NODE_TYPE_MISMATCH",            ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Content save is only supported for ARTICLE nodes", ex.message)
    }

    // ── Test 6 — Content exceeds size limit ───────────────────────────────────

    @Test
    fun `saveArticleContent - htmlContent exceeds 500 KB - throws FILE_TOO_LARGE 413 without uploading`() {
        stubHappyPath()
        val oversizedContent = "x".repeat(500 * 1024 + 1)   // 512 001 bytes in ASCII/UTF-8
        val request = SaveArticleContentRequest(htmlContent = oversizedContent)

        val ex = assertThrows<PlatformException> {
            service.saveArticleContent(nodeId, request, actorId)
        }

        assertEquals("FILE_TOO_LARGE",              ex.code)
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE,  ex.httpStatus)
        verify(exactly = 0) { storageService.upload(any(), any(), any()) }
    }

    // ── Test 7 — Upsert: second call deletes old row then inserts fresh one ───

    @Test
    fun `saveArticleContent - existing PRIMARY row present - deletes old row then inserts new one`() {
        val existingFile = buildContentFile()
        stubHappyPath(existingFile = existingFile)

        service.saveArticleContent(nodeId, validRequest(), actorId)

        verify(exactly = 1) { contentFileRepository.delete(existingFile) }
        verify(exactly = 1) { contentFileRepository.flush() }
        // save called exactly once for the fresh insert (not again for update)
        verify(exactly = 1) { contentFileRepository.save(any()) }
    }

    @Test
    fun `saveArticleContent - no existing PRIMARY row - inserts without delete`() {
        stubHappyPath(existingFile = null)

        service.saveArticleContent(nodeId, validRequest(), actorId)

        verify(exactly = 0) { contentFileRepository.delete(any<ContentFile>()) }
        verify(exactly = 1) { contentFileRepository.save(any()) }
    }

    // ── Test 8 — Summary updated when provided ────────────────────────────────

    @Test
    fun `saveArticleContent - summary provided - updates itemDetails summary and saves`() {
        stubHappyPath()
        val request = validRequest(summary = "Updated summary")

        service.saveArticleContent(nodeId, request, actorId)

        verify(exactly = 1) {
            contentItemDetailsRepository.save(match { it.summary == "Updated summary" })
        }
    }

    // ── Test 9 — Summary null → itemDetails NOT touched ──────────────────────

    @Test
    fun `saveArticleContent - summary null - does not save itemDetails`() {
        stubHappyPath()

        service.saveArticleContent(nodeId, validRequest(summary = null), actorId)

        verify(exactly = 0) { contentItemDetailsRepository.save(any()) }
    }

    // =========================================================================
    // uploadInlineImage tests
    // =========================================================================

    // ── Magic-byte helpers ────────────────────────────────────────────────────

    private fun jpegBytes() = ByteArray(16).also {
        it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte()
    }

    private fun pngBytes() = ByteArray(16).also {
        it[0] = 0x89.toByte(); it[1] = 0x50.toByte()
        it[2] = 0x4E.toByte(); it[3] = 0x47.toByte()
    }

    private fun gifBytes() = ByteArray(16).also {
        it[0] = 0x47.toByte(); it[1] = 0x49.toByte()
        it[2] = 0x46.toByte(); it[3] = 0x38.toByte()
    }

    private fun pdfBytes() = ByteArray(16).also {
        it[0] = 0x25.toByte(); it[1] = 0x50.toByte()
        it[2] = 0x44.toByte(); it[3] = 0x46.toByte()
    }

    private fun imageFile(name: String, content: ByteArray) =
        MockMultipartFile("file", name, "application/octet-stream", content)

    /** Stubs the happy-path chain for uploadInlineImage. */
    private fun stubImageHappyPath(
        nodeType: NodeType       = NodeType.ITEM,
        itemType: ItemType       = ItemType.ARTICLE,
        libraryStatus: LibraryStatus = LibraryStatus.PUBLISHED,
    ) {
        every { contentNodeRepository.findById(nodeId) } returns
            Optional.of(buildNode(nodeType))
        every { contentItemDetailsRepository.findByNodeId(nodeId) } returns
            buildItemDetails(itemType)
        every { contentLibraryRepository.findById(libraryId) } returns
            Optional.of(buildLibrary(libraryStatus))
        every { contentFileRepository.save(any()) } answers { firstArg() }
        justRun { storageService.upload(any(), any(), any()) }
    }

    // ── Test 10 — Valid JPEG ──────────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - valid JPEG magic bytes - inserts row and returns correct response`() {
        stubImageHappyPath()
        val file = imageFile("photo.jpg", jpegBytes())

        val response = service.uploadInlineImage(nodeId, file)

        assertEquals("image/jpeg", response.mimeType)
        assertTrue(response.fileKey.startsWith("content/$nodeId/images/"))
        assertTrue(response.fileKey.endsWith(".jpg"))
        verify(exactly = 1) { storageService.upload(any(), any(), "image/jpeg") }
        verify(exactly = 1) { contentFileRepository.save(any()) }
    }

    // ── Test 11 — Valid PNG ───────────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - valid PNG magic bytes - returns png extension and image-png mime type`() {
        stubImageHappyPath()
        val file = imageFile("image.png", pngBytes())

        val response = service.uploadInlineImage(nodeId, file)

        assertEquals("image/png", response.mimeType)
        assertTrue(response.fileKey.endsWith(".png"))
    }

    // ── Test 12 — Valid GIF ───────────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - valid GIF magic bytes - returns gif extension and image-gif mime type`() {
        stubImageHappyPath()
        val file = imageFile("anim.gif", gifBytes())

        val response = service.uploadInlineImage(nodeId, file)

        assertEquals("image/gif", response.mimeType)
        assertTrue(response.fileKey.endsWith(".gif"))
    }

    // ── Test 13 — PDF magic bytes ─────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - PDF magic bytes - throws INVALID_FILE_TYPE 415 without uploading`() {
        val file = imageFile("doc.pdf", pdfBytes())

        val ex = assertThrows<PlatformException> {
            service.uploadInlineImage(nodeId, file)
        }

        assertEquals("INVALID_FILE_TYPE",             ex.code)
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.httpStatus)
        verify(exactly = 0) { storageService.upload(any(), any(), any()) }
    }

    // ── Test 14 — File too large ──────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - file size exceeds maxImageSizeKb - throws FILE_TOO_LARGE 413 without uploading`() {
        // 2049 KB — one KB over the 2048 KB limit
        val oversizedContent = jpegBytes() + ByteArray(2049 * 1024)
        val file = imageFile("big.jpg", oversizedContent)

        val ex = assertThrows<PlatformException> {
            service.uploadInlineImage(nodeId, file)
        }

        assertEquals("FILE_TOO_LARGE",            ex.code)
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.httpStatus)
        verify(exactly = 0) { storageService.upload(any(), any(), any()) }
    }

    // ── Test 15 — Node not found ──────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - node not found - throws NODE_NOT_FOUND 404`() {
        every { contentNodeRepository.findById(nodeId) } returns Optional.empty()
        val file = imageFile("photo.jpg", jpegBytes())

        val ex = assertThrows<PlatformException> {
            service.uploadInlineImage(nodeId, file)
        }

        assertEquals("NODE_NOT_FOUND",     ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    // ── Test 16 — FOLDER node ─────────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - node is FOLDER - throws NODE_TYPE_MISMATCH 422`() {
        stubImageHappyPath(nodeType = NodeType.FOLDER)
        val file = imageFile("photo.jpg", jpegBytes())

        val ex = assertThrows<PlatformException> {
            service.uploadInlineImage(nodeId, file)
        }

        assertEquals("NODE_TYPE_MISMATCH",             ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,  ex.httpStatus)
    }

    // ── Test 17 — VIDEO item ──────────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - node is VIDEO ITEM - throws NODE_TYPE_MISMATCH 422`() {
        stubImageHappyPath(itemType = ItemType.VIDEO)
        val file = imageFile("photo.jpg", jpegBytes())

        val ex = assertThrows<PlatformException> {
            service.uploadInlineImage(nodeId, file)
        }

        assertEquals("NODE_TYPE_MISMATCH",             ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,  ex.httpStatus)
    }

    // ── Test 18 — Library archived ────────────────────────────────────────────

    @Test
    fun `uploadInlineImage - library is ARCHIVED - throws LIBRARY_ARCHIVED 422`() {
        stubImageHappyPath(libraryStatus = LibraryStatus.ARCHIVED)
        val file = imageFile("photo.jpg", jpegBytes())

        val ex = assertThrows<PlatformException> {
            service.uploadInlineImage(nodeId, file)
        }

        assertEquals("LIBRARY_ARCHIVED",               ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,  ex.httpStatus)
    }

    // ── Test 19 — Two uploads → two INSERT rows ───────────────────────────────

    @Test
    fun `uploadInlineImage - called twice for same node - inserts two rows without deleting`() {
        stubImageHappyPath()
        val file = imageFile("photo.jpg", jpegBytes())

        service.uploadInlineImage(nodeId, file)
        service.uploadInlineImage(nodeId, file)

        verify(exactly = 2) { contentFileRepository.save(any()) }
        verify(exactly = 0) { contentFileRepository.delete(any<ContentFile>()) }
    }
}


