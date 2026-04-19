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
    private val contentProperties = ContentProperties(maxArticleSizeKb = 500)

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
}


