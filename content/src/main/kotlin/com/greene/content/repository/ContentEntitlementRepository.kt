package com.greene.content.repository

import com.greene.content.domain.ContentEntitlement
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentEntitlementRepository : JpaRepository<ContentEntitlement, UUID> {
    fun existsByUserIdAndLibraryIdAndRevokedAtIsNull(userId: UUID, libraryId: UUID): Boolean
}

