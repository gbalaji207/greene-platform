package com.greene.training.service

import com.greene.core.exception.PlatformException
import com.greene.training.domain.BookingStatus
import org.springframework.http.HttpStatus

/**
 * Validates that a booking status transition is legal.
 *
 * Rules (per E3-US3 spec):
 *  - same → same : rejected with INVALID_STATUS_TRANSITION (422)
 *  - all other combinations : allowed
 */
object BookingStatusTransitionValidator {

    fun validate(current: BookingStatus, target: BookingStatus) {
        if (current == target) {
            throw PlatformException(
                code = "INVALID_STATUS_TRANSITION",
                message = "Booking is already $current",
                httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }
    }
}

