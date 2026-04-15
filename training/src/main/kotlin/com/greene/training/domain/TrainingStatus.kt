package com.greene.training.domain

/**
 * Indicates whether the training session for a batch has concluded.
 *
 * Stored in the `training_status` column; NULL means training has not yet started.
 * Set to COMPLETED by STAFF in E3-US5.
 */
enum class TrainingStatus {
    /** Staff has marked training as complete. */
    COMPLETED,
}

