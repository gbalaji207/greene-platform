package com.greene.training.dto

import com.greene.training.domain.BookingStatus
import jakarta.validation.constraints.NotNull

/**
 * Request DTO for PATCH /api/v1/bookings/{id}.
 *
 * [status] must be CONFIRMED or REJECTED — PENDING is not a valid target status.
 * The PENDING check is enforced in the service layer (BookingStatusTransitionValidator).
 */
data class UpdateBookingStatusRequest(

    @field:NotNull(message = "status is required")
    val status: BookingStatus? = null,

    val note: String? = null,
)


