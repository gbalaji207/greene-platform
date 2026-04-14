package com.greene.core.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Binds all `otp.*` keys from application.yml.
 *
 * Registered via @EnableConfigurationProperties in AuthConfig.
 *
 * Defaults match the spec:
 *   otp:
 *     expiry-minutes: 10
 *     resend-wait-minutes: 1
 *     max-resend-attempts: 3
 *     rate-limit-max: 3
 *     rate-limit-window-minutes: 10
 */
@ConfigurationProperties(prefix = "otp")
data class OtpProperties(
    /** How long a generated OTP remains valid before it expires. */
    val expiryMinutes: Int = 10,

    /** Minimum time a user must wait between resend attempts. */
    val resendWaitMinutes: Int = 1,

    /** Maximum number of resend attempts allowed per active OTP. */
    val maxResendAttempts: Int = 3,

    /** Maximum new-OTP generations allowed within [rateLimitWindowMinutes]. */
    val rateLimitMax: Int = 3,

    /** Sliding window length for the new-OTP rate limit. */
    val rateLimitWindowMinutes: Int = 10,
)


