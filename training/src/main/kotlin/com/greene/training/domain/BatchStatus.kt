package com.greene.training.domain

/**
 * Lifecycle status of a [com.greene.training.entity.Batch].
 *
 * Valid creation values: DRAFT, OPEN.
 * CLOSED and COMPLETED may only be reached via status transitions (E3-US4, E3-US5).
 */
enum class BatchStatus {
    /** Created but not visible to clients. Editable. */
    DRAFT,

    /** Visible to clients. Bookings accepted. Editable. */
    OPEN,

    /** No longer visible to clients. No new bookings. Read-only. */
    CLOSED,

    /** Training concluded. Premium content unlocked for enrolled clients. Read-only. */
    COMPLETED,
}

