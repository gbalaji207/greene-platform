package com.greene.core.auth.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

/**
 * [EmailService] implementation backed by Mailpit for local development.
 *
 * Mailpit acts as a local SMTP sink — all outbound email is captured in its
 * web UI (http://localhost:8025) and never forwarded to a real mail server.
 *
 * Configure via application-dev.yml:
 *   spring.mail.host=localhost
 *   spring.mail.port=1025
 */
@Service
@Profile("dev")
class MailpitEmailService(
    private val mailSender: JavaMailSender,
) : EmailService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendOtp(to: String, otp: String) {
        log.debug("[Mailpit] Sending OTP email to {}", to)
        val message = SimpleMailMessage().apply {
            setTo(to)
            setFrom("noreply@greene.local")
            subject = "Your greene verification code"
            text = "Your OTP is: $otp\n\nThis code expires in 10 minutes.\nDo not share it with anyone."
        }
        mailSender.send(message)
    }

    override fun sendWelcome(to: String, name: String) {
        log.debug("[Mailpit] Sending welcome email to {}", to)
        val message = SimpleMailMessage().apply {
            setTo(to)
            setFrom("noreply@greene.local")
            subject = "Welcome to greene!"
            text = "Hi $name,\n\nYour account is now active. You can now browse batches and begin your journey.\n\nThe greene team"
        }
        mailSender.send(message)
    }
}

