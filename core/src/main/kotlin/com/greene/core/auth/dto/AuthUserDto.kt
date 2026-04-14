package com.greene.core.auth.dto

import java.util.UUID

/** User snapshot embedded in the verify-otp success response. */
data class AuthUserDto(
    val id: UUID,
    val name: String,
    val email: String,
    /** String form of [com.greene.core.auth.domain.UserRole] — e.g. "CLIENT". */
    val role: String,
)

