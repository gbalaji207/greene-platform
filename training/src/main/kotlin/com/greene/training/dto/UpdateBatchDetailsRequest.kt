package com.greene.training.dto

import jakarta.validation.constraints.Min
import java.time.OffsetDateTime

/**
 * Request body for PATCH /api/v1/batches/{id}/details.
 *
 * All fields are optional — patch semantics apply.
 * At least one field must be non-null; enforced in the service layer (AT_LEAST_ONE_FIELD_REQUIRED).
 *
 * Cross-field date validation (endDateTime > startDateTime) is enforced in the service layer,
 * since it must be checked against the persisted startDateTime when only endDateTime is provided.
 */
data class UpdateBatchDetailsRequest(

    val name: String? = null,

    val description: String? = null,

    val location: String? = null,

    val topics: String? = null,

    /** Must be >= 1 when provided. */
    @field:Min(1)
    val maxSeats: Int? = null,

    val startDateTime: OffsetDateTime? = null,

    val endDateTime: OffsetDateTime? = null,
)

