package com.greene.core.auth.web

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class IdentifyRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String? = null,
)

data class RegisterRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String? = null,

    @field:NotBlank(message = "Name is required")
    @field:Size(
        min = 2, max = 100,
        message = "Name must be between 2 and 100 characters",
    )
    val name: String? = null,

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

data class VerifyOtpRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String? = null,

    @field:NotBlank(message = "OTP is required")
    @field:Pattern(
        regexp = "^\\d{6}$",
        message = "OTP must be exactly 6 digits",
    )
    val otp: String? = null,
)

data class ResendOtpRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String? = null,
)
