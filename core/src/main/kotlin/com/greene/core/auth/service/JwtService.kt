package com.greene.core.auth.service

import com.greene.core.auth.config.JwtProperties
import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.dto.TokenPairDto
import com.greene.core.auth.domain.RefreshTokenEntity
import com.greene.core.auth.repository.RefreshTokenRepository
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Issues and validates RS256-signed JWT access tokens, and manages opaque refresh tokens.
 *
 * ── Access token ─────────────────────────────────────────────────────────────
 *  Algorithm : RS256
 *  Claims    : sub (userId UUID), email, role, iat, exp
 *  Expiry    : jwt.access-token-expiry-minutes (default 15 min)
 *  Private key: PKCS#8 PEM supplied via jwt.private-key config key.
 *               Newlines may be literal \n (common in env vars) or actual line breaks.
 *
 * ── Refresh token ────────────────────────────────────────────────────────────
 *  Format  : SecureRandom UUID (opaque, base-64 URL safe string not needed — raw UUID)
 *  Storage : SHA-256 hex digest stored in refresh_tokens.token_hash
 *  Expiry  : jwt.refresh-token-expiry-days config (default 30 days)
 *  Rotation: old token revoked by AuthService.refresh() before calling generateTokenPair()
 */
@Service
class JwtService(
    private val jwtProperties: JwtProperties,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    companion object {
        private const val CLAIM_EMAIL = "email"
        private const val CLAIM_ROLE  = "role"
    }

    // ── Lazy key initialisation ───────────────────────────────────────────────
    // Keys are expensive to parse; initialise once and reuse.

    private val privateKey: RSAPrivateKey by lazy {
        loadPrivateKey(jwtProperties.privateKey)
    }

    /**
     * RSA public key derived from the private key (CRT components).
     * No separate public-key config needed — keeps deployment simple.
     */
    private val publicKey: RSAPublicKey by lazy {
        val crt = privateKey as? RSAPrivateCrtKey
            ?: error("JWT private key must be in PKCS#8 CRT format (standard RSA key).")
        val spec = RSAPublicKeySpec(crt.modulus, crt.publicExponent)
        KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates an RS256 access token + opaque refresh token for [user].
     * The refresh token's SHA-256 hash is persisted to [refresh_tokens].
     *
     * Must be called inside a transaction (annotated here so it can be called
     * from a non-transactional context if needed).
     */
    @Transactional
    fun generateTokenPair(user: UserEntity): TokenPairDto {
        val userId     = requireNotNull(user.id) { "User must be persisted before generating tokens." }
        val now        = Instant.now()
        val expiryMins = jwtProperties.accessTokenExpiryMinutes
        val expiresAt  = now.plus(expiryMins.toLong(), ChronoUnit.MINUTES)

        val accessToken = Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_EMAIL, user.email)
            .claim(CLAIM_ROLE,  user.role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(privateKey)          // JJWT auto-selects RS256 for RSAPrivateKey
            .compact()

        // Refresh token: opaque UUID stored hashed
        val rawRefreshToken = UUID.randomUUID().toString()
        refreshTokenRepository.save(
            RefreshTokenEntity(
                userId    = userId,
                tokenHash = sha256Hex(rawRefreshToken),
                expiresAt = now.plus(jwtProperties.refreshTokenExpiryDays.toLong(), ChronoUnit.DAYS),
            )
        )

        return TokenPairDto(
            accessToken  = accessToken,
            refreshToken = rawRefreshToken,
            expiresIn    = expiryMins * 60,
        )
    }

    /**
     * Parses and validates an RS256-signed access token.
     * Returns the [Claims] payload on success.
     *
     * Throws [io.jsonwebtoken.JwtException] (or a subtype) on any failure:
     *  - Expired token    → [io.jsonwebtoken.ExpiredJwtException]
     *  - Bad signature    → [io.jsonwebtoken.security.SecurityException]
     *  - Malformed token  → [io.jsonwebtoken.MalformedJwtException]
     *
     * The [com.greene.core.web.GlobalExceptionHandler] should be extended to map
     * these to a 401 response for protected endpoints (E2-US2 concern).
     */
    fun validateAccessToken(token: String): Claims =
        Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .payload

    // ── Hashing utility ──────────────────────────────────────────────────────

    /**
     * Returns the lowercase hex SHA-256 digest of [input].
     * Used by callers (e.g. AuthService) to hash a raw refresh token before
     * looking it up or saving it.
     */
    fun hashToken(input: String): String = sha256Hex(input)

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Loads a PKCS#8 RSA private key from a PEM string.
     *
     * Accepts both PKCS#8 (`-----BEGIN PRIVATE KEY-----`) and handles
     * literal `\n` sequences (common when supplying multiline values via
     * environment variables in container deployments).
     *
     * Handles three common env-var storage artefacts:
     *  1. base64url alphabet (`-` / `_`) — normalised to standard (`+` / `/`)
     *  2. stripped `=` padding  — stripped then re-added correctly
     *  3. non-zero trailing bits — decoded with MIME decoder (lenient); the trailing
     *     bits lie beyond the last data byte and are safely ignored
     */
    private fun loadPrivateKey(pem: String): RSAPrivateKey {
        val normalised = pem
            .replace("\\n", "\n")           // literal \n from env vars
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")       // strip all whitespace / newlines
            .trim()

        // 1. Normalise base64url alphabet → standard Base64 alphabet.
        val standardB64 = normalised.replace('-', '+').replace('_', '/')

        // 2. Strip *all* existing padding then re-add the exact right amount.
        //    Avoids double-padding when the key already has '=' chars.
        val unpadded = standardB64.trimEnd('=')
        val repadded = when (unpadded.length % 4) {
            0    -> unpadded          // exact multiple — no padding needed
            2    -> "$unpadded=="     // 2 data chars in last group
            3    -> "$unpadded="      // 3 data chars in last group
            else -> throw IllegalArgumentException(
                "JWT private key is corrupt: base64 length mod 4 == 1 after stripping padding " +
                "(a valid PKCS#8 encoding can never produce this). " +
                "Regenerate with: openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | " +
                "openssl pkcs8 -topk8 -nocrypt -outform PEM"
            )
        }

        // 3. MIME decoder is lenient about non-zero trailing bits that some RSA key
        //    generators produce.  Strict decoder (getDecoder) throws
        //    "Last unit does not have enough valid bits" in that case.
        //    The extra bits lie beyond the last data byte — masking them is safe.
        val keyBytes = Base64.getMimeDecoder().decode(repadded)
        return KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes)) as RSAPrivateKey
    }

    /** Returns the lowercase hex SHA-256 digest of [input] (UTF-8 encoded). */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

