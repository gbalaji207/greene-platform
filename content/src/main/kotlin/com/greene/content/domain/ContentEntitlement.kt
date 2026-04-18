package com.greene.content.domain

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "content_entitlements")
class ContentEntitlement(

    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "library_id", nullable = false)
    val libraryId: UUID,

    @Column(name = "granted_by", nullable = false)
    val grantedBy: UUID,

    @Column(name = "granted_at", nullable = false, updatable = false)
    val grantedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "revoked_at")
    var revokedAt: OffsetDateTime? = null
)
