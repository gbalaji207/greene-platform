package com.greene.training.dto

import com.greene.training.domain.BatchStatus
import jakarta.validation.constraints.NotNull

/**
 * Request body for PATCH /api/v1/batches/{id}/status.
 *
 * [status] must be OPEN or CLOSED — DRAFT is not a valid transition target.
 * The DRAFT guard is enforced in the service layer (VALIDATION_ERROR 400).
 * Transition legality (e.g. DRAFT → CLOSED forbidden) is also enforced in the service layer.
 */
data class UpdateBatchStatusRequest(

    @field:NotNull(message = "status is required")
    val status: BatchStatus? = null,
)

