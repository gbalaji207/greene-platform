package com.greene.core.storage

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration

/**
 * StorageService backed by AWS S3.
 *
 * Active on "staging" and "prod" Spring profiles.
 * Uses the standard AWS S3 client — no endpoint override, no path-style.
 * Credentials come from [StorageProperties] (injected from environment variables in prod).
 */
@Service
@Profile("staging", "prod")
class S3StorageService(private val properties: StorageProperties) : StorageService {

    private val credentials: StaticCredentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
        )

    private val client: S3Client =
        S3Client.builder()
            .credentialsProvider(credentials)
            .region(Region.US_EAST_1)
            .build()

    private val presigner: S3Presigner =
        S3Presigner.builder()
            .credentialsProvider(credentials)
            .region(Region.US_EAST_1)
            .build()

    override fun upload(key: String, bytes: ByteArray, contentType: String) {
        client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes),
        )
    }

    override fun delete(key: String) {
        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .build(),
        )
    }

    override fun getPresignedUrl(key: String): String =
        presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(properties.presignedUrlExpiryMinutes))
                .getObjectRequest(
                    GetObjectRequest.builder()
                        .bucket(properties.bucket)
                        .key(key)
                        .build()
                )
                .build()
        ).url().toString()
}

