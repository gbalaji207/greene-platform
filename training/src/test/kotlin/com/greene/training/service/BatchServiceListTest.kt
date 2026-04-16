package com.greene.training.service

import com.greene.training.domain.BatchStatus
import com.greene.training.dto.BatchListItemResponse
import com.greene.training.dto.BatchResponse
import com.greene.training.entity.Batch
import com.greene.training.repository.BatchRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@ExtendWith(MockKExtension::class)
class BatchServiceListTest {

    @MockK
    private lateinit var batchRepository: BatchRepository

    @InjectMockKs
    private lateinit var batchService: BatchService

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val creatorId     = UUID.fromString("f7e6d5c4-b3a2-1098-fedc-ba9876543210")
    private val startDateTime = OffsetDateTime.of(2026, 5, 1, 9, 0, 0, 0, ZoneOffset.UTC)
    private val now           = OffsetDateTime.now()

    private fun buildBatch(
        status: BatchStatus,
        id: UUID = UUID.randomUUID(),
    ) = Batch(
        id            = id,
        name          = "Batch ${status.name}",
        description   = null,
        startDateTime = startDateTime,
        endDateTime   = null,
        location      = null,
        topics        = null,
        maxSeats      = 20,
        status        = status,
        trainingStatus = null,
        createdBy     = creatorId,
        createdAt     = now,
        updatedAt     = now,
    )

    private fun emptyPage(pageable: org.springframework.data.domain.Pageable) =
        PageImpl<Batch>(emptyList(), pageable, 0)

    // ── G1: No OPEN batches exist ─────────────────────────────────────────────

    @Test
    fun `listBatches - no OPEN batches exist - returns empty items with total 0`() {
        val pageable = PageRequest.of(0, 20)
        every { batchRepository.findAllByStatus(BatchStatus.OPEN, pageable) } returns emptyPage(pageable)

        val result = batchService.listBatches(page = 1, pageSize = 20, statusFilter = null, isPrivileged = false)

        assertEquals(0L,   result.total)
        assertEquals(1,    result.page)
        assertEquals(20,   result.pageSize)
        assertTrue(result.items.isEmpty())
    }

    // ── G2 / G3: Unauthenticated or CLIENT caller — always OPEN only, trimmed projection ──

    @Test
    fun `listBatches - unprivileged caller - returns only OPEN batches as BatchListItemResponse`() {
        val openBatch = buildBatch(BatchStatus.OPEN)
        val pageable  = PageRequest.of(0, 20)
        every { batchRepository.findAllByStatus(BatchStatus.OPEN, pageable) } returns
            PageImpl(listOf(openBatch), pageable, 1)

        val result = batchService.listBatches(page = 1, pageSize = 20, statusFilter = null, isPrivileged = false)

        assertEquals(1L,   result.total)
        assertEquals(1,    result.items.size)
        assertTrue(result.items[0] is BatchListItemResponse,
            "Expected BatchListItemResponse but got ${result.items[0]::class.simpleName}")
        val item = result.items[0] as BatchListItemResponse
        assertEquals(openBatch.id,   item.id)
        assertEquals(openBatch.name, item.name)
    }

    @Test
    fun `listBatches - CLIENT caller with status=DRAFT - silently ignores filter and returns OPEN batches only`() {
        val openBatch = buildBatch(BatchStatus.OPEN)
        val pageable  = PageRequest.of(0, 20)
        // Status filter must be ignored — repository is always called with OPEN
        every { batchRepository.findAllByStatus(BatchStatus.OPEN, pageable) } returns
            PageImpl(listOf(openBatch), pageable, 1)

        // Simulate: CLIENT passes statusFilter = DRAFT but isPrivileged = false
        val result = batchService.listBatches(page = 1, pageSize = 20, statusFilter = BatchStatus.DRAFT, isPrivileged = false)

        // Verify filter was silently ignored — repository called with OPEN, not DRAFT
        verify(exactly = 1) { batchRepository.findAllByStatus(BatchStatus.OPEN, pageable) }
        verify(exactly = 0) { batchRepository.findAllByStatusIn(any(), any()) }
        assertEquals(1, result.items.size)
    }

    // ── ADMIN caller — all statuses, full projection ──────────────────────────

    @Test
    fun `listBatches - privileged caller without status filter - returns all batches as BatchResponse`() {
        val draftBatch = buildBatch(BatchStatus.DRAFT)
        val openBatch  = buildBatch(BatchStatus.OPEN)
        val pageable   = PageRequest.of(0, 20)
        every { batchRepository.findAll(pageable) } returns
            PageImpl(listOf(draftBatch, openBatch), pageable, 2)

        val result = batchService.listBatches(page = 1, pageSize = 20, statusFilter = null, isPrivileged = true)

        assertEquals(2L,   result.total)
        assertEquals(2,    result.items.size)
        assertTrue(result.items[0] is BatchResponse,
            "Expected BatchResponse but got ${result.items[0]::class.simpleName}")
        verify(exactly = 0) { batchRepository.findAllByStatus(any(), any()) }
        verify(exactly = 1) { batchRepository.findAll(pageable) }
    }

    @Test
    fun `listBatches - privileged caller with status filter DRAFT - returns only DRAFT batches`() {
        val draftBatch = buildBatch(BatchStatus.DRAFT)
        val pageable   = PageRequest.of(0, 20)
        every { batchRepository.findAllByStatusIn(listOf(BatchStatus.DRAFT), pageable) } returns
            PageImpl(listOf(draftBatch), pageable, 1)

        val result = batchService.listBatches(page = 1, pageSize = 20, statusFilter = BatchStatus.DRAFT, isPrivileged = true)

        assertEquals(1L,   result.total)
        assertEquals(1,    result.items.size)
        val item = result.items[0] as BatchResponse
        assertEquals(BatchStatus.DRAFT, item.status)
        verify(exactly = 1) { batchRepository.findAllByStatusIn(listOf(BatchStatus.DRAFT), pageable) }
        verify(exactly = 0) { batchRepository.findAll(pageable) }
    }

    // ── Pagination passthrough ────────────────────────────────────────────────

    @Test
    fun `listBatches - page and pageSize are passed correctly to repository`() {
        val pageable = PageRequest.of(1, 10)  // page=2 → zero-based index 1
        every { batchRepository.findAllByStatus(BatchStatus.OPEN, pageable) } returns emptyPage(pageable)

        val result = batchService.listBatches(page = 2, pageSize = 10, statusFilter = null, isPrivileged = false)

        assertEquals(2,  result.page)
        assertEquals(10, result.pageSize)
        verify(exactly = 1) { batchRepository.findAllByStatus(BatchStatus.OPEN, pageable) }
    }
}

