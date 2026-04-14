package com.greene.core.auth.dto

/**
 * Returned by POST /api/v1/auth/verify-otp on success.
 * Combines the JWT pair with a lightweight user snapshot so the client can
 * bootstrap its session without an extra /me request.
 */
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    /** Access token lifetime in seconds (mirrors the `exp` claim delta). */
    val expiresIn: Int,
    val user: AuthUserDto,
)

