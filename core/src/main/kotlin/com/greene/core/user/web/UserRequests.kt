package com.greene.core.user.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * Request body for PATCH /api/v1/users/{id}/role.
 *
 * [role] must be one of CLIENT, STAFF, or ADMIN.
 * SUPER_ADMIN is intentionally excluded — assignment of that role is
 * prohibited by business rules and rejected at the service layer as well.
 */
data class ChangeRoleRequest(
    @field:NotBlank(message = "Role is required")
    @field:Pattern(
        regexp = "^(CLIENT|STAFF|ADMIN)$",
        message = "Invalid role value. Must be one of: CLIENT, STAFF, ADMIN",
    )
    val role: String? = null,
)

