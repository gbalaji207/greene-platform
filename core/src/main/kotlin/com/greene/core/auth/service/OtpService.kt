package com.greene.core.auth.service

import com.greene.core.auth.config.OtpProperties
import com.greene.core.auth.domain.OtpPurpose
import com.greene.core.auth.domain.OtpTokenEntity
import com.greene.core.auth.repository.OtpTokenRepository
import com.greene.core.exception.PlatformException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Handles the full lifecycle of OTP tokens: generation, verification, and resend.
 *
 * ── Resend strategy ────────────────────────────────────────────────────────
 * OTPs are stored as BCrypt hashes (one-way). The plaintext OTP is kept in
 * Redis under key "otp:plaintext:{userId}" with a TTL matching the OTP expiry.
 * [prepareResend] retrieves that value and re-delivers the SAME code — the
 * `otp_hash` column in `otp_tokens` is NEVER modified on a resend.
 * ──────────────────────────────────────────────────────────────────────────
 */
@Service
@Transactional
class OtpService(
    private val otpTokenRepository: OtpTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val otpProperties: OtpProperties,
    private val redisTemplate: StringRedisTemplate,
) {

    companion object {
        private const val PLAINTEXT_KEY_PREFIX = "otp:plaintext:"
    }

    private val secureRandom = SecureRandom()

    // ── Generation ────────────────────────────────────────────────────────────

    /**
     * Generates a cryptographically random 6-digit OTP, BCrypt-hashes it,
     * invalidates all existing active OTPs for the same (userId, purpose) pair,
     * persists a new [OtpTokenEntity], stores the plaintext OTP in Redis with a
     * TTL matching the OTP expiry, and returns the **plaintext** OTP for delivery.
     *
     * The Redis key "otp:plaintext:{userId}" is the only place the plaintext is
     * retained after this method returns; it is required by [prepareResend].
     */
    fun generateAndPersist(userId: UUID, purpose: OtpPurpose): String {
        val otp = randomSixDigit()
        val now = Instant.now()

        // Invalidate any previously active OTP for this user+purpose.
        otpTokenRepository.invalidateAllActiveByUserIdAndPurpose(userId, purpose, now)

        otpTokenRepository.save(
            OtpTokenEntity(
                userId    = userId,
                otpHash   = passwordEncoder.encode(otp),
                purpose   = purpose,
                expiresAt = now.plus(otpProperties.expiryMinutes.toLong(), ChronoUnit.MINUTES),
            )
        )

        // Store plaintext in Redis so resend can retrieve the original code.
        redisTemplate.opsForValue().set(
            "$PLAINTEXT_KEY_PREFIX$userId",
            otp,
            otpProperties.expiryMinutes.toLong(),
            TimeUnit.MINUTES,
        )

        return otp
    }

    // ── Verification ──────────────────────────────────────────────────────────

    /**
     * Verifies the submitted OTP against the stored BCrypt hash and marks it used.
     *
     * Throws:
     *  - [PlatformException] OTP_NOT_FOUND (400) — no active (unused, non-invalidated) OTP exists
     *  - [PlatformException] OTP_ALREADY_USED (400) — the most recent OTP was already consumed
     *  - [PlatformException] OTP_EXPIRED (400) — OTP exists but `expires_at` has passed
     *  - [PlatformException] INVALID_OTP (400) — BCrypt mismatch
     */
    fun verifyAndConsume(userId: UUID, purpose: OtpPurpose, submittedOtp: String) {
        val active = otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose)

        if (active == null) {
            // Distinguish "already used" from "never generated / invalidated".
            val latest = otpTokenRepository
                .findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
            if (latest?.usedAt != null) {
                throw PlatformException(
                    "OTP_ALREADY_USED",
                    "This OTP has already been used",
                    HttpStatus.BAD_REQUEST,
                )
            }
            throw PlatformException(
                "OTP_NOT_FOUND",
                "No active OTP found. Please request a new one",
                HttpStatus.BAD_REQUEST,
            )
        }

        if (Instant.now().isAfter(active.expiresAt)) {
            throw PlatformException(
                "OTP_EXPIRED",
                "Your OTP has expired. Please restart the flow",
                HttpStatus.BAD_REQUEST,
            )
        }

        if (!passwordEncoder.matches(submittedOtp, active.otpHash)) {
            throw PlatformException(
                "INVALID_OTP",
                "The OTP entered is incorrect",
                HttpStatus.BAD_REQUEST,
            )
        }

        active.usedAt = Instant.now()
        otpTokenRepository.save(active)
    }

    // ── Resend ────────────────────────────────────────────────────────────────

    /**
     * Enforces resend rules and retrieves the original plaintext OTP from Redis
     * for re-delivery.
     *
     * The `otp_hash` in `otp_tokens` is NEVER modified — the exact same code
     * that was generated by [generateAndPersist] is re-sent. Only
     * [OtpTokenEntity.resendCount] and [OtpTokenEntity.lastResentAt] are updated.
     *
     * Throws:
     *  - [PlatformException] OTP_NOT_FOUND (400) — no active (unused, non-invalidated) OTP row exists
     *  - [PlatformException] OTP_EXPIRED (400) — OTP record has passed `expires_at`
     *                                             or the Redis plaintext key has expired
     *  - [PlatformException] MAX_RESEND_ATTEMPTS (400) — resend_count ≥ maxResendAttempts
     *  - [PlatformException] RESEND_TOO_SOON (400) — minimum wait window not elapsed
     */
    fun prepareResend(userId: UUID, purpose: OtpPurpose): ResendResult {
        val token = otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose)
            ?: throw PlatformException(
                "OTP_NOT_FOUND",
                "No active OTP found. Please restart the flow",
                HttpStatus.BAD_REQUEST,
            )

        val now = Instant.now()

        if (now.isAfter(token.expiresAt)) {
            throw PlatformException(
                "OTP_EXPIRED",
                "Your OTP has expired. Please restart the flow",
                HttpStatus.BAD_REQUEST,
            )
        }

        if (token.resendCount >= otpProperties.maxResendAttempts) {
            throw PlatformException(
                "MAX_RESEND_ATTEMPTS",
                "Maximum resend attempts reached. Please try again later",
                HttpStatus.BAD_REQUEST,
            )
        }

        // Wait window starts from the last resend (or OTP creation for the first resend).
        val lastAction    = token.lastResentAt ?: token.createdAt
        val nextAllowedAt = lastAction.plus(otpProperties.resendWaitMinutes.toLong(), ChronoUnit.MINUTES)

        if (now.isBefore(nextAllowedAt)) {
            throw PlatformException(
                "RESEND_TOO_SOON",
                "Please wait before requesting another resend",
                HttpStatus.BAD_REQUEST,
            )
        }

        // Retrieve the original plaintext OTP from Redis — never generate a new one.
        val plaintextOtp = redisTemplate.opsForValue().get("$PLAINTEXT_KEY_PREFIX$userId")
            ?: throw PlatformException(
                "OTP_EXPIRED",
                "Your OTP has expired. Please restart the flow",
                HttpStatus.BAD_REQUEST,
            )

        // Update resend metadata only — otp_hash is intentionally NOT changed.
        token.resendCount  = (token.resendCount + 1).toShort()
        token.lastResentAt = now
        otpTokenRepository.save(token)

        val nextResendAllowedAt = now.plus(otpProperties.resendWaitMinutes.toLong(), ChronoUnit.MINUTES)
        return ResendResult(otp = plaintextOtp, nextResendAllowedAt = nextResendAllowedAt)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns a zero-padded 6-digit string from a cryptographically secure source. */
    private fun randomSixDigit(): String =
        secureRandom.nextInt(1_000_000).toString().padStart(6, '0')
}

/**
 * Returned by [OtpService.prepareResend].
 *
 * @param otp                 Plaintext 6-digit code to send via email (single-use).
 * @param nextResendAllowedAt When the next resend attempt will be permitted.
 */
data class ResendResult(
    val otp: String,
    val nextResendAllowedAt: Instant,
)
