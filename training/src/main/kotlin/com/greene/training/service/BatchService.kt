package com.greene.training.service

import com.greene.core.api.error.ErrorDetail
import com.greene.core.exception.PlatformException
import com.greene.training.domain.BatchStatus
import com.greene.training.dto.BatchResponse
import com.greene.training.dto.CreateBatchRequest
import com.greene.training.entity.Batch
import com.greene.training.repository.BatchRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Domain service for batch management.
 *
 * Business rules enforced here:
 *  - Only DRAFT and OPEN are valid creation statuses (SC-08).
 *  - endDate must be on or after startDate when both are provided (SC-04).
 *  - createdBy is always sourced from the JWT principal — never from the request body.
 */
@Service
@Transactional
class BatchService(
    private val batchRepository: BatchRepository,
) {

    /**
     * Creates a new batch from [request] and associates it with [callerId].
     *
     * Validation order:
     *  1. Default status to DRAFT if omitted.
     *  2. Reject CLOSED / COMPLETED on creation → INVALID_BATCH_STATUS (SC-08).
     *  3. Reject endDate < startDate → VALIDATION_ERROR (SC-04).
     *  4. Persist and return [BatchResponse].
     */
    fun createBatch(request: CreateBatchRequest, callerId: UUID): BatchResponse {

        // Step 1 — status defaults to DRAFT when omitted (SC-12)
        val status = request.status ?: BatchStatus.DRAFT

        // Step 2 — only DRAFT and OPEN are allowed on creation (SC-08)
        if (status == BatchStatus.CLOSED || status == BatchStatus.COMPLETED) {
            throw PlatformException(
                code = "INVALID_BATCH_STATUS",
                message = "Batch can only be created with status DRAFT or OPEN",
                httpStatus = HttpStatus.BAD_REQUEST,
            )
        }

        // Step 3 — cross-field date check (SC-04); same date is valid (SC-05)
        if (request.endDate != null && request.startDate != null &&
            request.endDate.isBefore(request.startDate)
        ) {
            throw PlatformException(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                httpStatus = HttpStatus.BAD_REQUEST,
                details = listOf(
                    ErrorDetail(field = "endDate", message = "end date must be on or after start date")
                ),
            )
        }

        // Step 4 — persist; !! is safe here because @NotNull / @NotBlank validated upstream
        val batch = Batch(
            name = request.name!!,
            description = request.description,
            startDate = request.startDate!!,
            endDate = request.endDate,
            location = request.location,
            topics = request.topics,
            maxSeats = request.maxSeats,
            status = status,
            trainingStatus = null,
            createdBy = callerId,
        )

        return batchRepository.save(batch).toResponse()
    }

    /**
     * Returns the batch with [id], or throws BATCH_NOT_FOUND (SC-13).
     */
    @Transactional(readOnly = true)
    fun getBatch(id: UUID): BatchResponse =
        batchRepository.findById(id)
            .orElseThrow {
                PlatformException(
                    code = "BATCH_NOT_FOUND",
                    message = "Batch not found",
                    httpStatus = HttpStatus.NOT_FOUND,
                )
            }
            .toResponse()
}

// ── Mapping ───────────────────────────────────────────────────────────────────

private fun Batch.toResponse() = BatchResponse(
    id = id!!,
    name = name,
    description = description,
    startDate = startDate,
    endDate = endDate,
    location = location,
    topics = topics,
    maxSeats = maxSeats,
    status = status,
    trainingStatus = trainingStatus,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

