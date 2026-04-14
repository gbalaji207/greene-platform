package com.greene.core.auth.dto

/** Returned by POST /api/v1/auth/register */
data class RegisterResponse(
    val flow: String = "OTP_SENT",
)

