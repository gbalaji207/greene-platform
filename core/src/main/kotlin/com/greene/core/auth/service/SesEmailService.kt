package com.greene.core.auth.service

import com.greene.core.auth.config.SesProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.Body
import software.amazon.awssdk.services.ses.model.Content
import software.amazon.awssdk.services.ses.model.Destination
import software.amazon.awssdk.services.ses.model.Message
import software.amazon.awssdk.services.ses.model.SendEmailRequest

/**
 * [EmailService] implementation backed by Amazon SES (SDK v2, synchronous client).
 *
 * HTML templates are inline Kotlin string templates — simple and dependency-free.
 * SES region and sender address are configured via [SesProperties] (aws.ses.*).
 *
 * AWS credentials are resolved at [SesClient] construction time via the
 * Default Credential Provider Chain (env vars → instance profile → etc.).
 */
@Service
@Profile("staging", "prod")
class SesEmailService(
    private val sesClient: SesClient,
    private val sesProperties: SesProperties,
) : EmailService {

    private val log = LoggerFactory.getLogger(javaClass)

    // ── EmailService implementation ───────────────────────────────────────────

    override fun sendOtp(to: String, otp: String) {
        log.debug("Sending OTP email to {}", to)
        send(
            to      = to,
            subject = "Your Greene Platform verification code",
            html    = otpEmailHtml(otp),
        )
    }

    override fun sendWelcome(to: String, name: String) {
        log.debug("Sending welcome email to {}", to)
        send(
            to      = to,
            subject = "Welcome to Greene Platform",
            html    = welcomeEmailHtml(name),
        )
    }

    override fun sendStaffWelcome(to: String, name: String) {
        log.debug("Sending staff welcome email to {}", to)
        send(
            to      = to,
            subject = "Welcome to greene \u2014 Your staff account is ready",
            html    = staffWelcomeEmailHtml(name, to),
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun send(to: String, subject: String, html: String) {
        val request = SendEmailRequest.builder()
            .source(sesProperties.fromAddress)
            .destination(
                Destination.builder()
                    .toAddresses(to)
                    .build()
            )
            .message(
                Message.builder()
                    .subject(utf8Content(subject))
                    .body(
                        Body.builder()
                            .html(utf8Content(html))
                            .build()
                    )
                    .build()
            )
            .build()

        sesClient.sendEmail(request)
    }

    private fun utf8Content(text: String): Content =
        Content.builder().data(text).charset("UTF-8").build()

    // ── Email templates ───────────────────────────────────────────────────────

    private fun otpEmailHtml(otp: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
        <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,Helvetica,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
            <tr><td align="center">
              <table width="560" cellpadding="0" cellspacing="0"
                     style="background:#ffffff;border-radius:8px;padding:40px;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                <tr>
                  <td style="padding-bottom:24px;border-bottom:2px solid #e8f5e9;">
                    <h1 style="margin:0;font-size:22px;color:#2d7a2d;">Greene Platform</h1>
                  </td>
                </tr>
                <tr>
                  <td style="padding:28px 0 16px;">
                    <p style="margin:0 0 12px;font-size:16px;color:#333;">Your verification code is:</p>
                    <div style="background:#f4faf4;border:2px solid #c8e6c9;border-radius:8px;
                                padding:20px;text-align:center;margin:0 0 20px;">
                      <span style="font-size:40px;font-weight:bold;letter-spacing:12px;color:#2d7a2d;
                                   font-family:'Courier New',monospace;">$otp</span>
                    </div>
                    <p style="margin:0;font-size:14px;color:#666;">
                      This code expires in <strong>10 minutes</strong>.
                      Do not share it with anyone.
                    </p>
                  </td>
                </tr>
                <tr>
                  <td style="padding-top:24px;border-top:1px solid #eeeeee;">
                    <p style="margin:0;font-size:12px;color:#999;">
                      If you did not request this code, you can safely ignore this email.
                    </p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()

    private fun welcomeEmailHtml(name: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
        <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,Helvetica,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
            <tr><td align="center">
              <table width="560" cellpadding="0" cellspacing="0"
                     style="background:#ffffff;border-radius:8px;padding:40px;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                <tr>
                  <td style="padding-bottom:24px;border-bottom:2px solid #e8f5e9;">
                    <h1 style="margin:0;font-size:22px;color:#2d7a2d;">Greene Platform</h1>
                  </td>
                </tr>
                <tr>
                  <td style="padding:28px 0 16px;">
                    <h2 style="margin:0 0 16px;font-size:20px;color:#1b5e20;">
                      Welcome, $name! 🌱
                    </h2>
                    <p style="margin:0 0 12px;font-size:16px;color:#333;">
                      Your email has been verified and your account is now active.
                    </p>
                    <p style="margin:0;font-size:16px;color:#333;">
                      You can now log in and start exploring Greene Platform.
                    </p>
                  </td>
                </tr>
                <tr>
                  <td style="padding-top:24px;border-top:1px solid #eeeeee;">
                    <p style="margin:0;font-size:12px;color:#999;">
                      The Greene Platform Team
                    </p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()

    private fun staffWelcomeEmailHtml(name: String, email: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
        <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,Helvetica,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
            <tr><td align="center">
              <table width="560" cellpadding="0" cellspacing="0"
                     style="background:#ffffff;border-radius:8px;padding:40px;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                <tr>
                  <td style="padding-bottom:24px;border-bottom:2px solid #e8f5e9;">
                    <h1 style="margin:0;font-size:22px;color:#2d7a2d;">Greene Platform</h1>
                  </td>
                </tr>
                <tr>
                  <td style="padding:28px 0 16px;">
                    <p style="margin:0 0 12px;font-size:16px;color:#333;">Hi $name,</p>
                    <p style="margin:0 0 12px;font-size:16px;color:#333;">
                      Your greene staff account has been created.
                    </p>
                    <p style="margin:0 0 20px;font-size:16px;color:#333;">
                      <strong>Email:</strong> $email
                    </p>
                    <p style="margin:0 0 12px;font-size:16px;color:#333;">
                      To log in, visit the greene app and enter your email address.
                    </p>
                    <p style="margin:0;font-size:16px;color:#333;">
                      You will receive a one-time code to verify your identity.
                    </p>
                  </td>
                </tr>
                <tr>
                  <td style="padding-top:24px;border-top:1px solid #eeeeee;">
                    <p style="margin:0;font-size:12px;color:#999;">
                      The greene team
                    </p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()
}

