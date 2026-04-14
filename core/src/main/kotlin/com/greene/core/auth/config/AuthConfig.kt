package com.greene.core.auth.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient

/**
 * Central auth infrastructure configuration.
 *
 * Registers:
 *  - @ConfigurationProperties beans for otp.*, jwt.*, aws.ses.*
 *  - PasswordEncoder (BCrypt, strength 12) — shared by OtpService
 *  - SesClient — shared by SesEmailService
 */
@Configuration
@EnableConfigurationProperties(OtpProperties::class, JwtProperties::class, SesProperties::class)
class AuthConfig {

    /**
     * BCrypt encoder, strength 12.
     * Shared bean so it can be mocked in unit tests without constructing a real encoder.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    /**
     * AWS SES v2 client.
     * Credentials are resolved via the Default Credential Provider Chain at startup.
     * The client is a long-lived singleton — SesClient is thread-safe.
     */
    @Bean
    fun sesClient(sesProperties: SesProperties): SesClient =
        SesClient.builder()
            .region(Region.of(sesProperties.region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
}
