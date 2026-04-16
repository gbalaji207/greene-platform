package com.greene.training.dto

import com.greene.training.domain.BookingStatus
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Response DTO for each item in GET /api/v1/bookings list.
 * Includes denormalised batch and client fields.
 */
data class BookingListItemResponse(
    val id: UUID,
    val batchId: UUID,
    val batchName: String,
    val clientId: UUID,
    val clientName: String,
    val clientEmail: String,
    val clientPhone: String,
    val status: BookingStatus,
    val note: String?,
    val createdAt: OffsetDateTime,
)

