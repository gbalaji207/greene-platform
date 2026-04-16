package com.greene.training.service

import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import com.greene.training.domain.BatchStatus
import com.greene.training.domain.BookingStatus
import com.greene.training.dto.BookingResponse
import com.greene.training.entity.Booking
import com.greene.training.repository.BatchRepository
import com.greene.training.repository.BookingRepository
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
}

// ── Mapping ───────────────────────────────────────────────────────────────────

private fun Booking.toResponse() = BookingResponse(
    id = id!!,
    batchId = batchId,
    clientId = clientId,
    status = status,
    createdAt = createdAt,
)

