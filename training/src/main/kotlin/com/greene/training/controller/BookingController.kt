package com.greene.training.controller

import com.greene.core.api.response.ApiResponse
import com.greene.core.api.response.PagedData
import com.greene.training.domain.BookingStatus
import com.greene.training.dto.BookingDetailResponse
import com.greene.training.dto.BookingListItemResponse
import com.greene.training.dto.BookingResponse
import com.greene.training.dto.MyBookingResponse
import com.greene.training.dto.UpdateBookingStatusRequest
import com.greene.training.service.BookingService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for CLIENT-facing booking operations.
 *
 * POST /api/v1/batches/{id}/bookings — CLIENT role only.
 * The caller's userId is extracted from the JWT principal (set by JwtAuthenticationFilter).
 *
 * Error mapping is handled centrally by [com.greene.core.web.GlobalExceptionHandler].
 */
@RestController
@RequestMapping("/api/v1/batches")
class BookingController(private val bookingService: BookingService) {

    /**
     * POST /api/v1/batches/{id}/bookings
     *
     * Creates a PENDING booking for the authenticated CLIENT against the given batch.
     * No request body needed — identity comes from the JWT, batch from the path.
     *
     * Returns 201 Created with the persisted [BookingResponse] wrapped in [ApiResponse].
     */
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/{id}/bookings")
    fun createBooking(
        @PathVariable id: UUID,
    ): ResponseEntity<ApiResponse<BookingResponse>> {
        val clientId = UUID.fromString(
            SecurityContextHolder.getContext().authentication.principal as String,
        )
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(bookingService.createBooking(batchId = id, clientId = clientId)))
    }
}

/**
 * REST controller for staff/admin booking management.
 *
 * GET  /api/v1/bookings/me      — CLIENT role; returns all bookings for the authenticated client.
 * GET  /api/v1/bookings        — paginated list with optional filters.
 * PATCH /api/v1/bookings/{id}  — confirm or reject a booking.
 *
 * Staff/admin endpoints require ADMIN, STAFF, or SUPER_ADMIN role.
 * Error mapping is handled centrally by [com.greene.core.web.GlobalExceptionHandler].
 */
@RestController
@RequestMapping("/api/v1/bookings")
class StaffBookingController(private val bookingService: BookingService) {

    /**
     * GET /api/v1/bookings/me
     *
     * Returns all bookings for the authenticated CLIENT, each enriched with batch details.
     * Sorted by createdAt DESC. Returns 200 with an empty list when the client has no bookings.
     */
    @PreAuthorize("hasRole('CLIENT')")
    @GetMapping("/me")
    fun getMyBookings(authentication: Authentication): ResponseEntity<ApiResponse<List<MyBookingResponse>>> {
        val clientId = UUID.fromString(authentication.principal as String)
        val result = bookingService.getMyBookings(clientId)
        return ResponseEntity.ok(ApiResponse.of(result))
    }

    /**
     * GET /api/v1/bookings
     *
     * Returns a paginated list of bookings, optionally filtered by [status] and/or [batchId].
     * Invalid enum values for [status] or non-UUID [batchId] are caught by
     * GlobalExceptionHandler as VALIDATION_ERROR (400).
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @GetMapping
    fun getBookings(
        @RequestParam(required = false) status: BookingStatus?,
        @RequestParam(required = false) batchId: UUID?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
    ): ResponseEntity<ApiResponse<PagedData<BookingListItemResponse>>> {
        val result = bookingService.getBookings(
            status = status,
            batchId = batchId,
            page = page,
            pageSize = pageSize,
        )
        return ResponseEntity.ok(ApiResponse.of(result))
    }

    /**
     * PATCH /api/v1/bookings/{id}
     *
     * Confirms or rejects the booking identified by [id].
     * The request status must be CONFIRMED or REJECTED — PENDING is rejected with VALIDATION_ERROR.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @PatchMapping("/{id}")
    fun updateBookingStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBookingStatusRequest,
    ): ResponseEntity<ApiResponse<BookingDetailResponse>> {
        val result = bookingService.updateBookingStatus(bookingId = id, request = request)
        return ResponseEntity.ok(ApiResponse.of(result))
    }
}


