package com.greene.core.auth.dto

/** Returned by POST /api/v1/auth/identify */
data class IdentifyResponse(
    /** Either "LOGIN" (ACTIVE user found, OTP sent) or "REGISTER" (unknown / pending email). */
    val flow: String,
)

