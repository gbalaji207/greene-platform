package com.greene.training.dto

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Trimmed projection of a batch, returned to unauthenticated callers and CLIENT-role users
 * from GET /api/v1/batches.
 * Does not expose internal fields such as status, trainingStatus, createdBy, or audit timestamps.
 */
data class BatchListItemResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val startDateTime: OffsetDateTime,
    val endDateTime: OffsetDateTime?,
    val location: String?,
    val topics: String?,
    val maxSeats: Int?,
)

