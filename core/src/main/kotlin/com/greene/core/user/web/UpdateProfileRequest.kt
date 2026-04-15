package com.greene.core.user.web

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request body for PATCH /api/v1/users/me/profile.
 *
 * Both fields are optional — the service layer enforces that at least one
 * must be non-null (AT_LEAST_ONE_FIELD_REQUIRED).
 *
 * Email is intentionally absent: it is not patchable via this endpoint.
 * Any email field supplied by the client is silently ignored at the JSON
 * deserialization level because it has no corresponding property here.
 */
data class UpdateProfileRequest(

    @field:Size(min = 2, max = 100)
    val name: String? = null,

    @field:Pattern(regexp = """^\+[1-9]\d{7,14}$""")
    val phone: String? = null,
)

