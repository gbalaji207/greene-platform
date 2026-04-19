package com.greene.content.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "content_item_details")
class ContentItemDetails(

    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "node_id", nullable = false, unique = true)
    val nodeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    val itemType: ItemType,

    @Column(columnDefinition = "TEXT")
    var summary: String? = null
)

