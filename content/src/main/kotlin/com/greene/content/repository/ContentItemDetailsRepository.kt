package com.greene.content.repository

import com.greene.content.domain.ContentItemDetails
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentItemDetailsRepository : JpaRepository<ContentItemDetails, UUID> {

    fun findByNodeId(nodeId: UUID): ContentItemDetails?
}

