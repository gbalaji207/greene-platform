package com.greene.core.auth.domain

import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

/**
 * Maps the `refresh_tokens` table.
 *
 * Tokens are stored as SHA-256 hashes — the raw token is never persisted.
 * Rotation: on each use, `revokedAt` is set on the old row and a new row is inserted.
 * Bulk revocation (logout / password reset) sets `revokedAt` on all active rows for a user.
 *
 * `ipAddress` maps the PostgreSQL `INET` column; stored as a plain String at the
 * application layer. The `columnDefinition` is required so Hibernate validates against
 * the correct DB type rather than expecting `varchar`.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    var id: UUID? = null,

    /** FK → users.id (ON DELETE CASCADE enforced by DB). */
    @Column(nullable = false, updatable = false)
    var userId: UUID,

    /** SHA-256 hex digest of the raw opaque token. */
    @Column(nullable = false, unique = true, length = 255)
    var tokenHash: String,

    @Column(nullable = false)
    var expiresAt: Instant,

    /** Set on logout, password reset, or token rotation. Null means still valid. */
    @Column
    var revokedAt: Instant? = null,

    @Column(length = 500)
    var userAgent: String? = null,

    /** PostgreSQL INET type — stored as a dotted-decimal or CIDR string. */
    @Type(InetUserType::class)
    @Column(columnDefinition = "inet")
    var ipAddress: String? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column
    var lastUsedAt: Instant? = null,
)

