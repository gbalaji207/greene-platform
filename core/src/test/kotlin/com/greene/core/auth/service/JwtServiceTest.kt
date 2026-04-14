package com.greene.core.auth.service


import io.mockk.*
import com.greene.core.auth.config.JwtProperties
import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.repository.RefreshTokenRepository
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Pure unit tests for [JwtService].
 *
 * A real 2048-bit RSA key pair is generated once in [setup] and shared across
 * all tests. This avoids Spring context overhead while still exercising the
 * actual RSA signing and verification logic.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JwtServiceTest {

    private lateinit var keyPair       : KeyPair
    private lateinit var jwtProperties : JwtProperties
    private val refreshTokenRepository : RefreshTokenRepository = mockk()
    private lateinit var service       : JwtService

    @BeforeAll
    fun setup() {
        keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encodedKey = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$encodedKey\n-----END PRIVATE KEY-----"
        jwtProperties = JwtProperties(privateKey = pem, accessTokenExpiryMinutes = 15)
        service = JwtService(jwtProperties, refreshTokenRepository)
    }

    // Clear recorded invocations between tests so verify(exactly = 1) is per-test scoped.
    @BeforeEach
    fun resetMocks() {
        clearMocks(refreshTokenRepository)
    }

    // ── generateTokenPair ─────────────────────────────────────────────────────

    @Test
    fun `generateTokenPair returns JWT with correct sub email and role claims`() {
        val user = testUser()
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val tokens = service.generateTokenPair(user)

        assertNotNull(tokens.accessToken)
        assertEquals(900, tokens.expiresIn, "expiresIn must be accessTokenExpiryMinutes * 60 = 900")

        // Parse independently using the raw test public key to verify claims
        val claims = Jwts.parser()
            .verifyWith(keyPair.public as RSAPublicKey)
            .build()
            .parseSignedClaims(tokens.accessToken)
            .payload

        assertEquals(user.id.toString(), claims.subject)
        assertEquals(user.email,         claims["email"])
        assertEquals(user.role.name,     claims["role"])
    }

    @Test
    fun `generateTokenPair stores a SHA-256 hash of the refresh token not the plaintext`() {
        val user       = testUser()
        val savedSlot  = slot<com.greene.core.auth.domain.RefreshTokenEntity>()
        every { refreshTokenRepository.save(capture(savedSlot)) } answers { firstArg() }

        val tokens = service.generateTokenPair(user)

        verify(exactly = 1) { refreshTokenRepository.save(any()) }
        assertTrue(savedSlot.captured.tokenHash.isNotBlank())
        assertNotEquals(
            tokens.refreshToken,
            savedSlot.captured.tokenHash,
            "Refresh token must be stored as a hash, never plaintext",
        )
        // SHA-256 hex digest is always 64 hex characters
        assertEquals(64, savedSlot.captured.tokenHash.length)
    }

    // ── validateAccessToken ───────────────────────────────────────────────────

    @Test
    fun `validateAccessToken returns claims for a freshly generated token`() {
        val user = testUser()
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val tokens = service.generateTokenPair(user)
        val claims = service.validateAccessToken(tokens.accessToken)

        assertEquals(user.id.toString(), claims.subject)
        assertEquals(user.email,         claims["email"])
    }

    @Test
    fun `validateAccessToken throws ExpiredJwtException for a token past its expiry`() {
        // Build an expired JWT directly using the same key pair
        val expiredToken = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
            .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
            .signWith(keyPair.private)
            .compact()

        assertThrows<ExpiredJwtException> {
            service.validateAccessToken(expiredToken)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun testUser() = UserEntity(
        id     = UUID.randomUUID(),
        email  = "test@example.com",
        name   = "Test User",
        phone  = "+919876543210",
        role   = UserRole.CLIENT,
        status = UserStatus.ACTIVE,
    )
}



