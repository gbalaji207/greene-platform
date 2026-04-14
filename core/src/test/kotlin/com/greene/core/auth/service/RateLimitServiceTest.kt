package com.greene.core.auth.service


import io.mockk.*
import com.greene.core.auth.config.OtpProperties
import com.greene.core.exception.PlatformException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import java.util.concurrent.TimeUnit

class RateLimitServiceTest {

    private val redisTemplate : StringRedisTemplate          = mockk()
    private val valueOps      : ValueOperations<String, String> = mockk()
    private val otpProperties = OtpProperties(
        rateLimitMax            = 3,
        rateLimitWindowMinutes  = 10,
    )

    private val service = RateLimitService(redisTemplate, otpProperties)

    private val key = "otp:ratelimit:user@example.com"

    init {
        every { redisTemplate.opsForValue() } returns valueOps
    }

    // ── checkAndIncrement ─────────────────────────────────────────────────────

    @Test
    fun `checkAndIncrement does not throw on first hit and sets the expiry window`() {
        every { valueOps.increment(key) } returns 1L
        every { redisTemplate.expire(key, 10L, TimeUnit.MINUTES) } returns true

        assertDoesNotThrow { service.checkAndIncrement("user@example.com") }

        verify(exactly = 1) { redisTemplate.expire(key, 10L, TimeUnit.MINUTES) }
    }

    @Test
    fun `checkAndIncrement does not reset TTL on subsequent hits`() {
        every { valueOps.increment(key) } returns 2L

        assertDoesNotThrow { service.checkAndIncrement("user@example.com") }

        // expire must NOT be called — window was set on the first hit
        verify(exactly = 0) { redisTemplate.expire(any(), any<Long>(), any()) }
    }

    @Test
    fun `checkAndIncrement throws RATE_LIMIT_EXCEEDED when counter exceeds rateLimitMax`() {
        every { valueOps.increment(key) } returns 4L // rateLimitMax is 3

        val ex = assertThrows<PlatformException> {
            service.checkAndIncrement("user@example.com")
        }
        assertEquals("RATE_LIMIT_EXCEEDED", ex.code)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.httpStatus)
    }

    @Test
    fun `checkAndIncrement throws INTERNAL_ERROR when Redis returns null`() {
        every { valueOps.increment(any()) } returns null

        val ex = assertThrows<PlatformException> {
            service.checkAndIncrement("user@example.com")
        }
        assertEquals("INTERNAL_ERROR", ex.code)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.httpStatus)
    }

    @Test
    fun `checkAndIncrement normalises email to lowercase before forming the Redis key`() {
        every { valueOps.increment(key) } returns 1L
        every { redisTemplate.expire(any(), any<Long>(), any()) } returns true

        service.checkAndIncrement("USER@EXAMPLE.COM")

        verify { valueOps.increment(key) }
    }
}

