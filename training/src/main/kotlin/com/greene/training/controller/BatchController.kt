package com.greene.training.controller

import com.greene.core.api.response.ApiResponse
import com.greene.core.exception.PlatformException
import com.greene.training.domain.BatchStatus
import com.greene.training.dto.BatchResponse
import com.greene.training.dto.CreateBatchRequest
import com.greene.training.dto.UpdateBatchDetailsRequest
import com.greene.training.dto.UpdateBatchStatusRequest
import com.greene.training.service.BatchService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * REST controller for batch management.
 *
 * [GET /api/v1/batches] is public — access logic is handled in the service layer.
 * All other write/admin endpoints require ADMIN, STAFF, or SUPER_ADMIN via @PreAuthorize.
 *
 * Error mapping is handled centrally by [com.greene.core.web.GlobalExceptionHandler].
 */
@RestController
@RequestMapping("/api/v1/batches")
class BatchController(private val batchService: BatchService) {

    /**
     * GET /api/v1/batches
     *
     * Public endpoint — no JWT required.
     * - Unauthenticated / CLIENT callers: returns OPEN batches only, trimmed projection.
     * - ADMIN / STAFF / SUPER_ADMIN callers: returns all statuses, optional ?status= filter.
     *
     * Validates:
     *  - [page] ≥ 1, else 400 VALIDATION_ERROR
     *  - [pageSize] ≤ 50, else 400 VALIDATION_ERROR
     */
    @GetMapping
    fun listBatches(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) status: BatchStatus?,
    ): ResponseEntity<ApiResponse<*>> {
        if (page < 1) {
            throw PlatformException(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                httpStatus = HttpStatus.BAD_REQUEST,
                details = listOf(com.greene.core.api.error.ErrorDetail(field = "page", message = "page must be at least 1")),
            )
        }
        if (pageSize > 50) {
            throw PlatformException(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                httpStatus = HttpStatus.BAD_REQUEST,
                details = listOf(com.greene.core.api.error.ErrorDetail(field = "pageSize", message = "pageSize must not exceed 50")),
            )
        }

        val auth: Authentication? = SecurityContextHolder.getContext().authentication
        val isPrivileged = auth != null && auth.authorities.any { authority ->
            authority.authority in setOf("ROLE_ADMIN", "ROLE_STAFF", "ROLE_SUPER_ADMIN")
        }

        return ResponseEntity.ok(ApiResponse.of(batchService.listBatches(page, pageSize, status, isPrivileged)))
    }

    /**
     * POST /api/v1/batches
     *
     * Creates a new batch. The caller's user ID is extracted from the JWT principal
     * (set by [com.greene.core.auth.security.JwtAuthenticationFilter]) and used as
     * `createdBy` — it is never read from the request body.
     *
     * Returns 201 Created with the persisted batch wrapped in [ApiResponse].
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @PostMapping
    fun createBatch(
        @Valid @RequestBody request: CreateBatchRequest,
    ): ResponseEntity<ApiResponse<BatchResponse>> {
        val callerId = UUID.fromString(
            SecurityContextHolder.getContext().authentication.principal as String,
        )
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(batchService.createBatch(request, callerId)))
    }

    /**
     * GET /api/v1/batches/{id}
     *
     * Returns full batch detail for the given UUID.
     * Returns 404 BATCH_NOT_FOUND when no batch exists with that ID.
     * Returns 400 VALIDATION_ERROR when [id] is not a valid UUID
     * (handled by [com.greene.core.web.GlobalExceptionHandler.handleMethodArgumentTypeMismatch]).
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @GetMapping("/{id}")
    fun getBatch(
        @PathVariable id: UUID,
    ): ResponseEntity<ApiResponse<BatchResponse>> =
        ResponseEntity.ok(ApiResponse.of(batchService.getBatch(id)))

    /**
     * PATCH /api/v1/batches/{id}/details
     *
     * Partially updates editable fields of an existing batch.
     * Batch must not be CLOSED; at least one field must be provided.
     * Returns 200 with the updated batch on success.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @PatchMapping("/{id}/details")
    fun updateBatchDetails(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBatchDetailsRequest,
    ): ResponseEntity<ApiResponse<BatchResponse>> =
        ResponseEntity.ok(ApiResponse.of(batchService.updateBatchDetails(id, request)))

    /**
     * PATCH /api/v1/batches/{id}/status
     *
     * Transitions a batch to a new status (OPEN or CLOSED).
     * DRAFT is not a valid target; invalid transitions return 422.
     * On OPEN → CLOSED, all PENDING bookings are auto-rejected.
     * Returns 200 with the updated batch on success.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @PatchMapping("/{id}/status")
    fun updateBatchStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBatchStatusRequest,
    ): ResponseEntity<ApiResponse<BatchResponse>> {
        val callerId = UUID.fromString(
            SecurityContextHolder.getContext().authentication.principal as String,
        )
        return ResponseEntity.ok(ApiResponse.of(batchService.updateBatchStatus(id, request, callerId)))
    }
}
