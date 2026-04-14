package com.greene.core.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Binds all `aws.ses.*` keys from application.yml.
 *
 * aws:
 *   ses:
 *     region: ap-south-1
 *     from-address: noreply@yourdomain.com
 *
 * Credentials are resolved via the AWS Default Credential Provider Chain
 * (env vars AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY, instance profile, etc.).
 */
@ConfigurationProperties(prefix = "aws.ses")
data class SesProperties(

    /** AWS region where SES is configured, e.g. "ap-south-1". */
    val region: String = "ap-south-1",

    /**
     * Verified sender address in SES.
     * Required — application will fail to start if absent.
     */
    val fromAddress: String,
)

