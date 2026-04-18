package com.greene.training.repository

import com.greene.training.entity.BatchStatusLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Repository for [BatchStatusLog] audit records.
 */
interface BatchStatusLogRepository : JpaRepository<BatchStatusLog, UUID> {

    /**
     * Returns all status-transition records for the given batch,
     * most recent first.
     */
    fun findAllByBatchIdOrderByChangedAtDesc(batchId: UUID): List<BatchStatusLog>
}

