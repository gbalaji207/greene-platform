package com.greene.core.staff.web

import com.greene.core.api.response.ApiResponse
import com.greene.core.staff.dto.StaffUserResponse
import com.greene.core.staff.dto.UpdatedStatusResponse
import com.greene.core.staff.service.StaffService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Staff management endpoints — admin-only operations.
 *
 * Error codes thrown from [StaffService] are mapped to the correct HTTP status
 * by [com.greene.core.web.GlobalExceptionHandler] via
 * [com.greene.core.exception.PlatformException.httpStatus].
 */
// TODO: @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RestController
@RequestMapping("/api/v1/staff")
class StaffController(private val staffService: StaffService) {

    /**
     * POST /api/v1/staff
     *
     * Creates a new staff account with role STAFF and status ACTIVE.
     * A welcome email with login instructions is sent immediately.
     * Returns 201 Created with the full staff account details.
     */
    @PostMapping
    fun createStaff(
        @Valid @RequestBody request: CreateStaffRequest,
    ): ResponseEntity<ApiResponse<StaffUserResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(staffService.create(request)))

    /**
     * PATCH /api/v1/staff/{id}/status
     *
     * Suspends or reactivates a staff account.
     * On suspension, all active refresh tokens for the user are immediately revoked.
     * Returns 200 OK with the updated id, status, and updatedAt timestamp.
     */
    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateStaffStatusRequest,
    ): ResponseEntity<ApiResponse<UpdatedStatusResponse>> =
        ResponseEntity.ok(ApiResponse.of(staffService.updateStatus(id, request.status!!)))
}

