package com.greene.content.repository

import com.greene.content.domain.ContentFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ContentFileRepository : JpaRepository<ContentFile, UUID> {
    fun existsByNodeIdAndFileRole(nodeId: UUID, fileRole: String): Boolean
    fun findAllByNodeIdIn(nodeIds: List<UUID>): List<ContentFile>
    fun deleteAllByNodeIdIn(nodeIds: List<UUID>)

    @Query("SELECT cf.nodeId FROM ContentFile cf WHERE cf.fileRole = 'PRIMARY' AND cf.nodeId IN :nodeIds")
    fun findNodeIdsWithPrimaryFileIn(@Param("nodeIds") nodeIds: List<UUID>): List<UUID>
}
