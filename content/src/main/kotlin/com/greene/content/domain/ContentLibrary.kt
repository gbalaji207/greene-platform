package com.greene.content.domain

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "content_libraries")
class ContentLibrary(

    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: LibraryStatus = LibraryStatus.DRAFT,

    @Column(name = "created_by", nullable = false)
    val createdBy: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

