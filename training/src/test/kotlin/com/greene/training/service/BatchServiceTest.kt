package com.greene.training.service

import com.greene.core.exception.PlatformException
import com.greene.training.domain.BatchStatus
import com.greene.training.dto.CreateBatchRequest
import com.greene.training.entity.Batch
import com.greene.training.repository.BatchRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class BatchServiceTest {

    @MockK
    private lateinit var batchRepository: BatchRepository

    @InjectMockKs
    private lateinit var batchService: BatchService

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val callerId      = UUID.fromString("f7e6d5c4-b3a2-1098-fedc-ba9876543210")
    private val batchId       = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private val startDateTime = OffsetDateTime.of(2026, 5, 1, 9, 0, 0, 0, ZoneOffset.UTC)
    private val endDateTime   = OffsetDateTime.of(2026, 5, 15, 9, 0, 0, 0, ZoneOffset.UTC)
    private val now           = OffsetDateTime.now()

    private fun buildBatch(
        status: BatchStatus = BatchStatus.DRAFT,
        start: OffsetDateTime = startDateTime,
        end: OffsetDateTime? = null,
    ) = Batch(
        id             = batchId,
        name           = "Batch April 2026",
        description    = null,
        startDateTime  = start,
        endDateTime    = end,
        location       = null,
        topics         = null,
        maxSeats       = null,
        status         = status,
        trainingStatus = null,
        createdBy      = callerId,
        createdAt      = now,
        updatedAt      = now,
    )

    private fun request(
        name: String?            = "Batch April 2026",
        start: OffsetDateTime?   = startDateTime,
        end: OffsetDateTime?     = null,
        status: BatchStatus?     = null,
    ) = CreateBatchRequest(
        name          = name,
        startDateTime = start,
        endDateTime   = end,
        status        = status,
    )

    // ── createBatch – happy paths ─────────────────────────────────────────────

    @Test
    fun `createBatch - explicit OPEN status - saves entity and returns BatchResponse with OPEN`() {
        every { batchRepository.save(any()) } returns buildBatch(status = BatchStatus.OPEN)

        val result = batchService.createBatch(request(status = BatchStatus.OPEN), callerId)

        assertEquals(batchId,           result.id)
        assertEquals(BatchStatus.OPEN,  result.status)
        assertEquals(callerId,          result.createdBy)
        assertNull(result.trainingStatus)
        verify(exactly = 1) { batchRepository.save(match { it.status == BatchStatus.OPEN }) }
    }

    @Test
    fun `createBatch - explicit DRAFT status - saves entity and returns BatchResponse with DRAFT`() {
        every { batchRepository.save(any()) } returns buildBatch(status = BatchStatus.DRAFT)

        val result = batchService.createBatch(request(status = BatchStatus.DRAFT), callerId)

        assertEquals(BatchStatus.DRAFT, result.status)
        assertEquals(batchId,           result.id)
        verify(exactly = 1) { batchRepository.save(match { it.status == BatchStatus.DRAFT }) }
    }

    @Test
    fun `createBatch - status omitted - defaults to DRAFT and saves entity with DRAFT`() {
        every { batchRepository.save(any()) } returns buildBatch(status = BatchStatus.DRAFT)

        val result = batchService.createBatch(request(status = null), callerId)

        assertEquals(BatchStatus.DRAFT, result.status)
        verify { batchRepository.save(match { it.status == BatchStatus.DRAFT }) }
    }

    // ── createBatch – INVALID_BATCH_STATUS ───────────────────────────────────

    @Test
    fun `createBatch - status CLOSED - throws INVALID_BATCH_STATUS 400`() {
        val ex = assertThrows<PlatformException> {
            batchService.createBatch(request(status = BatchStatus.CLOSED), callerId)
        }

        assertEquals("INVALID_BATCH_STATUS",                              ex.code)
        assertEquals(HttpStatus.BAD_REQUEST,                              ex.httpStatus)
        assertEquals("Batch can only be created with status DRAFT or OPEN", ex.message)
    }

    @Test
    fun `createBatch - status COMPLETED - throws INVALID_BATCH_STATUS 400`() {
        val ex = assertThrows<PlatformException> {
            batchService.createBatch(request(status = BatchStatus.COMPLETED), callerId)
        }

        assertEquals("INVALID_BATCH_STATUS", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST,  ex.httpStatus)
    }

    // ── createBatch – date-time cross-field validation ────────────────────────

    @Test
    fun `createBatch - endDateTime before startDateTime - throws VALIDATION_ERROR 400 with field detail`() {
        val req = request(start = startDateTime, end = startDateTime.minusDays(1))

        val ex = assertThrows<PlatformException> {
            batchService.createBatch(req, callerId)
        }

        assertEquals("VALIDATION_ERROR",                              ex.code)
        assertEquals(HttpStatus.BAD_REQUEST,                          ex.httpStatus)
        assertEquals("Request validation failed",                     ex.message)
        assertEquals(1,                                               ex.details.size)
        assertEquals("endDateTime",                                   ex.details[0].field)
        assertEquals("end date time must be after start date time",   ex.details[0].message)
    }

    @Test
    fun `createBatch - endDateTime equal to startDateTime - throws VALIDATION_ERROR 400`() {
        val req = request(start = startDateTime, end = startDateTime)

        val ex = assertThrows<PlatformException> {
            batchService.createBatch(req, callerId)
        }

        assertEquals("VALIDATION_ERROR",  ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        assertEquals("endDateTime",          ex.details[0].field)
    }

    @Test
    fun `createBatch - endDateTime after startDateTime - succeeds`() {
        every { batchRepository.save(any()) } returns buildBatch(start = startDateTime, end = endDateTime)

        val result = batchService.createBatch(request(start = startDateTime, end = endDateTime), callerId)

        assertEquals(startDateTime, result.startDateTime)
        assertEquals(endDateTime,   result.endDateTime)
    }

    // ── getBatch ──────────────────────────────────────────────────────────────

    @Test
    fun `getBatch - batch exists - returns BatchResponse with all fields`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch())

        val result = batchService.getBatch(batchId)

        assertEquals(batchId,            result.id)
        assertEquals("Batch April 2026", result.name)
        assertEquals(BatchStatus.DRAFT,  result.status)
        assertEquals(callerId,           result.createdBy)
        assertNull(result.trainingStatus)
    }

    @Test
    fun `getBatch - id not found - throws BATCH_NOT_FOUND 404`() {
        every { batchRepository.findById(batchId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            batchService.getBatch(batchId)
        }

        assertEquals("BATCH_NOT_FOUND",    ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
        assertEquals("Batch not found",    ex.message)
    }
}

