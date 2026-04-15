package com.greene.training.controller

import com.greene.core.api.response.ApiResponse
import com.greene.training.dto.BatchResponse
import com.greene.training.dto.CreateBatchRequest
import com.greene.training.service.BatchService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for batch management.
 *
 * All endpoints require ADMIN, STAFF, or SUPER_ADMIN role.
 * CLIENT callers are rejected with 403 FORBIDDEN via @PreAuthorize.
 * Unauthenticated requests receive 401 UNAUTHORIZED from [com.greene.core.auth.security.JwtAuthenticationEntryPoint].
 *
 * Error mapping is handled centrally by [com.greene.core.web.GlobalExceptionHandler].
 */
@RestController
@RequestMapping("/api/v1/batches")
class BatchController(private val batchService: BatchService) {

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
}

