package com.greene.training.repository

import com.greene.training.domain.BatchStatus
import com.greene.training.entity.Batch
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BatchRepository : JpaRepository<Batch, UUID> {

    /** Used by public/CLIENT callers — always OPEN only. */
    fun findAllByStatus(status: BatchStatus, pageable: Pageable): Page<Batch>

    /** Used by staff/admin callers when a specific status filter is supplied. */
    fun findAllByStatusIn(statuses: Collection<BatchStatus>, pageable: Pageable): Page<Batch>
}

