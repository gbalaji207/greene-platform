package com.greene.core.auth.dto

import java.time.Instant

/** Returned by POST /api/v1/auth/resend-otp on success. */
data class ResendOtpResponse(
    val flow: String = "OTP_RESENT",
    /** ISO-8601 instant — when the next resend attempt will be permitted. */
    val nextResendAllowedAt: Instant,
)

