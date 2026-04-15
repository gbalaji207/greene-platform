package com.greene.core.auth.service


import io.mockk.*
import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.domain.OtpPurpose
import com.greene.core.auth.dto.TokenPairDto
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

class AuthServiceTest {

    private val userRepository   : UserRepository    = mockk()
    private val otpService       : OtpService        = mockk()
    private val rateLimitService : RateLimitService  = mockk()
    private val emailService     : EmailService      = mockk()
    private val jwtService       : JwtService        = mockk()

    private val service = AuthService(
        userRepository, otpService, rateLimitService, emailService, jwtService
    )

    private val email      = "User@Example.com"   // mixed-case to verify normalisation
    private val normalised = "user@example.com"
    private val userId     = UUID.randomUUID()

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
}

