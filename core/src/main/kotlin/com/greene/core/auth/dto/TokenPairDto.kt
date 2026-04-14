package com.greene.core.auth.dto

/**
 * Returned by JwtService.generateTokenPair() and surfaced in the verify-otp response.
 *
 * @param accessToken  RS256-signed JWT; includes sub, email, role, iat, exp.
 * @param refreshToken Raw opaque token (UUID); stored as SHA-256 hash in refresh_tokens.
 * @param expiresIn    Access token lifetime in seconds (mirrors the `exp` claim delta).
 */
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
)

