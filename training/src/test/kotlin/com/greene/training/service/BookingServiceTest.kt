package com.greene.training.service

import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import com.greene.training.domain.BatchStatus
import com.greene.training.domain.BookingStatus
import com.greene.training.dto.UpdateBookingStatusRequest
import com.greene.training.entity.Batch
import com.greene.training.entity.Booking
import com.greene.training.repository.BatchRepository
import com.greene.training.repository.BookingRepository
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class BookingServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var batchRepository: BatchRepository

    @MockK
    private lateinit var bookingRepository: BookingRepository

    @InjectMockKs
    private lateinit var bookingService: BookingService

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val clientId  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val batchId   = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val bookingId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val staffId   = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
    private val now       = OffsetDateTime.of(2026, 4, 16, 10, 30, 0, 0, ZoneOffset.of("+05:30"))

    private fun activeClient() = UserEntity(
        id     = clientId,
        email  = "client@example.com",
        name   = "Arun Kumar",
        phone  = "+919876543210",
        role   = UserRole.CLIENT,
        status = UserStatus.ACTIVE,
    )

    private fun pendingClient() = UserEntity(
        id     = clientId,
        email  = "client@example.com",
        name   = "Arun Kumar",
        phone  = "+919876543210",
        role   = UserRole.CLIENT,
        status = UserStatus.PENDING_VERIFICATION,
    )

    private fun buildBatch(status: BatchStatus) = Batch(
        id            = batchId,
        name          = "Batch May 2026",
        description   = null,
        startDateTime = OffsetDateTime.of(2026, 5, 1, 9, 0, 0, 0, ZoneOffset.UTC),
        endDateTime   = OffsetDateTime.of(2026, 5, 3, 17, 0, 0, 0, ZoneOffset.UTC),
        location      = null,
        topics        = null,
        maxSeats      = 20,
        status        = status,
        trainingStatus = null,
        createdBy     = staffId,
        createdAt     = now,
        updatedAt     = now,
    )

    private fun savedBooking() = Booking(
        id        = bookingId,
        batchId   = batchId,
        clientId  = clientId,
        status    = BookingStatus.PENDING,
        createdAt = now,
        updatedAt = now,
    )

    // ── B5: PENDING_VERIFICATION account ─────────────────────────────────────

    @Test
    fun `createBooking - account PENDING_VERIFICATION - throws ACCOUNT_NOT_VERIFIED 403`() {
        every { userRepository.findById(clientId) } returns Optional.of(pendingClient())

        val ex = assertThrows<PlatformException> {
            bookingService.createBooking(batchId, clientId)
        }

        assertEquals("ACCOUNT_NOT_VERIFIED", ex.code)
        assertEquals(HttpStatus.FORBIDDEN,   ex.httpStatus)
        verify(exactly = 0) { batchRepository.findById(any()) }
        verify(exactly = 0) { bookingRepository.existsByClientIdAndBatchId(any(), any()) }
    }

    // ── B6: Batch not found ───────────────────────────────────────────────────

    @Test
    fun `createBooking - batch id does not exist - throws BATCH_NOT_FOUND 404`() {
        every { userRepository.findById(clientId) }  returns Optional.of(activeClient())
        every { batchRepository.findById(batchId) }  returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            bookingService.createBooking(batchId, clientId)
        }

        assertEquals("BATCH_NOT_FOUND",    ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
        verify(exactly = 0) { bookingRepository.existsByClientIdAndBatchId(any(), any()) }
    }

    // ── B7: Batch is DRAFT (invisible to clients) ─────────────────────────────

    @Test
    fun `createBooking - batch is DRAFT - throws BATCH_NOT_FOUND 404`() {
        every { userRepository.findById(clientId) } returns Optional.of(activeClient())
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(BatchStatus.DRAFT))

        val ex = assertThrows<PlatformException> {
            bookingService.createBooking(batchId, clientId)
        }

        assertEquals("BATCH_NOT_FOUND",    ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
        verify(exactly = 0) { bookingRepository.existsByClientIdAndBatchId(any(), any()) }
    }

    // ── B8: Batch is CLOSED ───────────────────────────────────────────────────

    @Test
    fun `createBooking - batch is CLOSED - throws BATCH_NOT_BOOKABLE 422`() {
        every { userRepository.findById(clientId) } returns Optional.of(activeClient())
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(BatchStatus.CLOSED))

        val ex = assertThrows<PlatformException> {
            bookingService.createBooking(batchId, clientId)
        }

        assertEquals("BATCH_NOT_BOOKABLE",              ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,   ex.httpStatus)
        verify(exactly = 0) { bookingRepository.existsByClientIdAndBatchId(any(), any()) }
    }


    // ── B10 / B11: Duplicate booking (any status) ─────────────────────────────

    @Test
    fun `createBooking - booking already exists for same client and batch - throws BOOKING_ALREADY_EXISTS 409`() {
        every { userRepository.findById(clientId) }                          returns Optional.of(activeClient())
        every { batchRepository.findById(batchId) }                          returns Optional.of(buildBatch(BatchStatus.OPEN))
        every { bookingRepository.existsByClientIdAndBatchId(clientId, batchId) } returns true

        val ex = assertThrows<PlatformException> {
            bookingService.createBooking(batchId, clientId)
        }

        assertEquals("BOOKING_ALREADY_EXISTS", ex.code)
        assertEquals(HttpStatus.CONFLICT,       ex.httpStatus)
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    // ── B12: Happy path ───────────────────────────────────────────────────────

    @Test
    fun `createBooking - valid CLIENT books OPEN batch for first time - returns BookingResponse with PENDING status`() {
        every { userRepository.findById(clientId) }                               returns Optional.of(activeClient())
        every { batchRepository.findById(batchId) }                               returns Optional.of(buildBatch(BatchStatus.OPEN))
        every { bookingRepository.existsByClientIdAndBatchId(clientId, batchId) } returns false
        every { bookingRepository.save(any()) }                                   returns savedBooking()

        val result = bookingService.createBooking(batchId, clientId)

        assertEquals(bookingId,            result.id)
        assertEquals(batchId,              result.batchId)
        assertEquals(clientId,             result.clientId)
        assertEquals(BookingStatus.PENDING, result.status)
        assertEquals(now,                  result.createdAt)
        verify(exactly = 1) {
            bookingRepository.save(match {
                it.batchId == batchId && it.clientId == clientId && it.status == BookingStatus.PENDING
            })
        }
    }

    // ── getBookings ───────────────────────────────────────────────────────────

    @Test
    fun `getBookings - no filters - returns all bookings mapped to BookingListItemResponse`() {
        val booking = savedBooking()
        every { bookingRepository.findAll(any<Specification<Booking>>(), any<Pageable>()) } returns
                PageImpl(listOf(booking))
        every { batchRepository.findById(batchId) }   returns Optional.of(buildBatch(BatchStatus.OPEN))
        every { userRepository.findById(clientId) }   returns Optional.of(activeClient())

        val result = bookingService.getBookings(status = null, batchId = null, page = 1, pageSize = 20)

        assertEquals(1,                    result.items.size)
        assertEquals(bookingId,            result.items[0].id)
        assertEquals(batchId,              result.items[0].batchId)
        assertEquals("Batch May 2026",     result.items[0].batchName)
        assertEquals(clientId,             result.items[0].clientId)
        assertEquals("Arun Kumar",         result.items[0].clientName)
        assertEquals("client@example.com", result.items[0].clientEmail)
        assertEquals("+919876543210",      result.items[0].clientPhone)
        assertEquals(BookingStatus.PENDING, result.items[0].status)
        assertEquals(1,                    result.page)
        assertEquals(20,                   result.pageSize)
        assertEquals(1L,                   result.total)
    }

    @Test
    fun `getBookings - status PENDING filter - only PENDING bookings returned`() {
        val booking = savedBooking()
        every { bookingRepository.findAll(any<Specification<Booking>>(), any<Pageable>()) } returns
                PageImpl(listOf(booking))
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(BatchStatus.OPEN))
        every { userRepository.findById(clientId) } returns Optional.of(activeClient())

        val result = bookingService.getBookings(status = BookingStatus.PENDING, batchId = null, page = 1, pageSize = 20)

        assertEquals(1,                     result.items.size)
        assertEquals(BookingStatus.PENDING,  result.items[0].status)
    }

    @Test
    fun `getBookings - combined status and batchId filter - correct subset returned`() {
        val booking = savedBooking()
        every { bookingRepository.findAll(any<Specification<Booking>>(), any<Pageable>()) } returns
                PageImpl(listOf(booking))
        every { batchRepository.findById(batchId) } returns Optional.of(buildBatch(BatchStatus.OPEN))
        every { userRepository.findById(clientId) } returns Optional.of(activeClient())

        val result = bookingService.getBookings(status = BookingStatus.PENDING, batchId = batchId, page = 1, pageSize = 20)

        assertEquals(1,      result.items.size)
        assertEquals(batchId, result.items[0].batchId)
    }

    @Test
    fun `getBookings - no matching bookings - returns empty PagedData with total 0`() {
        every { bookingRepository.findAll(any<Specification<Booking>>(), any<Pageable>()) } returns
                PageImpl(emptyList())

        val result = bookingService.getBookings(status = BookingStatus.PENDING, batchId = batchId, page = 1, pageSize = 20)

        assertEquals(0,  result.items.size)
        assertEquals(0L, result.total)
    }

    @Test
    fun `getBookings - pageSize 100 - throws VALIDATION_ERROR 400`() {
        val ex = assertThrows<PlatformException> {
            bookingService.getBookings(status = null, batchId = null, page = 1, pageSize = 100)
        }

        assertEquals("VALIDATION_ERROR",  ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        verify(exactly = 0) { bookingRepository.findAll(any<Specification<Booking>>(), any<Pageable>()) }
    }

    @Test
    fun `getBookings - page 0 - throws VALIDATION_ERROR 400`() {
        val ex = assertThrows<PlatformException> {
            bookingService.getBookings(status = null, batchId = null, page = 0, pageSize = 20)
        }

        assertEquals("VALIDATION_ERROR",  ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        verify(exactly = 0) { bookingRepository.findAll(any<Specification<Booking>>(), any<Pageable>()) }
    }

    // ── updateBookingStatus ───────────────────────────────────────────────────

    @Test
    fun `updateBookingStatus - booking not found - throws BOOKING_NOT_FOUND 404`() {
        every { bookingRepository.findById(bookingId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            bookingService.updateBookingStatus(bookingId, UpdateBookingStatusRequest(status = BookingStatus.CONFIRMED))
        }

        assertEquals("BOOKING_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `updateBookingStatus - CONFIRMED to CONFIRMED - throws INVALID_STATUS_TRANSITION 422`() {
        val booking = savedBooking().apply { status = BookingStatus.CONFIRMED }
        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)

        val ex = assertThrows<PlatformException> {
            bookingService.updateBookingStatus(bookingId, UpdateBookingStatusRequest(status = BookingStatus.CONFIRMED))
        }

        assertEquals("INVALID_STATUS_TRANSITION",     ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `updateBookingStatus - REJECTED to REJECTED - throws INVALID_STATUS_TRANSITION 422`() {
        val booking = savedBooking().apply { status = BookingStatus.REJECTED }
        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)

        val ex = assertThrows<PlatformException> {
            bookingService.updateBookingStatus(bookingId, UpdateBookingStatusRequest(status = BookingStatus.REJECTED))
        }

        assertEquals("INVALID_STATUS_TRANSITION",     ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `updateBookingStatus - PENDING to CONFIRMED - returns BookingDetailResponse with CONFIRMED status`() {
        val booking  = savedBooking()
        val saved    = savedBooking().apply { status = BookingStatus.CONFIRMED; updatedAt = now.plusMinutes(30) }
        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)
        every { bookingRepository.save(any()) }         returns saved

        val result = bookingService.updateBookingStatus(
            bookingId,
            UpdateBookingStatusRequest(status = BookingStatus.CONFIRMED),
        )

        assertEquals(BookingStatus.CONFIRMED, result.status)
        assertEquals(bookingId,               result.id)
        assertEquals(batchId,                 result.batchId)
        assertEquals(clientId,                result.clientId)
        assertNull(result.note)
        verify(exactly = 1) { bookingRepository.save(match { it.status == BookingStatus.CONFIRMED }) }
    }

    @Test
    fun `updateBookingStatus - PENDING to REJECTED with note - note persisted in response`() {
        val booking = savedBooking()
        val saved   = savedBooking().apply {
            status    = BookingStatus.REJECTED
            note      = "Not eligible at this time"
            updatedAt = now.plusMinutes(5)
        }
        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)
        every { bookingRepository.save(any()) }         returns saved

        val result = bookingService.updateBookingStatus(
            bookingId,
            UpdateBookingStatusRequest(status = BookingStatus.REJECTED, note = "Not eligible at this time"),
        )

        assertEquals(BookingStatus.REJECTED,        result.status)
        assertEquals("Not eligible at this time",   result.note)
    }

    @Test
    fun `updateBookingStatus - CONFIRMED to REJECTED - allowed and returns updated response`() {
        val booking = savedBooking().apply { status = BookingStatus.CONFIRMED }
        val saved   = savedBooking().apply { status = BookingStatus.REJECTED; updatedAt = now.plusMinutes(10) }
        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)
        every { bookingRepository.save(any()) }         returns saved

        val result = bookingService.updateBookingStatus(
            bookingId,
            UpdateBookingStatusRequest(status = BookingStatus.REJECTED),
        )

        assertEquals(BookingStatus.REJECTED, result.status)
    }

    @Test
    fun `updateBookingStatus - REJECTED to CONFIRMED - allowed and returns updated response`() {
        val booking = savedBooking().apply { status = BookingStatus.REJECTED }
        val saved   = savedBooking().apply { status = BookingStatus.CONFIRMED; updatedAt = now.plusMinutes(10) }
        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)
        every { bookingRepository.save(any()) }         returns saved

        val result = bookingService.updateBookingStatus(
            bookingId,
            UpdateBookingStatusRequest(status = BookingStatus.CONFIRMED),
        )

        assertEquals(BookingStatus.CONFIRMED, result.status)
    }

    @Test
    fun `updateBookingStatus - PENDING to CONFIRMED no note - note is null in response`() {
        val booking = savedBooking()
        val saved   = savedBooking().apply { status = BookingStatus.CONFIRMED; updatedAt = now.plusMinutes(1) }
        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)
        every { bookingRepository.save(any()) }         returns saved

        val result = bookingService.updateBookingStatus(
            bookingId,
            UpdateBookingStatusRequest(status = BookingStatus.CONFIRMED, note = null),
        )

        assertEquals(BookingStatus.CONFIRMED, result.status)
        assertNull(result.note)
    }
}

