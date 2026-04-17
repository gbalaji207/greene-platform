package com.greene.training.repository

import com.greene.training.entity.Booking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface BookingRepository : JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    fun existsByClientIdAndBatchId(clientId: UUID, batchId: UUID): Boolean

    fun findAllByClientId(clientId: UUID): List<Booking>
}

