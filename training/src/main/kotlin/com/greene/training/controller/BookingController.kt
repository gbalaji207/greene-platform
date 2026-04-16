package com.greene.training.controller

import com.greene.core.api.response.ApiResponse
import com.greene.training.dto.BookingResponse
import com.greene.training.service.BookingService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for booking operations.
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

