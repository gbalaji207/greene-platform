package com.greene.core.auth.service

import com.greene.core.auth.config.OtpProperties
import com.greene.core.exception.PlatformException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Redis-backed rate limiter for new OTP generation events.
 *
 * Pattern: INCR key → on first hit set EXPIRE → reject if count exceeds ceiling.
 *
 * Key format: `otp:ratelimit:{email_lowercase}`
 *
 * The window is a fixed expiry from the **first** request in a burst, not a true
 * sliding window. This is standard for simple INCR/EXPIRE rate limiting and is
 * sufficient for OTP abuse prevention.
 *
 * Resend attempts are NOT subject to this limiter — they are governed by
 * [OtpService.prepareResend] using the resend_count / last_resent_at columns.
 */
@Service
class RateLimitService(
    private val redisTemplate: StringRedisTemplate,
    private val otpProperties: OtpProperties,
) {

    companion object {
        private const val KEY_PREFIX = "otp:ratelimit:"
    }

    /**
     * Increments the OTP-generation counter for [email] within the configured window.
     * Throws [PlatformException] with code `RATE_LIMIT_EXCEEDED` (HTTP 429) if the
     * counter exceeds [OtpProperties.rateLimitMax].
     *
     * Must be called **before** persisting a new OTP.
     */
    fun checkAndIncrement(email: String) {
        val key   = "$KEY_PREFIX${email.lowercase()}"
        val count = redisTemplate.opsForValue().increment(key)
            ?: throw PlatformException(
                "INTERNAL_ERROR",
                "Rate limit check failed.",
                HttpStatus.INTERNAL_SERVER_ERROR,
            )

        // Set the expiry only on the first increment so the window does not reset
        // on each subsequent request within the same burst.
        if (count == 1L) {
            redisTemplate.expire(
                key,
                otpProperties.rateLimitWindowMinutes.toLong(),
                TimeUnit.MINUTES,
            )
        }

        if (count > otpProperties.rateLimitMax) {
            throw PlatformException(
                "RATE_LIMIT_EXCEEDED",
                "Too many OTP requests. Please wait and try again later.",
                HttpStatus.TOO_MANY_REQUESTS,
            )
        }
    }
}

