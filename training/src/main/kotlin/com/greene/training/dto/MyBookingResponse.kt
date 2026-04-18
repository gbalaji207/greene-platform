package com.greene.training.dto

import com.greene.training.domain.BookingStatus
import com.greene.training.domain.TrainingStatus
import java.time.OffsetDateTime
import java.util.UUID

data class MyBookingResponse(
    val id: UUID,
    val batchId: UUID,
    val batchName: String,
    val startDateTime: OffsetDateTime,
    val endDateTime: OffsetDateTime?,
    val location: String?,
    val topics: String?,
    val maxSeats: Int?,
    val bookingStatus: BookingStatus,
    val trainingStatus: TrainingStatus,
    val note: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

