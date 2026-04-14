package com.greene.core.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Binds all `jwt.*` keys from application.yml.
 *
 * jwt:
 *   private-key: |
 *     -----BEGIN PRIVATE KEY-----
 *     ...base64...
 *     -----END PRIVATE KEY-----
 *   access-token-expiry-minutes: 15
 *
 * The private key must be a PKCS#8 PEM string (BEGIN PRIVATE KEY header).
 * In deployed environments, supply it via the JWT_PRIVATE_KEY environment variable.
 * Newlines can be represented as literal \n — the loader normalises both forms.
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(

    /**
     * RSA-2048 (or larger) private key in PKCS#8 PEM format.
     * Required — no default. Application will fail to start if absent.
     */
    val privateKey: String,

    /** Access token lifetime. Encoded in the `exp` claim and returned as `expiresIn`. */
    val accessTokenExpiryMinutes: Int = 15,
)

