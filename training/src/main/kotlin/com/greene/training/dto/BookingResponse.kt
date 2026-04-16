package com.greene.training.dto

import com.greene.training.domain.BookingStatus
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Response DTO for a booking resource.
 * Mirrors the `data` block in the API spec success response for
 * POST /api/v1/batches/{id}/bookings (201).
 */
data class BookingResponse(
    val id: UUID,
    val batchId: UUID,
    val clientId: UUID,
    val status: BookingStatus,
    val createdAt: OffsetDateTime,
)

