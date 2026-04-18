package com.greene.training.repository

import com.greene.training.domain.BookingStatus
import com.greene.training.entity.Booking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface BookingRepository : JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    fun existsByClientIdAndBatchId(clientId: UUID, batchId: UUID): Boolean

    fun findAllByClientId(clientId: UUID): List<Booking>

    fun findAllByClientIdOrderByCreatedAtDesc(clientId: UUID): List<Booking>

    /** Used by [com.greene.training.service.BatchService.updateBatchStatus] to auto-reject on close. */
    fun findAllByBatchIdAndStatus(batchId: UUID, status: BookingStatus): List<Booking>
}

