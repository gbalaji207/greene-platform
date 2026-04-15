package com.greene.core.user.dto

import java.time.Instant
import java.util.UUID

/**
 * Response body for PATCH /api/v1/users/{id}/role.
 * Returned inside the standard [com.greene.core.api.response.ApiResponse] wrapper.
 */
data class ChangeRoleResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val role: String,
    val updatedAt: Instant,
)

