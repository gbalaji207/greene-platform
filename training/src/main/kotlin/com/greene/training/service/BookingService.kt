package com.greene.training.service

import com.greene.core.api.response.PagedData
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import com.greene.core.auth.domain.UserStatus
import com.greene.training.domain.BatchStatus
import com.greene.training.domain.BookingStatus
import com.greene.training.dto.BookingDetailResponse
import com.greene.training.dto.BookingListItemResponse
import com.greene.training.dto.BookingResponse
import com.greene.training.dto.UpdateBookingStatusRequest
import com.greene.training.entity.Booking
import com.greene.training.repository.BatchRepository
import com.greene.training.repository.BookingRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Domain service for booking management.
 *
 * Business rules enforced here (in order):
 *  1. Caller's account must be ACTIVE — PENDING_VERIFICATION → 403 ACCOUNT_NOT_VERIFIED.
 *  2. Batch must exist and must not be DRAFT (invisible to clients) → 404 BATCH_NOT_FOUND.
 *  3. Batch must not be CLOSED or COMPLETED → 422 BATCH_NOT_BOOKABLE.
 *  4. Client must not already have a booking for this batch (any status) → 409 BOOKING_ALREADY_EXISTS.
 *  5. Persist booking with status PENDING.
 */
@Service
@Transactional
class BookingService(
    private val userRepository: UserRepository,
    private val batchRepository: BatchRepository,
    private val bookingRepository: BookingRepository,
) {

    /**
     * Creates a booking for [batchId] on behalf of [clientId].
     *
     * [clientId] is always sourced from the JWT principal — never from the request body.
     */
    fun createBooking(batchId: UUID, clientId: UUID): BookingResponse {

        // Rule 1 — caller must be ACTIVE
        val user = userRepository.findById(clientId).orElseThrow {
            PlatformException(
                code = "UNAUTHORIZED",
                message = "Authenticated user not found",
                httpStatus = HttpStatus.UNAUTHORIZED,
            )
        }
        if (user.status == UserStatus.PENDING_VERIFICATION) {
            throw PlatformException(
                code = "ACCOUNT_NOT_VERIFIED",
                message = "Account email has not been verified",
                httpStatus = HttpStatus.FORBIDDEN,
            )
        }

        // Rule 2 — batch must exist and not be DRAFT (treated as not found for clients)
        val batch = batchRepository.findById(batchId).orElseThrow {
            PlatformException(
                code = "BATCH_NOT_FOUND",
                message = "Batch not found",
                httpStatus = HttpStatus.NOT_FOUND,
            )
        }
        if (batch.status == BatchStatus.DRAFT) {
            throw PlatformException(
                code = "BATCH_NOT_FOUND",
                message = "Batch not found",
                httpStatus = HttpStatus.NOT_FOUND,
            )
        }

        // Rule 3 — batch must be OPEN (CLOSED / COMPLETED no longer accept bookings)
        if (batch.status == BatchStatus.CLOSED || batch.status == BatchStatus.COMPLETED) {
            throw PlatformException(
                code = "BATCH_NOT_BOOKABLE",
                message = "Batch is no longer accepting bookings",
                httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }

        // Rule 4 — duplicate booking check (any status counts)
        if (bookingRepository.existsByClientIdAndBatchId(clientId, batchId)) {
            throw PlatformException(
                code = "BOOKING_ALREADY_EXISTS",
                message = "A booking for this batch already exists",
                httpStatus = HttpStatus.CONFLICT,
            )
        }

        // Rule 5 — persist
        val booking = bookingRepository.save(
            Booking(
                batchId = batchId,
                clientId = clientId,
                status = BookingStatus.PENDING,
            )
        )

        return booking.toResponse()
    }

    /**
     * Returns a paginated list of bookings for staff/admin, with optional filters.
     *
     * Validation:
     *  - [page] must be >= 1
     *  - [pageSize] must be <= 50
     */
    @Transactional(readOnly = true)
    fun getBookings(
        status: BookingStatus?,
        batchId: UUID?,
        page: Int,
        pageSize: Int,
    ): PagedData<BookingListItemResponse> {

        if (page < 1) throw PlatformException(
            code = "VALIDATION_ERROR",
            message = "page must be >= 1",
            httpStatus = HttpStatus.BAD_REQUEST,
        )
        if (pageSize > 50) throw PlatformException(
            code = "VALIDATION_ERROR",
            message = "pageSize must be <= 50",
            httpStatus = HttpStatus.BAD_REQUEST,
        )

        val pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))

        val spec = Specification.allOf(
            buildList {
                if (status != null) add(Specification<Booking> { root, _, cb ->
                    cb.equal(root.get<BookingStatus>("status"), status)
                })
                if (batchId != null) add(Specification<Booking> { root, _, cb ->
                    cb.equal(root.get<java.util.UUID>("batchId"), batchId)
                })
            }
        )
        val bookingPage = bookingRepository.findAll(spec, pageable)

        val items = bookingPage.content.map { booking ->
            val batch = batchRepository.findById(booking.batchId).orElseThrow {
                PlatformException(
                    code = "BATCH_NOT_FOUND",
                    message = "Batch ${booking.batchId} not found",
                    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                )
            }
            val client = userRepository.findById(booking.clientId).orElseThrow {
                PlatformException(
                    code = "USER_NOT_FOUND",
                    message = "User ${booking.clientId} not found",
                    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                )
            }
            booking.toListItemResponse(batchName = batch.name, client = client)
        }

        return PagedData(
            items = items,
            page = page,
            pageSize = pageSize,
            total = bookingPage.totalElements,
        )
    }

    /**
     * Confirms or rejects a booking on behalf of a staff/admin caller.
     *
     * Business rules (in order):
     *  1. Booking must exist → 404 BOOKING_NOT_FOUND
     *  2. Status transition must be valid → 422 INVALID_STATUS_TRANSITION (same → same)
     *  3. Persist updated status + note (note may be null — clears any existing value)
     */
    fun updateBookingStatus(bookingId: UUID, request: UpdateBookingStatusRequest): BookingDetailResponse {

        // Rule 1 — booking must exist
        val booking = bookingRepository.findById(bookingId).orElseThrow {
            PlatformException(
                code = "BOOKING_NOT_FOUND",
                message = "Booking not found",
                httpStatus = HttpStatus.NOT_FOUND,
            )
        }

        // Rule 2 — validate status transition (throws 422 if current == target)
        BookingStatusTransitionValidator.validate(booking.status, request.status!!)

        // Rule 3 — mutate and save
        booking.status = request.status
        booking.note = request.note

        val saved = bookingRepository.save(booking)
        return saved.toDetailResponse()
    }
}

// ── Mapping ───────────────────────────────────────────────────────────────────

private fun Booking.toResponse() = BookingResponse(
    id = id!!,
    batchId = batchId,
    clientId = clientId,
    status = status,
    createdAt = createdAt,
)

private fun Booking.toListItemResponse(
    batchName: String,
    client: com.greene.core.auth.domain.UserEntity,
) = BookingListItemResponse(
    id = id!!,
    batchId = batchId,
    batchName = batchName,
    clientId = clientId,
    clientName = client.name,
    clientEmail = client.email,
    clientPhone = client.phone,
    status = status,
    note = note,
    createdAt = createdAt,
)

private fun Booking.toDetailResponse() = BookingDetailResponse(
    id = id!!,
    batchId = batchId,
    clientId = clientId,
    status = status,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

