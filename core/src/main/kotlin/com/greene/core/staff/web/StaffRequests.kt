package com.greene.core.staff.web

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateStaffRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(
        min = 2, max = 100,
        message = "Name must be between 2 and 100 characters",
    )
    val name: String? = null,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String? = null,

    /**
     * E.164 format: leading +, country code, subscriber number.
     * Examples: +919876543210, +12125551234
     */
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(
        regexp = "^\\+[1-9]\\d{7,14}$",
        message = "Phone must be in E.164 format (e.g. +919876543210)",
    )
    val phone: String? = null,
)

data class UpdateStaffStatusRequest(
    @field:NotNull(message = "Status is required")
    @field:Pattern(
        regexp = "^(ACTIVE|SUSPENDED)$",
        message = "Status must be one of: ACTIVE, SUSPENDED",
    )
    val status: String? = null,
)

