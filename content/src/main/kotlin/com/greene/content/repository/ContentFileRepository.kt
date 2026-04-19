package com.greene.content.repository

import com.greene.content.domain.ContentFile
import com.greene.content.domain.FileRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentFileRepository : JpaRepository<ContentFile, UUID> {

    fun findByNodeIdAndFileRole(nodeId: UUID, fileRole: FileRole): ContentFile?

    fun existsByNodeIdAndFileRole(nodeId: UUID, fileRole: FileRole): Boolean

    fun findAllByNodeIdIn(nodeIds: Collection<UUID>): List<ContentFile>
}

