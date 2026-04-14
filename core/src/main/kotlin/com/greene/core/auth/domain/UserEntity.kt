package com.greene.core.auth.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Maps the `users` table.
 *
 * All required business fields are constructor parameters.
 * The `kotlin-plugin-jpa` compiler plugin generates a synthetic no-arg constructor
 * so that Hibernate can instantiate entities via reflection — application code
 * always uses the explicit constructor below.
 *
 * `id` is null before first persist; Hibernate assigns a UUID before the INSERT.
 * `createdAt` / `updatedAt` are set by the application; the DB trigger
 * `trg_users_updated_at` additionally keeps `updated_at` accurate at the DB level.
 */
@Entity
@Table(name = "users")
class UserEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(nullable = false, unique = true, length = 255)
    var email: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, unique = true, length = 20)
    var phone: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.CLIENT,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: UserStatus = UserStatus.PENDING_VERIFICATION,

    /** Always FALSE in Phase 1 — SMS OTP verification is deferred. */
    @Column(nullable = false)
    var phoneVerified: Boolean = false,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column
    var lastLoginAt: Instant? = null,
) {
    /** Mirrors the DB trigger so the in-memory object stays consistent. */
    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}

