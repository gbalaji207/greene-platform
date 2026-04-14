package com.greene.core.auth.service

/**
 * Contract for all outbound transactional email sent by the auth module.
 *
 * All methods are fire-and-forget from the caller's perspective.
 * Implementations (e.g. [SesEmailService]) are responsible for error handling
 * and should throw on unrecoverable delivery failures so the caller can surface
 * a meaningful error.
 */
interface EmailService {

    /**
     * Sends a one-time password to [to].
     *
     * @param to  Recipient email address.
     * @param otp Plaintext 6-digit OTP — **never** log or persist after this call.
     */
    fun sendOtp(to: String, otp: String)

    /**
     * Sends a welcome email to a newly activated account.
     *
     * @param to   Recipient email address.
     * @param name User's display name, interpolated into the email body.
     */
    fun sendWelcome(to: String, name: String)
}

