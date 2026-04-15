package com.greene.core.auth.dto

/**
 * Returned by POST /api/v1/auth/logout on success.
 * Logout is idempotent — this response is returned for every 200 case.
 */
data class LogoutResponse(
    val message: String,
)

