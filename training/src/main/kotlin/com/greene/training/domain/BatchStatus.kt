package com.greene.training.domain

/**
 * Lifecycle status of a [com.greene.training.entity.Batch].
 *
 * Valid creation values: DRAFT, OPEN.
 * CLOSED may only be reached via status transition (E3-US4).
 * Training completion is tracked via `training_complete` / `training_completed_at` on bookings.
 */
enum class BatchStatus {
    /** Created but not visible to clients. Editable. */
    DRAFT,

    /** Visible to clients. Bookings accepted. Editable. */
    OPEN,

    /** No longer visible to clients. No new bookings. Read-only. */
    CLOSED,
}

