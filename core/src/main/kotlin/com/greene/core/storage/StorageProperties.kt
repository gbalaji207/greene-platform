package com.greene.core.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from the `storage.*` namespace in application-{profile}.yml.
 *
 * Dev  → MinIO (local)
 * Prod → AWS S3 / Backblaze B2
 */
@ConfigurationProperties(prefix = "storage")
data class StorageProperties(
    val endpoint: String = "",
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val presignedUrlExpiryMinutes: Long = 60,
)

