package com.greene.core.auth.service

import com.greene.core.auth.domain.OtpPurpose
import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.dto.AuthTokenResponse
import com.greene.core.auth.dto.AuthUserDto
import com.greene.core.auth.dto.IdentifyResponse
import com.greene.core.auth.dto.LogoutResponse
import com.greene.core.auth.dto.RegisterResponse
import com.greene.core.auth.dto.ResendOtpResponse
import com.greene.core.auth.dto.TokenPairDto
import com.greene.core.auth.repository.RefreshTokenRepository
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Orchestrates the four auth flows defined in E2-US1.
 *
 * All public methods are transactional: if an outbound call (SES, Redis) fails,
 * any partial DB changes are rolled back and the client receives a 5xx.
 * The OTP / user record is then still in its pre-call state, allowing the user
 * to retry without manual cleanup.
 *
 * Email normalisation: all email lookups use lowercase. Callers may pass
 * mixed-case emails and they will be normalised transparently.
 */
@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val otpService: OtpService,
    private val rateLimitService: RateLimitService,
    private val emailService: EmailService,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    // ── identify ──────────────────────────────────────────────────────────────

    /**
     * Looks up the email and returns which flow the client should present.
     *
     *  - SUSPENDED user → 403 ACCOUNT_SUSPENDED (E2-US2)
     *  - ACTIVE user    → generate LOGIN OTP, send via SES, apply rate limit → flow = LOGIN
     *  - Anything else  → no OTP sent → flow = REGISTER
     *
     * Privacy: the REGISTER branch never confirms whether the email exists.
     * SUSPENDED surfaces an explicit 403 so the admin-contact message can be shown.
     */
    fun identify(email: String): IdentifyResponse {
        val normalised = email.lowercase()
        val user = userRepository.findByEmail(normalised)

        return when (user?.status) {
            UserStatus.SUSPENDED -> throw PlatformException(
                "ACCOUNT_SUSPENDED",
                "Your account has been suspended. Please contact your administrator.",
                HttpStatus.FORBIDDEN,
            )
            UserStatus.ACTIVE -> {
                // Rate-limit check BEFORE persisting the OTP.
                rateLimitService.checkAndIncrement(normalised)
                val otp = otpService.generateAndPersist(user.id!!, OtpPurpose.LOGIN)
                emailService.sendOtp(normalised, otp)
                IdentifyResponse(flow = "LOGIN")
            }
            else -> IdentifyResponse(flow = "REGISTER")
        }
    }

    // ── register ──────────────────────────────────────────────────────────────

    /**
     * Creates or updates an account and sends an EMAIL_VERIFICATION OTP.
     *
     *  - ACTIVE email           → 409 EMAIL_ALREADY_ACTIVE
     *  - PENDING_VERIFICATION   → update name + phone, generate fresh OTP
     *  - Not found              → create new account (PENDING_VERIFICATION)
     */
    fun register(email: String, name: String, phone: String): RegisterResponse {
        val normalised = email.lowercase()
        val existing   = userRepository.findByEmail(normalised)

        // Step 1 — email already active guard (before any write).
        if (existing?.status == UserStatus.ACTIVE) {
            throw PlatformException(
                "EMAIL_ALREADY_ACTIVE",
                "An account with this email is already registered",
                HttpStatus.CONFLICT,
            )
        }

        // Step 2 — phone belongs to a different account guard (before any write).
        val existingByPhone = userRepository.findByPhone(phone)
        if (existingByPhone != null && existingByPhone.email != normalised) {
            throw PlatformException(
                "PHONE_ALREADY_REGISTERED",
                "An account with this phone number already exists",
                HttpStatus.CONFLICT,
            )
        }

        // Rate-limit check BEFORE any writes.
        rateLimitService.checkAndIncrement(normalised)

        // Step 3 — PENDING_VERIFICATION re-registration path.
        // Step 4 — new account creation path.
        val user: UserEntity = if (existing?.status == UserStatus.PENDING_VERIFICATION) {
            // Re-registration: update mutable fields, keep the same record.
            existing.name  = name
            existing.phone = phone
            userRepository.save(existing)
        } else {
            userRepository.save(
                UserEntity(
                    email         = normalised,
                    name          = name,
                    phone         = phone,
                    status        = UserStatus.PENDING_VERIFICATION,
                    phoneVerified = false,
                )
            )
        }

        val otp = otpService.generateAndPersist(user.id!!, OtpPurpose.EMAIL_VERIFICATION)
        emailService.sendOtp(normalised, otp)

        return RegisterResponse()
    }

    // ── verifyOtp ─────────────────────────────────────────────────────────────

    /**
     * Validates the OTP and returns auth tokens on success.
     *
     * Flow detection is server-side, derived from the user's current status:
     *  - PENDING_VERIFICATION → EMAIL_VERIFICATION purpose → activate account + welcome email
     *  - ACTIVE               → LOGIN purpose → tokens only, no email
     *
     * All OTP error codes (INVALID_OTP, OTP_EXPIRED, OTP_ALREADY_USED) are thrown
     * by [OtpService.verifyAndConsume] and propagate unchanged.
     */
    fun verifyOtp(email: String, otp: String): AuthTokenResponse {
        val normalised = email.lowercase()
        val user = userRepository.findByEmail(normalised)
            ?: throw PlatformException(
                "ACCOUNT_NOT_FOUND",
                "No account found for this email",
                HttpStatus.NOT_FOUND,
            )

        val userId  = requireNotNull(user.id)
        val purpose = resolvePurpose(user)

        // Throws INVALID_OTP / OTP_EXPIRED / OTP_ALREADY_USED on failure.
        // All DB state is unchanged until this returns successfully.
        otpService.verifyAndConsume(userId, purpose, otp)

        // ── Post-verification side effects ────────────────────────────────────
        if (purpose == OtpPurpose.EMAIL_VERIFICATION) {
            user.status = UserStatus.ACTIVE
            emailService.sendWelcome(normalised, user.name)
        }

        user.lastLoginAt = Instant.now()
        val savedUser = userRepository.save(user)

        val tokens = jwtService.generateTokenPair(savedUser)

        return AuthTokenResponse(
            accessToken  = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresIn    = tokens.expiresIn,
            user = AuthUserDto(
                id    = savedUser.id!!,
                name  = savedUser.name,
                email = savedUser.email,
                role  = savedUser.role.name,
            ),
        )
    }

    // ── resendOtp ─────────────────────────────────────────────────────────────

    /**
     * Resends the active OTP for the given email.
     *
     * All throttle errors (OTP_EXPIRED, RESEND_TOO_SOON, MAX_RESEND_ATTEMPTS) are
     * thrown by [OtpService.prepareResend] and propagate unchanged.
     */
    fun resendOtp(email: String): ResendOtpResponse {
        val normalised = email.lowercase()
        val user = userRepository.findByEmail(normalised)
            ?: throw PlatformException(
                "ACCOUNT_NOT_FOUND",
                "No account found for this email",
                HttpStatus.NOT_FOUND,
            )

        val purpose      = resolvePurpose(user)
        val resendResult = otpService.prepareResend(user.id!!, purpose)

        emailService.sendOtp(normalised, resendResult.otp)

        return ResendOtpResponse(nextResendAllowedAt = resendResult.nextResendAllowedAt)
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    /**
     * Validates the supplied refresh token, rotates it, and returns a new token pair.
     * Old token is revoked atomically — single-use enforced.
     *
     * Error order (per E2-US3 spec):
     *  1. Token not found → REFRESH_TOKEN_INVALID 401
     *  2. revoked_at set  → REFRESH_TOKEN_INVALID 401
     *  3. expires_at past → REFRESH_TOKEN_EXPIRED 401
     */
    fun refresh(refreshToken: String): TokenPairDto {
        val tokenHash   = jwtService.hashToken(refreshToken)
        val tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: throw PlatformException(
                "REFRESH_TOKEN_INVALID",
                "Invalid session. Please log in again.",
                HttpStatus.UNAUTHORIZED,
            )

        if (tokenEntity.revokedAt != null) {
            throw PlatformException(
                "REFRESH_TOKEN_INVALID",
                "Invalid session. Please log in again.",
                HttpStatus.UNAUTHORIZED,
            )
        }

        if (tokenEntity.expiresAt.isBefore(Instant.now())) {
            throw PlatformException(
                "REFRESH_TOKEN_EXPIRED",
                "Your session has expired. Please log in again.",
                HttpStatus.UNAUTHORIZED,
            )
        }

        // Revoke the old token before issuing a new one (rotation).
        tokenEntity.revokedAt = Instant.now()
        refreshTokenRepository.save(tokenEntity)

        // Load the owning user.
        val user = userRepository.findById(tokenEntity.userId).orElseThrow {
            PlatformException(
                "REFRESH_TOKEN_INVALID",
                "Invalid session. Please log in again.",
                HttpStatus.UNAUTHORIZED,
            )
        }

        // generateTokenPair() creates + persists the new refresh token hash.
        return jwtService.generateTokenPair(user)
    }

    // ── logout ────────────────────────────────────────────────────────────────

    /**
     * Revokes the supplied refresh token.
     * Idempotent — returns success silently when token is not found or already revoked.
     * Only this token is revoked; other sessions for the same user are unaffected.
     */
    fun logout(refreshToken: String): LogoutResponse {
        val tokenHash   = jwtService.hashToken(refreshToken)
        val tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)

        if (tokenEntity != null && tokenEntity.revokedAt == null) {
            tokenEntity.revokedAt = Instant.now()
            refreshTokenRepository.save(tokenEntity)
        }

        return LogoutResponse(message = "Logged out successfully")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Derives the expected OTP purpose from the user's current status.
     * SUSPENDED users are treated as not found (opaque 404) to avoid status disclosure.
     */
    private fun resolvePurpose(user: UserEntity): OtpPurpose = when (user.status) {
        UserStatus.PENDING_VERIFICATION -> OtpPurpose.EMAIL_VERIFICATION
        UserStatus.ACTIVE               -> OtpPurpose.LOGIN
        UserStatus.SUSPENDED            -> throw PlatformException(
            "ACCOUNT_NOT_FOUND",
            "No account found for this email.",
            HttpStatus.NOT_FOUND,
        )
    }
}

