package com.greene.core.staff.dto

import java.time.Instant
import java.util.UUID

/** Returned by POST /api/v1/staff on successful staff account creation. */
data class StaffUserResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val phone: String,
    /** String form of [com.greene.core.auth.domain.UserRole] — always "STAFF" for this endpoint. */
    val role: String,
    /** String form of [com.greene.core.auth.domain.UserStatus] — always "ACTIVE" on creation. */
    val status: String,
    val createdAt: Instant,
)

