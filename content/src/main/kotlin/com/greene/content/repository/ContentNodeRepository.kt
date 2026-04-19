package com.greene.content.repository

import com.greene.content.domain.ContentNode
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentNodeRepository : JpaRepository<ContentNode, UUID>

