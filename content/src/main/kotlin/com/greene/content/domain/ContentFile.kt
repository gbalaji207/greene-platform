package com.greene.content.domain

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "content_files")
class ContentFile(

    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "node_id", nullable = false)
    val nodeId: UUID,

    @Column(name = "file_key", nullable = false, length = 500)
    var fileKey: String,

    @Column(name = "mime_type", nullable = false, length = 100)
    var mimeType: String,

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "file_role", nullable = false, length = 20)
    val fileRole: FileRole,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

