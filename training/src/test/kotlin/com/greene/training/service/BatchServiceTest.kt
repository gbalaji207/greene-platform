package com.greene.training.service

import com.greene.core.exception.PlatformException
import com.greene.training.domain.BatchStatus
import com.greene.training.domain.BookingStatus
import com.greene.training.dto.CreateBatchRequest
import com.greene.training.dto.UpdateBatchDetailsRequest
import com.greene.training.dto.UpdateBatchStatusRequest
import com.greene.training.entity.Batch
import com.greene.training.entity.BatchStatusLog
import com.greene.training.entity.Booking
import com.greene.training.repository.BatchRepository
import com.greene.training.repository.BatchStatusLogRepository
import com.greene.training.repository.BookingRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
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

    @MockK
    private lateinit var bookingRepository: BookingRepository

    @MockK
    private lateinit var batchStatusLogRepository: BatchStatusLogRepository

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

    private fun buildBooking(
        id: UUID = UUID.randomUUID(),
        status: BookingStatus = BookingStatus.PENDING,
    ) = Booking(
        id        = id,
        batchId   = batchId,
        clientId  = UUID.randomUUID(),
        status    = status,
        createdAt = now,
        updatedAt = now,
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

    // ── updateBatchDetails ────────────────────────────────────────────────────

    // D3
    @Test
    fun `updateBatchDetails - batch not found - throws BATCH_NOT_FOUND 404`() {
        every { batchRepository.findById(batchId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchDetails(batchId, UpdateBatchDetailsRequest(name = "X"))
        }

        assertEquals("BATCH_NOT_FOUND",    ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    // D4
    @Test
    fun `updateBatchDetails - batch is CLOSED - throws BATCH_NOT_EDITABLE 422`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(status = BatchStatus.CLOSED))

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchDetails(batchId, UpdateBatchDetailsRequest(name = "X"))
        }

        assertEquals("BATCH_NOT_EDITABLE",              ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,   ex.httpStatus)
    }

    // D5
    @Test
    fun `updateBatchDetails - all fields null - throws AT_LEAST_ONE_FIELD_REQUIRED 400`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch())

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchDetails(batchId, UpdateBatchDetailsRequest())
        }

        assertEquals("AT_LEAST_ONE_FIELD_REQUIRED", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST,         ex.httpStatus)
    }

    // D6
    @Test
    fun `updateBatchDetails - maxSeats = 0 - throws VALIDATION_ERROR 400`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch())

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchDetails(batchId, UpdateBatchDetailsRequest(maxSeats = 0))
        }

        assertEquals("VALIDATION_ERROR",    ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        assertEquals("maxSeats",             ex.details[0].field)
    }

    // D8
    @Test
    fun `updateBatchDetails - endDateTime before startDateTime (both provided) - throws VALIDATION_ERROR 400`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch())

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchDetails(
                batchId,
                UpdateBatchDetailsRequest(
                    startDateTime = startDateTime,
                    endDateTime   = startDateTime.minusDays(1),
                ),
            )
        }

        assertEquals("VALIDATION_ERROR",    ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        assertEquals("endDateTime",          ex.details[0].field)
    }

    // D9
    @Test
    fun `updateBatchDetails - only endDateTime provided before existing startDateTime - throws VALIDATION_ERROR 400`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(start = startDateTime))

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchDetails(
                batchId,
                UpdateBatchDetailsRequest(endDateTime = startDateTime.minusDays(1)),
            )
        }

        assertEquals("VALIDATION_ERROR",    ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        assertEquals("endDateTime",          ex.details[0].field)
    }

    // D10
    @Test
    fun `updateBatchDetails - single field update (name) - only name changed, other fields unchanged`() {
        val original = buildBatch(status = BatchStatus.OPEN)
        val updated  = buildBatch(status = BatchStatus.OPEN).apply { name = "New Name" }
        every { batchRepository.findById(batchId) } returns Optional.of(original)
        every { batchRepository.save(any()) }        returns updated

        val result = batchService.updateBatchDetails(batchId, UpdateBatchDetailsRequest(name = "New Name"))

        assertEquals("New Name",          result.name)
        assertEquals(BatchStatus.OPEN,    result.status)
        assertNull(result.description)
        assertNull(result.location)
        assertNull(result.maxSeats)
        verify(exactly = 1) { batchRepository.save(match { it.name == "New Name" }) }
    }

    // D11
    @Test
    fun `updateBatchDetails - multiple fields update - all updated fields reflected in response`() {
        val original = buildBatch(status = BatchStatus.OPEN)
        val updated  = buildBatch(status = BatchStatus.OPEN).apply {
            name        = "Updated Batch"
            location    = "Anna Nagar"
            maxSeats    = 30
            endDateTime = endDateTime
        }
        every { batchRepository.findById(batchId) } returns Optional.of(original)
        every { batchRepository.save(any()) }        returns updated

        val result = batchService.updateBatchDetails(
            batchId,
            UpdateBatchDetailsRequest(name = "Updated Batch", location = "Anna Nagar", maxSeats = 30),
        )

        assertEquals("Updated Batch", result.name)
        assertEquals("Anna Nagar",    result.location)
        assertEquals(30,              result.maxSeats)
        verify(exactly = 1) {
            batchRepository.save(match {
                it.name == "Updated Batch" && it.location == "Anna Nagar" && it.maxSeats == 30
            })
        }
    }

    // ── updateBatchStatus ─────────────────────────────────────────────────────

    // S3
    @Test
    fun `updateBatchStatus - batch not found - throws BATCH_NOT_FOUND 404`() {
        every { batchRepository.findById(batchId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.OPEN), callerId)
        }

        assertEquals("BATCH_NOT_FOUND",    ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    // S6
    @Test
    fun `updateBatchStatus - status DRAFT - throws VALIDATION_ERROR 400`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(BatchStatus.OPEN))

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.DRAFT), callerId)
        }

        assertEquals("VALIDATION_ERROR",    ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    // S7
    @Test
    fun `updateBatchStatus - DRAFT to CLOSED - throws INVALID_BATCH_STATUS_TRANSITION 422`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(BatchStatus.DRAFT))

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.CLOSED), callerId)
        }

        assertEquals("INVALID_BATCH_STATUS_TRANSITION", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,   ex.httpStatus)
    }

    // S8
    @Test
    fun `updateBatchStatus - OPEN to OPEN - throws INVALID_BATCH_STATUS_TRANSITION 422`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(BatchStatus.OPEN))

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.OPEN), callerId)
        }

        assertEquals("INVALID_BATCH_STATUS_TRANSITION", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,   ex.httpStatus)
    }

    // S9
    @Test
    fun `updateBatchStatus - CLOSED to CLOSED - throws INVALID_BATCH_STATUS_TRANSITION 422`() {
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(BatchStatus.CLOSED))

        val ex = assertThrows<PlatformException> {
            batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.CLOSED), callerId)
        }

        assertEquals("INVALID_BATCH_STATUS_TRANSITION", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,   ex.httpStatus)
    }

    // S10
    @Test
    fun `updateBatchStatus - DRAFT to OPEN - batch saved with OPEN status, log written, no booking changes`() {
        val batch   = buildBatch(BatchStatus.DRAFT)
        val saved   = buildBatch(BatchStatus.OPEN)
        val logSlot = slot<BatchStatusLog>()

        every { batchRepository.findById(batchId) }    returns Optional.of(batch)
        every { batchRepository.save(any()) }           returns saved
        every { batchStatusLogRepository.save(capture(logSlot)) } returns BatchStatusLog(
            batchId = batchId, fromStatus = BatchStatus.DRAFT,
            toStatus = BatchStatus.OPEN, changedBy = callerId,
        )

        val result = batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.OPEN), callerId)

        assertEquals(BatchStatus.OPEN, result.status)
        assertEquals(BatchStatus.DRAFT, logSlot.captured.fromStatus)
        assertEquals(BatchStatus.OPEN,  logSlot.captured.toStatus)
        assertEquals(callerId,          logSlot.captured.changedBy)
        verify(exactly = 0) { bookingRepository.findAllByBatchIdAndStatus(any(), any()) }
        verify(exactly = 0) { bookingRepository.saveAll(any<List<Booking>>()) }
    }

    // S11
    @Test
    fun `updateBatchStatus - OPEN to CLOSED no pending bookings - batch saved, log written`() {
        val batch   = buildBatch(BatchStatus.OPEN)
        val saved   = buildBatch(BatchStatus.CLOSED)
        val logSlot = slot<BatchStatusLog>()

        every { batchRepository.findById(batchId) }    returns Optional.of(batch)
        every { bookingRepository.findAllByBatchIdAndStatus(batchId, BookingStatus.PENDING) } returns emptyList()
        every { bookingRepository.saveAll(any<List<Booking>>()) } returns emptyList()
        every { batchRepository.save(any()) }           returns saved
        every { batchStatusLogRepository.save(capture(logSlot)) } returns BatchStatusLog(
            batchId = batchId, fromStatus = BatchStatus.OPEN,
            toStatus = BatchStatus.CLOSED, changedBy = callerId,
        )

        val result = batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.CLOSED), callerId)

        assertEquals(BatchStatus.CLOSED,  result.status)
        assertEquals(BatchStatus.OPEN,    logSlot.captured.fromStatus)
        assertEquals(BatchStatus.CLOSED,  logSlot.captured.toStatus)
        assertEquals(callerId,            logSlot.captured.changedBy)
        verify(exactly = 1) { bookingRepository.findAllByBatchIdAndStatus(batchId, BookingStatus.PENDING) }
        verify(exactly = 1) { bookingRepository.saveAll(emptyList<Booking>()) }
    }

    // S12
    @Test
    fun `updateBatchStatus - OPEN to CLOSED with PENDING bookings - PENDING rejected, CONFIRMED unchanged, log written`() {
        val pendingBookingId   = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val confirmedBookingId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val pendingBooking     = buildBooking(id = pendingBookingId,   status = BookingStatus.PENDING)
        val confirmedBooking   = buildBooking(id = confirmedBookingId, status = BookingStatus.CONFIRMED)

        val batch           = buildBatch(BatchStatus.OPEN)
        val saved           = buildBatch(BatchStatus.CLOSED)
        val logSlot         = slot<BatchStatusLog>()
        val savedAllSlot    = slot<List<Booking>>()

        every { batchRepository.findById(batchId) } returns Optional.of(batch)
        every { bookingRepository.findAllByBatchIdAndStatus(batchId, BookingStatus.PENDING) } returns listOf(pendingBooking)
        every { bookingRepository.saveAll(capture(savedAllSlot)) } returns listOf(pendingBooking)
        every { batchRepository.save(any()) }           returns saved
        every { batchStatusLogRepository.save(capture(logSlot)) } returns BatchStatusLog(
            batchId = batchId, fromStatus = BatchStatus.OPEN,
            toStatus = BatchStatus.CLOSED, changedBy = callerId,
        )

        batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.CLOSED), callerId)

        // Pending booking was rejected with the correct note
        val rejectedBookings = savedAllSlot.captured
        assertEquals(1,                                      rejectedBookings.size)
        assertEquals(pendingBookingId,                       rejectedBookings[0].id)
        assertEquals(BookingStatus.REJECTED,                 rejectedBookings[0].status)
        assertEquals("Auto Rejected as batch closed.",       rejectedBookings[0].note)

        // Confirmed booking was NOT included in saveAll
        val rejectedIds = rejectedBookings.map { it.id }
        assert(!rejectedIds.contains(confirmedBookingId)) { "CONFIRMED booking must not be rejected" }

        // Log captured correctly
        assertEquals(BatchStatus.OPEN,   logSlot.captured.fromStatus)
        assertEquals(BatchStatus.CLOSED, logSlot.captured.toStatus)
        assertEquals(callerId,           logSlot.captured.changedBy)
    }

    // S13
    @Test
    fun `updateBatchStatus - CLOSED to OPEN - batch saved with OPEN status, log written`() {
        val batch   = buildBatch(BatchStatus.CLOSED)
        val saved   = buildBatch(BatchStatus.OPEN)
        val logSlot = slot<BatchStatusLog>()

        every { batchRepository.findById(batchId) }    returns Optional.of(batch)
        every { batchRepository.save(any()) }           returns saved
        every { batchStatusLogRepository.save(capture(logSlot)) } returns BatchStatusLog(
            batchId = batchId, fromStatus = BatchStatus.CLOSED,
            toStatus = BatchStatus.OPEN, changedBy = callerId,
        )

        val result = batchService.updateBatchStatus(batchId, UpdateBatchStatusRequest(BatchStatus.OPEN), callerId)

        assertEquals(BatchStatus.OPEN,   result.status)
        assertEquals(BatchStatus.CLOSED, logSlot.captured.fromStatus)
        assertEquals(BatchStatus.OPEN,   logSlot.captured.toStatus)
        assertEquals(callerId,           logSlot.captured.changedBy)
        verify(exactly = 0) { bookingRepository.findAllByBatchIdAndStatus(any(), any()) }
    }
}
