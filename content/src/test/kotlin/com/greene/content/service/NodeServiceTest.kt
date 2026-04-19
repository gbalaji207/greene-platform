package com.greene.content.service

import com.greene.content.domain.ContentNode
import com.greene.content.domain.FileRole
import com.greene.content.domain.NodeStatus
import com.greene.content.domain.NodeType
import com.greene.content.dto.UpdateNodeRequest
import com.greene.content.repository.ContentFileRepository
import com.greene.content.repository.ContentNodeRepository
import com.greene.core.exception.PlatformException
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class NodeServiceTest {

    @MockK private lateinit var contentNodeRepository: ContentNodeRepository
    @MockK private lateinit var contentFileRepository: ContentFileRepository

    private lateinit var service: NodeService

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val nodeId = UUID.fromString("ee000000-0000-0000-0000-000000000001")
    private val now    = OffsetDateTime.now()

    private fun buildNode(nodeType: NodeType = NodeType.ITEM) = ContentNode(
        id        = nodeId,
        libraryId = UUID.fromString("dd000000-0000-0000-0000-000000000001"),
        nodeType  = nodeType,
        title     = "Week 1 Watering Guide",
        depth     = 1,
        status    = NodeStatus.DRAFT,
        createdAt = now,
        updatedAt = now,
    )

    @BeforeEach
    fun setUp() {
        service = NodeService(
            contentNodeRepository = contentNodeRepository,
            contentFileRepository = contentFileRepository,
        )
    }

    // ── Test 10 — Publish ITEM with no PRIMARY file → 422 ────────────────────

    @Test
    fun `updateNode - publishing ITEM with no PRIMARY file - throws NO_PRIMARY_FILE 422`() {
        every { contentNodeRepository.findById(nodeId) } returns Optional.of(buildNode(NodeType.ITEM))
        every { contentFileRepository.existsByNodeIdAndFileRole(nodeId, FileRole.PRIMARY) } returns false

        val ex = assertThrows<PlatformException> {
            service.updateNode(nodeId, UpdateNodeRequest(status = NodeStatus.PUBLISHED))
        }

        assertEquals("NO_PRIMARY_FILE",               ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Cannot publish a content item before saving content", ex.message)
    }

    // ── Test 11 — Publish ITEM with PRIMARY file → success ───────────────────

    @Test
    fun `updateNode - publishing ITEM with PRIMARY file present - succeeds without exception`() {
        val node = buildNode(NodeType.ITEM)
        every { contentNodeRepository.findById(nodeId) } returns Optional.of(node)
        every { contentFileRepository.existsByNodeIdAndFileRole(nodeId, FileRole.PRIMARY) } returns true
        every { contentNodeRepository.save(any()) } answers { firstArg() }

        assertDoesNotThrow {
            service.updateNode(nodeId, UpdateNodeRequest(status = NodeStatus.PUBLISHED))
        }

        verify(exactly = 1) { contentNodeRepository.save(match { it.status == NodeStatus.PUBLISHED }) }
    }

    // ── Test 12 — Publishing a FOLDER never checks content_files ─────────────

    @Test
    fun `updateNode - publishing FOLDER - does not check contentFileRepository`() {
        val node = buildNode(NodeType.FOLDER)
        every { contentNodeRepository.findById(nodeId) } returns Optional.of(node)
        every { contentNodeRepository.save(any()) } answers { firstArg() }

        assertDoesNotThrow {
            service.updateNode(nodeId, UpdateNodeRequest(status = NodeStatus.PUBLISHED))
        }

        verify(exactly = 0) { contentFileRepository.existsByNodeIdAndFileRole(any(), any()) }
    }
}

