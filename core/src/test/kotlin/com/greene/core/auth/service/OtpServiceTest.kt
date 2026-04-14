package com.greene.core.auth.service


import io.mockk.*
import com.greene.core.auth.config.OtpProperties
import com.greene.core.auth.domain.OtpPurpose
import com.greene.core.auth.domain.OtpTokenEntity
import com.greene.core.auth.repository.OtpTokenRepository
import com.greene.core.exception.PlatformException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class OtpServiceTest {

    private val otpTokenRepository: OtpTokenRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val otpProperties = OtpProperties(
        expiryMinutes          = 10,
        resendWaitMinutes      = 1,
        maxResendAttempts      = 3,
        rateLimitMax           = 3,
        rateLimitWindowMinutes = 10,
    )

    private val redisTemplate: StringRedisTemplate = mockk()
    private val redisOps: ValueOperations<String, String> = mockk()

    private val service = OtpService(otpTokenRepository, passwordEncoder, otpProperties, redisTemplate)

    private val userId  = UUID.randomUUID()
    private val purpose = OtpPurpose.EMAIL_VERIFICATION

    // ── generateAndPersist ────────────────────────────────────────────────────

    @Test
    fun `generateAndPersist returns a 6-digit numeric OTP`() {
        every { otpTokenRepository.invalidateAllActiveByUserIdAndPurpose(any(), any(), any()) } returns 0
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { otpTokenRepository.save(any()) } answers { firstArg() }
        every { redisTemplate.opsForValue() } returns redisOps
        justRun { redisOps.set(any(), any(), any<Long>(), any()) }

        val otp = service.generateAndPersist(userId, purpose)

        assertEquals(6, otp.length, "OTP must be 6 characters, got: $otp")
        assertTrue(otp.all { it.isDigit() }, "OTP must be numeric, got: $otp")
    }

    @Test
    fun `generateAndPersist invalidates prior active OTP then persists a new one`() {
        every { otpTokenRepository.invalidateAllActiveByUserIdAndPurpose(any(), any(), any()) } returns 1
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { otpTokenRepository.save(any()) } answers { firstArg() }
        every { redisTemplate.opsForValue() } returns redisOps
        justRun { redisOps.set(any(), any(), any<Long>(), any()) }

        service.generateAndPersist(userId, purpose)

        verify(exactly = 1) { otpTokenRepository.invalidateAllActiveByUserIdAndPurpose(userId, purpose, any()) }
        verify(exactly = 1) {
            otpTokenRepository.save(
                match { it.userId == userId && it.purpose == purpose && it.otpHash == "hashed" }
            )
        }
    }

    @Test
    fun `generateAndPersist stores plaintext OTP in Redis with OTP expiry TTL`() {
        every { otpTokenRepository.invalidateAllActiveByUserIdAndPurpose(any(), any(), any()) } returns 0
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { otpTokenRepository.save(any()) } answers { firstArg() }
        every { redisTemplate.opsForValue() } returns redisOps
        justRun { redisOps.set(any(), any(), any<Long>(), any()) }

        service.generateAndPersist(userId, purpose)

        verify(exactly = 1) {
            redisOps.set(
                "otp:plaintext:$userId",
                any(),
                otpProperties.expiryMinutes.toLong(),
                java.util.concurrent.TimeUnit.MINUTES,
            )
        }
    }

    // ── verifyAndConsume ──────────────────────────────────────────────────────

    @Test
    fun `verifyAndConsume marks OTP as used on correct code`() {
        val token = activeToken()
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns token
        every { passwordEncoder.matches("123456", "hashed") } returns true
        every { otpTokenRepository.save(any()) } answers { firstArg() }

        service.verifyAndConsume(userId, purpose, "123456")

        verify { otpTokenRepository.save(match { it.usedAt != null }) }
    }

    @Test
    fun `verifyAndConsume throws OTP_ALREADY_USED when latest token was previously consumed`() {
        val usedToken = OtpTokenEntity(
            userId    = userId,
            otpHash   = "hashed",
            purpose   = purpose,
            expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES),
            usedAt    = Instant.now(),
        )
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns null
        every { otpTokenRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose) } returns usedToken

        val ex = assertThrows<PlatformException> {
            service.verifyAndConsume(userId, purpose, "123456")
        }
        assertEquals("OTP_ALREADY_USED", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    @Test
    fun `verifyAndConsume throws OTP_NOT_FOUND when no OTP record exists at all`() {
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns null
        every { otpTokenRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose) } returns null

        val ex = assertThrows<PlatformException> {
            service.verifyAndConsume(userId, purpose, "123456")
        }
        assertEquals("OTP_NOT_FOUND", ex.code)
    }

    @Test
    fun `verifyAndConsume throws OTP_EXPIRED when token expiresAt is in the past`() {
        val expiredToken = activeToken(expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES))
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns expiredToken

        val ex = assertThrows<PlatformException> {
            service.verifyAndConsume(userId, purpose, "123456")
        }
        assertEquals("OTP_EXPIRED", ex.code)
    }

    @Test
    fun `verifyAndConsume throws INVALID_OTP on bcrypt mismatch`() {
        val token = activeToken()
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns token
        every { passwordEncoder.matches("wrong", "hashed") } returns false

        val ex = assertThrows<PlatformException> {
            service.verifyAndConsume(userId, purpose, "wrong")
        }
        assertEquals("INVALID_OTP", ex.code)
    }

    // ── prepareResend ─────────────────────────────────────────────────────────

    @Test
    fun `prepareResend returns original OTP from Redis and updates metadata without changing otp_hash`() {
        val token = activeToken(
            createdAt   = Instant.now().minus(2, ChronoUnit.MINUTES), // wait window elapsed
            resendCount = 0,
        )
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns token
        every { redisTemplate.opsForValue() } returns redisOps
        every { redisOps.get("otp:plaintext:$userId") } returns "123456"
        every { otpTokenRepository.save(any()) } answers { firstArg() }

        val result = service.prepareResend(userId, purpose)

        assertEquals("123456", result.otp, "Must resend the SAME OTP retrieved from Redis")
        assertTrue(result.nextResendAllowedAt.isAfter(Instant.now()))
        verify {
            otpTokenRepository.save(
                // otp_hash must be unchanged; only resendCount and lastResentAt are updated
                match { it.resendCount == 1.toShort() && it.otpHash == "hashed" && it.lastResentAt != null }
            )
        }
        // Ensure no new OTP was encoded (passwordEncoder.encode must NOT be called)
        verify(exactly = 0) { passwordEncoder.encode(any()) }
    }

    @Test
    fun `prepareResend throws OTP_NOT_FOUND when no active token is found`() {
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns null

        val ex = assertThrows<PlatformException> {
            service.prepareResend(userId, purpose)
        }
        assertEquals("OTP_NOT_FOUND", ex.code)
    }

    @Test
    fun `prepareResend throws OTP_EXPIRED when active token is past expiresAt`() {
        val token = activeToken(expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES))
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns token

        val ex = assertThrows<PlatformException> {
            service.prepareResend(userId, purpose)
        }
        assertEquals("OTP_EXPIRED", ex.code)
    }

    @Test
    fun `prepareResend throws OTP_EXPIRED when Redis key has expired`() {
        val token = activeToken(
            createdAt   = Instant.now().minus(2, ChronoUnit.MINUTES),
            resendCount = 0,
        )
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns token
        every { redisTemplate.opsForValue() } returns redisOps
        every { redisOps.get("otp:plaintext:$userId") } returns null

        val ex = assertThrows<PlatformException> {
            service.prepareResend(userId, purpose)
        }
        assertEquals("OTP_EXPIRED", ex.code)
    }

    @Test
    fun `prepareResend throws MAX_RESEND_ATTEMPTS when resend count reaches the configured limit`() {
        val token = activeToken(resendCount = 3) // equal to maxResendAttempts
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns token

        val ex = assertThrows<PlatformException> {
            service.prepareResend(userId, purpose)
        }
        assertEquals("MAX_RESEND_ATTEMPTS", ex.code)
    }

    @Test
    fun `prepareResend throws RESEND_TOO_SOON before minimum wait window has elapsed`() {
        val token = activeToken(
            createdAt   = Instant.now(), // just created — 1-minute wait has not elapsed
            resendCount = 0,
        )
        every { otpTokenRepository.findActiveByUserIdAndPurpose(userId, purpose) } returns token

        val ex = assertThrows<PlatformException> {
            service.prepareResend(userId, purpose)
        }
        assertEquals("RESEND_TOO_SOON", ex.code)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun activeToken(
        expiresAt   : Instant = Instant.now().plus(5, ChronoUnit.MINUTES),
        createdAt   : Instant = Instant.now().minus(5, ChronoUnit.MINUTES),
        resendCount : Int     = 0,
    ) = OtpTokenEntity(
        userId      = userId,
        otpHash     = "hashed",
        purpose     = purpose,
        expiresAt   = expiresAt,
        createdAt   = createdAt,
        resendCount = resendCount.toShort(),
    )
}


