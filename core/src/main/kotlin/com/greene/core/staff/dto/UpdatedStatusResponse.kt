package com.greene.core.staff.dto

import java.time.Instant
import java.util.UUID

/** Returned by PATCH /api/v1/staff/{id}/status on successful status change. */
data class UpdatedStatusResponse(
    val id: UUID,
    /** String form of the new [com.greene.core.auth.domain.UserStatus] — "ACTIVE" or "SUSPENDED". */
    val status: String,
    val updatedAt: Instant,
)

