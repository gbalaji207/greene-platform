package com.greene.training.service

import com.greene.core.exception.PlatformException
import com.greene.training.domain.BookingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus

class BookingStatusTransitionValidatorTest {

    // ── Allowed transitions ────────────────────────────────────────────────────

    @Test
    fun `PENDING to CONFIRMED - allowed - no exception thrown`() {
        assertDoesNotThrow {
            BookingStatusTransitionValidator.validate(BookingStatus.PENDING, BookingStatus.CONFIRMED)
        }
    }

    @Test
    fun `PENDING to REJECTED - allowed - no exception thrown`() {
        assertDoesNotThrow {
            BookingStatusTransitionValidator.validate(BookingStatus.PENDING, BookingStatus.REJECTED)
        }
    }

    @Test
    fun `CONFIRMED to REJECTED - allowed - no exception thrown`() {
        assertDoesNotThrow {
            BookingStatusTransitionValidator.validate(BookingStatus.CONFIRMED, BookingStatus.REJECTED)
        }
    }

    @Test
    fun `REJECTED to CONFIRMED - allowed - no exception thrown`() {
        assertDoesNotThrow {
            BookingStatusTransitionValidator.validate(BookingStatus.REJECTED, BookingStatus.CONFIRMED)
        }
    }

    // ── Forbidden transitions (same → same) ───────────────────────────────────

    @Test
    fun `CONFIRMED to CONFIRMED - throws INVALID_STATUS_TRANSITION 422`() {
        val ex = assertThrows<PlatformException> {
            BookingStatusTransitionValidator.validate(BookingStatus.CONFIRMED, BookingStatus.CONFIRMED)
        }

        assertEquals("INVALID_STATUS_TRANSITION",     ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Booking is already CONFIRMED",  ex.message)
    }

    @Test
    fun `REJECTED to REJECTED - throws INVALID_STATUS_TRANSITION 422`() {
        val ex = assertThrows<PlatformException> {
            BookingStatusTransitionValidator.validate(BookingStatus.REJECTED, BookingStatus.REJECTED)
        }

        assertEquals("INVALID_STATUS_TRANSITION",     ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Booking is already REJECTED",   ex.message)
    }
}

