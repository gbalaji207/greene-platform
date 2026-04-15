package com.greene.core.storage

/**
 * Abstraction over object storage.
 *
 * Implementations:
 *  - [MinioStorageService]  — active on profile "dev"   (local MinIO)
 *  - [S3StorageService]     — active on profiles "staging" / "prod" (AWS S3)
 */
interface StorageService {

    /**
     * Upload [bytes] under [key] with the given [contentType].
     * Overwrites silently if the key already exists.
     */
    fun upload(key: String, bytes: ByteArray, contentType: String)

    /**
     * Permanently delete the object identified by [key].
     * No-op if the key does not exist.
     */
    fun delete(key: String)

    /**
     * Return a pre-signed GET URL valid for the configured expiry window.
     */
    fun getPresignedUrl(key: String): String
}

