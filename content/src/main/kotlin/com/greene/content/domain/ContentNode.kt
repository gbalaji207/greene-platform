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

    @Column(name = "parent_id")
    var parentId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 10)
    val nodeType: NodeType,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(nullable = false)
    var depth: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status: NodeStatus,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

