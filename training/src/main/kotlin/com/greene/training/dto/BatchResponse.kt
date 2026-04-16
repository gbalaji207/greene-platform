package com.greene.training.dto

import com.greene.training.domain.BatchStatus
import com.greene.training.domain.TrainingStatus
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Response envelope for a single batch resource.
 * Mirrors the `data` block in the API spec success responses for
 * POST /api/v1/batches (201) and GET /api/v1/batches/{id} (200).
 */
data class BatchResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val startDateTime: OffsetDateTime,
    val endDateTime: OffsetDateTime?,
    val location: String?,
    val topics: String?,
    val maxSeats: Int?,
    val status: BatchStatus,
    val trainingStatus: TrainingStatus?,
    val createdBy: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

