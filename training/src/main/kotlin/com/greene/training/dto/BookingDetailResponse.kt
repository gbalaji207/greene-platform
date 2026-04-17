package com.greene.training.dto

import com.greene.training.domain.BookingStatus
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Response DTO for PATCH /api/v1/bookings/{id}.
 * Mirrors the `data` block in the API spec success response (200).
 */
data class BookingDetailResponse(
    val id: UUID,
    val batchId: UUID,
    val clientId: UUID,
    val status: BookingStatus,
    val note: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

