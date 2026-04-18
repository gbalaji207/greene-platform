package com.greene.content.repository

import com.greene.content.domain.ContentItemDetail
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentItemDetailRepository : JpaRepository<ContentItemDetail, UUID> {
    fun findByNodeId(nodeId: UUID): ContentItemDetail?
    fun findAllByNodeIdIn(nodeIds: List<UUID>): List<ContentItemDetail>
    fun deleteAllByNodeIdIn(nodeIds: List<UUID>)
}

