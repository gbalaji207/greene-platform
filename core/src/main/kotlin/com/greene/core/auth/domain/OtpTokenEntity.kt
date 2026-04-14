package com.greene.core.auth.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Maps the `otp_tokens` table.
 *
 * `userId` is stored as a plain UUID column (no JPA association object) to keep
 * queries simple and avoid lazy-load complications. The ON DELETE CASCADE FK
 * constraint is enforced at the DB level by V2__create_auth_tables.sql.
 *
 * "Active" OTP = usedAt IS NULL AND invalidatedAt IS NULL.
 * Only one active OTP per (userId, purpose) pair is maintained by the service layer.
 */
@Entity
@Table(name = "otp_tokens")
class OtpTokenEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    var id: UUID? = null,

    /** FK → users.id (ON DELETE CASCADE enforced by DB). */
    @Column(nullable = false, updatable = false)
    var userId: UUID,

    /** BCrypt hash of the 6-digit OTP — plaintext is never persisted. */
    @Column(nullable = false, length = 255)
    var otpHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var purpose: OtpPurpose = OtpPurpose.EMAIL_VERIFICATION,

    @Column(nullable = false)
    var expiresAt: Instant,

    /** Set when the OTP is consumed successfully (single-use). */
    @Column
    var usedAt: Instant? = null,

    /** Set when superseded by a newly generated OTP for the same user+purpose. */
    @Column
    var invalidatedAt: Instant? = null,

    @Column(nullable = false)
    var resendCount: Short = 0,

    @Column
    var lastResentAt: Instant? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)

