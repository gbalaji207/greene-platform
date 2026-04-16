package com.greene.training.dto

import com.greene.training.domain.BatchStatus
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

/**
 * Request body for POST /api/v1/batches.
 *
 * All fields are nullable with null defaults (platform convention — allows partial JSON bodies
 * to deserialize cleanly and report bean-validation errors field by field).
 *
 * Cross-field validation (endDate >= startDate) is enforced in the service layer
 * for clarity, since it requires comparing two fields.
 */
data class CreateBatchRequest(

    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val name: String? = null,

    @field:Size(max = 2000)
    val description: String? = null,

    @field:NotNull
    @field:FutureOrPresent(message = "must be today or a future date")
    val startDateTime: OffsetDateTime? = null,

    // Cross-field: endDateTime > startDateTime validated in BatchService
    val endDateTime: OffsetDateTime? = null,

    @field:Size(max = 500)
    val location: String? = null,

    @field:Size(max = 500)
    val topics: String? = null,

    /** Must be >= 1 when provided. Informational only — never seat-enforced at booking time. */
    @field:Min(1)
    val maxSeats: Int? = null,

    /** DRAFT or OPEN only on creation. Defaults to DRAFT when omitted. */
    val status: BatchStatus? = null,
)

