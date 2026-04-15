package com.greene.core.auth.web

import com.greene.core.api.response.ApiResponse
import com.greene.core.auth.dto.AuthTokenResponse
import com.greene.core.auth.dto.IdentifyResponse
import com.greene.core.auth.dto.LogoutResponse
import com.greene.core.auth.dto.RegisterResponse
import com.greene.core.auth.dto.ResendOtpResponse
import com.greene.core.auth.dto.TokenPairDto
import com.greene.core.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public auth endpoints — no JWT required.
 * All paths are explicitly permitted in [com.greene.core.config.SecurityConfig].
 *
 * Error codes thrown from [AuthService] and the service layer are mapped to the
 * correct HTTP status by [com.greene.core.web.GlobalExceptionHandler] via
 * [com.greene.core.exception.PlatformException.httpStatus].
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    /**
     * POST /api/v1/auth/identify
     *
     * Determines the client flow (LOGIN or REGISTER) from the supplied email.
     * For LOGIN flow an OTP is generated and sent immediately.
     * Response is always 200 to avoid leaking whether the email exists.
     */
    @PostMapping("/identify")
    fun identify(
        @Valid @RequestBody request: IdentifyRequest,
    ): ResponseEntity<ApiResponse<IdentifyResponse>> =
        ResponseEntity.ok(ApiResponse.of(authService.identify(request.email!!)))

    /**
     * POST /api/v1/auth/register
     *
     * Creates a new PENDING_VERIFICATION account (or refreshes an existing one)
     * and sends an EMAIL_VERIFICATION OTP.
     * Returns 201 Created.
     */
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): ResponseEntity<ApiResponse<RegisterResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(authService.register(request.email!!, request.name!!, request.phone!!)))

    /**
     * POST /api/v1/auth/verify-otp
     *
     * Validates the submitted OTP for either the LOGIN or EMAIL_VERIFICATION flow.
     * On success, activates the account (if registration) and returns a token pair.
     */
    @PostMapping("/verify-otp")
    fun verifyOtp(
        @Valid @RequestBody request: VerifyOtpRequest,
    ): ResponseEntity<ApiResponse<AuthTokenResponse>> =
        ResponseEntity.ok(ApiResponse.of(authService.verifyOtp(request.email!!, request.otp!!)))

    /**
     * POST /api/v1/auth/resend-otp
     *
     * Resends the active OTP (subject to throttle rules) and returns the
     * instant at which the next resend will be permitted.
     */
    @PostMapping("/resend-otp")
    fun resendOtp(
        @Valid @RequestBody request: ResendOtpRequest,
    ): ResponseEntity<ApiResponse<ResendOtpResponse>> =
        ResponseEntity.ok(ApiResponse.of(authService.resendOtp(request.email!!)))

    /**
     * POST /api/v1/auth/refresh
     *
     * Validates the supplied refresh token, rotates it, and returns a new token pair.
     * Old refresh token is revoked on success — single use enforced.
     * Public endpoint — no JWT required.
     */
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<ApiResponse<TokenPairDto>> =
        ResponseEntity.ok(ApiResponse.of(authService.refresh(request.refreshToken!!)))

    /**
     * POST /api/v1/auth/logout
     *
     * Revokes the supplied refresh token. Idempotent — always returns 200.
     * Public endpoint — no JWT required.
     */
    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ): ResponseEntity<ApiResponse<LogoutResponse>> =
        ResponseEntity.ok(ApiResponse.of(authService.logout(request.refreshToken!!)))
}

