package com.greene.training.service

import com.greene.core.api.error.ErrorDetail
import com.greene.core.api.response.PagedData
import com.greene.core.exception.PlatformException
import com.greene.training.domain.BatchStatus
import com.greene.training.domain.BookingStatus
import com.greene.training.dto.BatchListItemResponse
import com.greene.training.dto.BatchResponse
import com.greene.training.dto.CreateBatchRequest
import com.greene.training.dto.UpdateBatchDetailsRequest
import com.greene.training.dto.UpdateBatchStatusRequest
import com.greene.training.entity.Batch
import com.greene.training.entity.BatchStatusLog
import com.greene.training.repository.BatchRepository
import com.greene.training.repository.BatchStatusLogRepository
import com.greene.training.repository.BookingRepository
import org.springframework.data.domain.PageRequest
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
    private val bookingRepository: BookingRepository,
    private val batchStatusLogRepository: BatchStatusLogRepository,
) {

    /**
     * Creates a new batch from [request] and associates it with [callerId].
     *
     * Validation order:
     *  1. Default status to DRAFT if omitted.
     *  2. Reject CLOSED on creation — INVALID_BATCH_STATUS (SC-08).
     *  3. Reject endDate < startDate — VALIDATION_ERROR (SC-04).
     *  4. Persist and return [BatchResponse].
     */
    fun createBatch(request: CreateBatchRequest, callerId: UUID): BatchResponse {

        // Step 1 — status defaults to DRAFT when omitted (SC-12)
        val status = request.status ?: BatchStatus.DRAFT

        // Step 2 — only DRAFT and OPEN are allowed on creation (SC-08)
        if (status == BatchStatus.CLOSED) {
            throw PlatformException(
                code = "INVALID_BATCH_STATUS",
                message = "Batch can only be created with status DRAFT or OPEN",
                httpStatus = HttpStatus.BAD_REQUEST,
            )
        }

        // Step 3 — cross-field date-time check; endDateTime must be after startDateTime
        if (request.endDateTime != null && request.startDateTime != null &&
            !request.endDateTime.isAfter(request.startDateTime)
        ) {
            throw PlatformException(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                httpStatus = HttpStatus.BAD_REQUEST,
                details = listOf(
                    ErrorDetail(field = "endDateTime", message = "end date time must be after start date time")
                ),
            )
        }

        // Step 4 — persist; !! is safe here because @NotNull / @NotBlank validated upstream
        val batch = Batch(
            name = request.name!!,
            description = request.description,
            startDateTime = request.startDateTime!!,
            endDateTime = request.endDateTime,
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

    /**
     * Returns a paginated list of batches.
     *
     * - Public / CLIENT callers always see only OPEN batches (trimmed projection).
     * - Staff / Admin callers see all statuses; an optional [statusFilter] narrows results.
     *
     * [page] is 1-based; validated upstream. [pageSize] max is 50; validated upstream.
     * [isPrivileged] is true when the caller holds ADMIN, STAFF, or SUPER_ADMIN.
     */
    @Transactional(readOnly = true)
    fun listBatches(
        page: Int,
        pageSize: Int,
        statusFilter: BatchStatus?,
        isPrivileged: Boolean,
    ): PagedData<*> {
        val pageable = PageRequest.of(page - 1, pageSize)

        return if (!isPrivileged) {
            // Public / CLIENT — always OPEN only, trimmed projection
            val result = batchRepository.findAllByStatus(BatchStatus.OPEN, pageable)
            PagedData(
                items = result.content.map { it.toListItemResponse() },
                page = page,
                pageSize = pageSize,
                total = result.totalElements,
            )
        } else {
            // Admin / Staff — all statuses, optional filter, full projection
            val result = if (statusFilter != null) {
                batchRepository.findAllByStatusIn(listOf(statusFilter), pageable)
            } else {
                batchRepository.findAll(pageable)
            }
            PagedData(
                items = result.content.map { it.toResponse() },
                page = page,
                pageSize = pageSize,
                total = result.totalElements,
            )
        }
    }

    /**
     * Applies a partial update to an existing batch.
     *
     * Validation order:
     *  1. Batch must exist → BATCH_NOT_FOUND (404).
     *  2. Batch must not be CLOSED → BATCH_NOT_EDITABLE (422).
     *  3. At least one field must be non-null → AT_LEAST_ONE_FIELD_REQUIRED (400).
     *  4. maxSeats, if provided, must be >= 1 → VALIDATION_ERROR (400).
     *  5. Date cross-field check:
     *       - Both provided → endDateTime must be after startDateTime.
     *       - Only endDateTime provided → must be after existing batch.startDateTime.
     *       → VALIDATION_ERROR (400).
     *  6. Patch: apply only non-null request fields to the entity.
     *  7. Save and return [BatchResponse].
     */
    fun updateBatchDetails(batchId: UUID, request: UpdateBatchDetailsRequest): BatchResponse {

        // Step 1 — batch must exist
        val batch = batchRepository.findById(batchId).orElseThrow {
            PlatformException(
                code = "BATCH_NOT_FOUND",
                message = "Batch not found",
                httpStatus = HttpStatus.NOT_FOUND,
            )
        }

        // Step 2 — closed batches are read-only
        if (batch.status == BatchStatus.CLOSED) {
            throw PlatformException(
                code = "BATCH_NOT_EDITABLE",
                message = "Batch is closed and cannot be edited",
                httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }

        // Step 3 — at least one field must be present
        if (request.name == null &&
            request.description == null &&
            request.location == null &&
            request.topics == null &&
            request.maxSeats == null &&
            request.startDateTime == null &&
            request.endDateTime == null
        ) {
            throw PlatformException(
                code = "AT_LEAST_ONE_FIELD_REQUIRED",
                message = "At least one field must be provided",
                httpStatus = HttpStatus.BAD_REQUEST,
            )
        }

        // Step 4 — maxSeats must be >= 1 when provided (belt-and-suspenders; @Min(1) covers bean validation)
        if (request.maxSeats != null && request.maxSeats < 1) {
            throw PlatformException(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                httpStatus = HttpStatus.BAD_REQUEST,
                details = listOf(ErrorDetail(field = "maxSeats", message = "must be greater than or equal to 1")),
            )
        }

        // Step 5 — date cross-field validation
        val effectiveStart = request.startDateTime ?: batch.startDateTime
        val effectiveEnd   = request.endDateTime   ?: batch.endDateTime

        if (request.endDateTime != null && effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) {
            throw PlatformException(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                httpStatus = HttpStatus.BAD_REQUEST,
                details = listOf(ErrorDetail(field = "endDateTime", message = "end date time must be after start date time")),
            )
        }

        // Step 6 — patch: apply only non-null fields
        if (request.name          != null) batch.name          = request.name
        if (request.description   != null) batch.description   = request.description
        if (request.location      != null) batch.location      = request.location
        if (request.topics        != null) batch.topics        = request.topics
        if (request.maxSeats      != null) batch.maxSeats      = request.maxSeats
        if (request.startDateTime != null) batch.startDateTime = request.startDateTime
        if (request.endDateTime   != null) batch.endDateTime   = request.endDateTime

        // Step 7 — persist and return
        return batchRepository.save(batch).toResponse()
    }

    /**
     * Transitions a batch to a new status.
     *
     * Validation order:
     *  1. Batch must exist → BATCH_NOT_FOUND (404).
     *  2. DRAFT must not be the requested target → VALIDATION_ERROR (400).
     *  3. Transition must be in the allowed set → INVALID_BATCH_STATUS_TRANSITION (422).
     *  4. On OPEN → CLOSED: auto-reject all PENDING bookings.
     *  5. Update batch status and save.
     *  6. Write audit log to batch_status_logs.
     *  7. Return [BatchResponse].
     *
     * The entire operation (batch save, booking updates, log write) is atomic
     * — this method is covered by the class-level [@Transactional].
     */
    fun updateBatchStatus(
        batchId: UUID,
        request: UpdateBatchStatusRequest,
        callerId: UUID,
    ): BatchResponse {

        // Step 1 — batch must exist
        val batch = batchRepository.findById(batchId).orElseThrow {
            PlatformException(
                code = "BATCH_NOT_FOUND",
                message = "Batch not found",
                httpStatus = HttpStatus.NOT_FOUND,
            )
        }

        val toStatus = request.status!! // @NotNull guarantees non-null by the time we reach here

        // Step 2 — DRAFT is not a valid transition target
        if (toStatus == BatchStatus.DRAFT) {
            throw PlatformException(
                code = "VALIDATION_ERROR",
                message = "DRAFT is not a valid target status",
                httpStatus = HttpStatus.BAD_REQUEST,
            )
        }

        // Step 3 — validate allowed transitions
        val fromStatus = batch.status
        val allowed = mapOf(
            BatchStatus.DRAFT  to BatchStatus.OPEN,
            BatchStatus.OPEN   to BatchStatus.CLOSED,
            BatchStatus.CLOSED to BatchStatus.OPEN,
        )
        if (allowed[fromStatus] != toStatus) {
            throw PlatformException(
                code = "INVALID_BATCH_STATUS_TRANSITION",
                message = "Cannot transition batch from $fromStatus to $toStatus",
                httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }

        // Step 4 — auto-reject PENDING bookings on OPEN → CLOSED
        if (fromStatus == BatchStatus.OPEN && toStatus == BatchStatus.CLOSED) {
            val pendingBookings = bookingRepository.findAllByBatchIdAndStatus(batchId, BookingStatus.PENDING)
            pendingBookings.forEach { booking ->
                booking.status = BookingStatus.REJECTED
                booking.note   = "Auto Rejected as batch closed."
            }
            bookingRepository.saveAll(pendingBookings)
        }

        // Step 5 — update batch status
        batch.status = toStatus

        // Step 6 — persist batch
        val saved = batchRepository.save(batch)

        // Step 7 — audit log
        batchStatusLogRepository.save(
            BatchStatusLog(
                batchId     = batchId,
                fromStatus  = fromStatus,
                toStatus    = toStatus,
                changedBy   = callerId,
            )
        )

        return saved.toResponse()
    }
}

// ── Mapping ───────────────────────────────────────────────────────────────────

private fun Batch.toResponse() = BatchResponse(
    id = id!!,
    name = name,
    description = description,
    startDateTime = startDateTime,
    endDateTime = endDateTime,
    location = location,
    topics = topics,
    maxSeats = maxSeats,
    status = status,
    trainingStatus = trainingStatus,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Batch.toListItemResponse() = BatchListItemResponse(
    id = id!!,
    name = name,
    description = description,
    startDateTime = startDateTime,
    endDateTime = endDateTime,
    location = location,
    topics = topics,
    maxSeats = maxSeats,
)
