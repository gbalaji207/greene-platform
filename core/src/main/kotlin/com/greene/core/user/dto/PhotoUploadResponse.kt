package com.greene.core.user.dto

/**
 * Response body for POST /api/v1/users/me/profile/photo.
 *
 * Returned inside the standard [com.greene.core.api.response.ApiResponse] wrapper.
 *
 * [profilePhotoUrl] is the pre-signed GET URL for the newly uploaded photo,
 * valid for the configured expiry window.
 */
data class PhotoUploadResponse(
    val profilePhotoUrl: String,
)

