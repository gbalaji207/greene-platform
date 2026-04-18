package com.greene.content.repository

import com.greene.content.domain.ContentLibrary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface ContentLibraryRepository : JpaRepository<ContentLibrary, UUID>,
    JpaSpecificationExecutor<ContentLibrary>

