package com.greene.content.domain

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "content_item_details")
class ContentItemDetail(

    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "node_id", nullable = false, unique = true)
    val nodeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    val itemType: ItemType,

    @Column(length = 500)
    var summary: String? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

