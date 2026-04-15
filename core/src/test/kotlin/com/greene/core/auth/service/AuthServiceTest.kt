package com.greene.core.auth.service


import io.mockk.*
import com.greene.core.auth.domain.OtpPurpose
import com.greene.core.auth.domain.RefreshTokenEntity
import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.dto.LogoutResponse
import com.greene.core.auth.dto.TokenPairDto
import com.greene.core.auth.repository.RefreshTokenRepository
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class AuthServiceTest {

    private val userRepository      : UserRepository       = mockk()
    private val otpService          : OtpService           = mockk()
    private val rateLimitService    : RateLimitService     = mockk()
    private val emailService        : EmailService         = mockk()
    private val jwtService          : JwtService           = mockk()
    private val refreshTokenRepository : RefreshTokenRepository = mockk()

    private val service = AuthService(
        userRepository, otpService, rateLimitService, emailService, jwtService, refreshTokenRepository
    )

    private val email      = "User@Example.com"   // mixed-case to verify normalisation
    private val normalised = "user@example.com"
    private val userId     = UUID.randomUUID()

    // Raw opaque token (UUID format, as issued to the client) and its simulated hash
    private val rawRefreshToken = "550e8400-e29b-41d4-a716-446655440000"
    private val tokenHash       = "3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c"

    // ── identify ──────────────────────────────────────────────────────────────

    @Test
    fun `identify returns LOGIN flow for ACTIVE user and sends OTP`() {
        every { userRepository.findByEmail(normalised) } returns activeUser()
        justRun { rateLimitService.checkAndIncrement(normalised) }
        every { otpService.generateAndPersist(userId, OtpPurpose.LOGIN) } returns "654321"
        justRun { emailService.sendOtp(normalised, "654321") }

        val response = service.identify(email)

        assertEquals("LOGIN", response.flow)
        verify(exactly = 1) { rateLimitService.checkAndIncrement(normalised) }
        verify(exactly = 1) { otpService.generateAndPersist(userId, OtpPurpose.LOGIN) }
        verify(exactly = 1) { emailService.sendOtp(normalised, "654321") }
    }

    @Test
    fun `identify returns REGISTER flow for unknown email without sending OTP`() {
        every { userRepository.findByEmail(normalised) } returns null

        val response = service.identify(email)

        assertEquals("REGISTER", response.flow)
        verify(exactly = 0) { otpService.generateAndPersist(any(), any()) }
        verify(exactly = 0) { emailService.sendOtp(any(), any()) }
    }

    @Test
    fun `identify returns REGISTER flow for PENDING user without sending OTP`() {
        every { userRepository.findByEmail(normalised) } returns pendingUser()

        val response = service.identify(email)

        assertEquals("REGISTER", response.flow)
        verify(exactly = 0) { otpService.generateAndPersist(any(), any()) }
    }

    @Test
    fun `identify throws ACCOUNT_SUSPENDED for SUSPENDED user`() {
        every { userRepository.findByEmail(normalised) } returns suspendedUser()

        val ex = assertThrows<PlatformException> {
            service.identify(email)
        }

        assertEquals("ACCOUNT_SUSPENDED", ex.code)
        assertEquals(HttpStatus.FORBIDDEN, ex.httpStatus)
        assertEquals(
            "Your account has been suspended. Please contact your administrator.",
            ex.message,
        )
    }

    @Test
    fun `identify does not generate OTP and does not send email when account is SUSPENDED`() {
        every { userRepository.findByEmail(normalised) } returns suspendedUser()

        assertThrows<PlatformException> { service.identify(email) }

        verify(exactly = 0) { otpService.generateAndPersist(any(), any()) }
        verify(exactly = 0) { emailService.sendOtp(any(), any()) }
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    fun `register throws EMAIL_ALREADY_ACTIVE for ACTIVE user and skips rate-limit check`() {
        every { userRepository.findByEmail(normalised) } returns activeUser()

        val ex = assertThrows<PlatformException> {
            service.register(email, "Arun", "+919876543210")
        }

        assertEquals("EMAIL_ALREADY_ACTIVE", ex.code)
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
        verify(exactly = 0) { rateLimitService.checkAndIncrement(any()) }
    }

    @Test
    fun `register creates new user and sends OTP when email not found`() {
        val savedUser = pendingUser()
        every { userRepository.findByEmail(normalised) } returns null
        every { userRepository.findByPhone("+919876543210") } returns null
        justRun { rateLimitService.checkAndIncrement(normalised) }
        every { userRepository.save(any()) } returns savedUser
        every { otpService.generateAndPersist(userId, OtpPurpose.EMAIL_VERIFICATION) } returns "111111"
        justRun { emailService.sendOtp(normalised, "111111") }

        val response = service.register(email, "Arun", "+919876543210")

        assertEquals("OTP_SENT", response.flow)
        verify {
            userRepository.save(
                match { it.email == normalised && it.status == UserStatus.PENDING_VERIFICATION }
            )
        }
        verify(exactly = 1) { emailService.sendOtp(normalised, "111111") }
    }

    @Test
    fun `register updates PENDING user fields and sends fresh OTP on re-registration`() {
        val pending = pendingUser(name = "OldName", phone = "+910000000000")
        every { userRepository.findByEmail(normalised) } returns pending
        every { userRepository.findByPhone("+919876543210") } returns null
        justRun { rateLimitService.checkAndIncrement(normalised) }
        every { userRepository.save(pending) } returns pending
        every { otpService.generateAndPersist(userId, OtpPurpose.EMAIL_VERIFICATION) } returns "222222"
        justRun { emailService.sendOtp(normalised, "222222") }

        service.register(email, "Arun", "+919876543210")

        // Entity mutated in-place before save — verify updated fields
        verify { userRepository.save(match { it.name == "Arun" && it.phone == "+919876543210" }) }
    }

    @Test
    fun `register normalises email to lowercase before persisting`() {
        every { userRepository.findByEmail(normalised) } returns null
        every { userRepository.findByPhone("+919876543210") } returns null
        justRun { rateLimitService.checkAndIncrement(normalised) }
        every { userRepository.save(any()) } returns pendingUser()
        every { otpService.generateAndPersist(any(), any()) } returns "333333"
        justRun { emailService.sendOtp(any(), any()) }

        service.register("USER@EXAMPLE.COM", "Arun", "+919876543210")

        verify { userRepository.save(match { it.email == normalised }) }
    }

    // ── verifyOtp ─────────────────────────────────────────────────────────────

    @Test
    fun `verifyOtp throws ACCOUNT_NOT_FOUND for unknown email`() {
        every { userRepository.findByEmail(normalised) } returns null

        val ex = assertThrows<PlatformException> {
            service.verifyOtp(email, "123456")
        }
        assertEquals("ACCOUNT_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `verifyOtp activates PENDING user sends welcome email and returns tokens`() {
        val pending = pendingUser()
        val tokens  = TokenPairDto("access-token", "refresh-token", 900)

        every { userRepository.findByEmail(normalised) } returns pending
        justRun { otpService.verifyAndConsume(userId, OtpPurpose.EMAIL_VERIFICATION, "123456") }
        justRun { emailService.sendWelcome(normalised, pending.name) }
        every { userRepository.save(any()) } answers { firstArg() }
        every { jwtService.generateTokenPair(any()) } returns tokens

        val response = service.verifyOtp(email, "123456")

        assertEquals("access-token", response.accessToken)
        assertEquals(UserStatus.ACTIVE, pending.status, "User status must be ACTIVE after verification")
        verify(exactly = 1) { emailService.sendWelcome(normalised, pending.name) }
    }

    @Test
    fun `verifyOtp returns tokens for ACTIVE user without sending welcome email`() {
        val user   = activeUser()
        val tokens = TokenPairDto("access-token", "refresh-token", 900)

        every { userRepository.findByEmail(normalised) } returns user
        justRun { otpService.verifyAndConsume(userId, OtpPurpose.LOGIN, "123456") }
        every { userRepository.save(any()) } answers { firstArg() }
        every { jwtService.generateTokenPair(any()) } returns tokens

        val response = service.verifyOtp(email, "123456")

        assertEquals("access-token", response.accessToken)
        verify(exactly = 0) { emailService.sendWelcome(any(), any()) }
    }

    @Test
    fun `verifyOtp throws ACCOUNT_NOT_FOUND for SUSPENDED user to prevent status disclosure`() {
        every { userRepository.findByEmail(normalised) } returns suspendedUser()

        val ex = assertThrows<PlatformException> {
            service.verifyOtp(email, "123456")
        }
        assertEquals("ACCOUNT_NOT_FOUND", ex.code)
        verify(exactly = 0) { otpService.verifyAndConsume(any(), any(), any()) }
    }

    // ── resendOtp ─────────────────────────────────────────────────────────────

    @Test
    fun `resendOtp throws ACCOUNT_NOT_FOUND for unknown email`() {
        every { userRepository.findByEmail(normalised) } returns null

        val ex = assertThrows<PlatformException> {
            service.resendOtp(email)
        }
        assertEquals("ACCOUNT_NOT_FOUND", ex.code)
    }

    @Test
    fun `resendOtp delegates to OtpService and EmailService and returns OTP_RESENT`() {
        val user         = pendingUser()
        val resendResult = ResendResult(
            otp                  = "456789",
            nextResendAllowedAt  = Instant.now().plusSeconds(60),
        )
        every { userRepository.findByEmail(normalised) } returns user
        every { otpService.prepareResend(userId, OtpPurpose.EMAIL_VERIFICATION) } returns resendResult
        justRun { emailService.sendOtp(normalised, "456789") }

        val response = service.resendOtp(email)

        assertEquals("OTP_RESENT", response.flow)
        assertEquals(resendResult.nextResendAllowedAt, response.nextResendAllowedAt)
        verify(exactly = 1) { emailService.sendOtp(normalised, "456789") }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun activeUser() = UserEntity(
        id     = userId,
        email  = normalised,
        name   = "Arun Kumar",
        phone  = "+919876543210",
        role   = UserRole.CLIENT,
        status = UserStatus.ACTIVE,
    )

    private fun pendingUser(name: String = "Arun Kumar", phone: String = "+919876543210") = UserEntity(
        id     = userId,
        email  = normalised,
        name   = name,
        phone  = phone,
        role   = UserRole.CLIENT,
        status = UserStatus.PENDING_VERIFICATION,
    )

    private fun suspendedUser() = UserEntity(
        id     = userId,
        email  = normalised,
        name   = "Arun Kumar",
        phone  = "+919876543210",
        role   = UserRole.CLIENT,
        status = UserStatus.SUSPENDED,
    )

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    fun `refresh_whenTokenValid_shouldReturnNewTokenPairAndRevokeOldToken`() {
        val entity  = activeRefreshTokenEntity()
        val newPair = TokenPairDto("new-access", "new-refresh", 900)

        every { jwtService.hashToken(rawRefreshToken) }               returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) }   returns entity
        every { refreshTokenRepository.save(entity) }                  returns entity
        every { userRepository.findById(userId) }                      returns Optional.of(activeUser())
        every { jwtService.generateTokenPair(any()) }                  returns newPair

        val result = service.refresh(rawRefreshToken)

        assertEquals("new-access",   result.accessToken)
        assertEquals("new-refresh",  result.refreshToken)
        assertEquals(900,            result.expiresIn)
        assertNotNull(entity.revokedAt, "Old token must have revokedAt set after rotation")
    }

    @Test
    fun `refresh_whenTokenNotFound_shouldThrowRefreshTokenInvalid401`() {
        every { jwtService.hashToken(rawRefreshToken) }             returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns null

        val ex = assertThrows<PlatformException> { service.refresh(rawRefreshToken) }

        assertEquals("REFRESH_TOKEN_INVALID", ex.code)
        assertEquals(HttpStatus.UNAUTHORIZED,  ex.httpStatus)
        assertEquals("Invalid session. Please log in again.", ex.message)
    }

    @Test
    fun `refresh_whenTokenRevoked_shouldThrowRefreshTokenInvalid401`() {
        every { jwtService.hashToken(rawRefreshToken) }             returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns revokedRefreshTokenEntity()

        val ex = assertThrows<PlatformException> { service.refresh(rawRefreshToken) }

        assertEquals("REFRESH_TOKEN_INVALID", ex.code)
        assertEquals(HttpStatus.UNAUTHORIZED,  ex.httpStatus)
    }

    @Test
    fun `refresh_whenTokenExpired_shouldThrowRefreshTokenExpired401`() {
        every { jwtService.hashToken(rawRefreshToken) }             returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns expiredRefreshTokenEntity()

        val ex = assertThrows<PlatformException> { service.refresh(rawRefreshToken) }

        assertEquals("REFRESH_TOKEN_EXPIRED", ex.code)
        assertEquals(HttpStatus.UNAUTHORIZED,  ex.httpStatus)
        assertEquals("Your session has expired. Please log in again.", ex.message)
    }

    @Test
    fun `refresh_shouldRevokeOldTokenBeforeIssuingNewOne`() {
        val entity      = activeRefreshTokenEntity()
        val savedSlot   = slot<RefreshTokenEntity>()
        val newPair     = TokenPairDto("at", "rt", 900)

        every { jwtService.hashToken(rawRefreshToken) }                    returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) }        returns entity
        every { refreshTokenRepository.save(capture(savedSlot)) }          returns entity
        every { userRepository.findById(userId) }                          returns Optional.of(activeUser())
        every { jwtService.generateTokenPair(any()) }                      returns newPair

        service.refresh(rawRefreshToken)

        // Old token must be revoked (revokedAt set) in the save call
        assertNotNull(savedSlot.captured.revokedAt, "Entity passed to save() must have revokedAt set")

        // save() must be called strictly before generateTokenPair()
        verifyOrder {
            refreshTokenRepository.save(any())
            jwtService.generateTokenPair(any())
        }
    }

    @Test
    fun `refresh_whenSameTokenUsedTwice_shouldReturn401OnSecondCall`() {
        val entity  = activeRefreshTokenEntity()   // revokedAt = null initially
        val newPair = TokenPairDto("at", "rt", 900)

        every { jwtService.hashToken(rawRefreshToken) }             returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns entity   // same mutable object each time
        every { refreshTokenRepository.save(entity) }               returns entity
        every { userRepository.findById(userId) }                   returns Optional.of(activeUser())
        every { jwtService.generateTokenPair(any()) }               returns newPair

        // First call: entity.revokedAt is null → succeeds and sets revokedAt on the entity
        service.refresh(rawRefreshToken)

        // Second call: the mock returns the SAME entity, which now has revokedAt set
        val ex = assertThrows<PlatformException> { service.refresh(rawRefreshToken) }

        assertEquals("REFRESH_TOKEN_INVALID", ex.code)
        assertEquals(HttpStatus.UNAUTHORIZED,  ex.httpStatus)
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    fun `logout_whenTokenActive_shouldRevokeTokenAndReturnSuccess`() {
        val entity = activeRefreshTokenEntity()

        every { jwtService.hashToken(rawRefreshToken) }             returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns entity
        every { refreshTokenRepository.save(entity) }               returns entity

        val result = service.logout(rawRefreshToken)

        assertEquals(LogoutResponse(message = "Logged out successfully"), result)
        assertNotNull(entity.revokedAt, "Token must be revoked on logout")
        verify(exactly = 1) { refreshTokenRepository.save(entity) }
    }

    @Test
    fun `logout_whenTokenNotFound_shouldReturnSuccessSilently`() {
        every { jwtService.hashToken(rawRefreshToken) }             returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns null

        val result = service.logout(rawRefreshToken)

        assertEquals(LogoutResponse(message = "Logged out successfully"), result)
        verify(exactly = 0) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `logout_whenTokenAlreadyRevoked_shouldReturnSuccessSilently`() {
        every { jwtService.hashToken(rawRefreshToken) }             returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns revokedRefreshTokenEntity()

        val result = service.logout(rawRefreshToken)

        assertEquals(LogoutResponse(message = "Logged out successfully"), result)
        verify(exactly = 0) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `logout_shouldOnlyRevokeSuppliedToken_notAllUserTokens`() {
        val entity = activeRefreshTokenEntity()

        every { jwtService.hashToken(rawRefreshToken) }             returns tokenHash
        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns entity
        every { refreshTokenRepository.save(entity) }               returns entity

        service.logout(rawRefreshToken)

        verify(exactly = 0) { refreshTokenRepository.revokeAllActiveByUserId(any(), any()) }
    }

    // ── E2-US3 helpers ────────────────────────────────────────────────────────

    private fun activeRefreshTokenEntity() = RefreshTokenEntity(
        userId    = userId,
        tokenHash = tokenHash,
        expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
    )

    private fun revokedRefreshTokenEntity() = RefreshTokenEntity(
        userId    = userId,
        tokenHash = tokenHash,
        expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
        revokedAt = Instant.now().minusSeconds(60),
    )

    private fun expiredRefreshTokenEntity() = RefreshTokenEntity(
        userId    = userId,
        tokenHash = tokenHash,
        expiresAt = Instant.now().minusSeconds(1),   // one second in the past
    )
}

