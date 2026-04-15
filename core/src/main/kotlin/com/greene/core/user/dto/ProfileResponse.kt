package com.greene.core.user.dto

import java.time.Instant
import java.util.UUID

/**
 * Response body for:
 *  - GET  /api/v1/users/me
 *  - PATCH /api/v1/users/me/profile
 *
 * Returned inside the standard [com.greene.core.api.response.ApiResponse] wrapper.
 *
 * [profilePhotoUrl] is a pre-signed URL valid for the configured expiry window,
 * or null if the user has not uploaded a photo yet.
 */
data class ProfileResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val status: String,
    val profilePhotoUrl: String?,
    val createdAt: Instant,
)

