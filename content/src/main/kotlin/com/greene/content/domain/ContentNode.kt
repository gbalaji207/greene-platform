package com.greene.content.domain

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "content_nodes")
class ContentNode(

    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "library_id", nullable = false)
    val libraryId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    val nodeType: NodeType,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(nullable = false)
    val depth: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: NodeStatus = NodeStatus.DRAFT,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

