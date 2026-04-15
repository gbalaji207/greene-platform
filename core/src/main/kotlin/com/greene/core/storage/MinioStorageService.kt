package com.greene.core.storage

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.time.Duration

/**
 * StorageService backed by a local MinIO instance.
 *
 * Active only on the "dev" Spring profile.
 * MinIO requires path-style access and accepts any region string.
 */
@Service
@Profile("dev")
class MinioStorageService(private val properties: StorageProperties) : StorageService {

    private val credentials: StaticCredentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
        )

    private val s3Configuration: S3Configuration =
        S3Configuration.builder()
            .pathStyleAccessEnabled(true) // Required for MinIO
            .build()

    private val client: S3Client =
        S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint))
            .credentialsProvider(credentials)
            .region(Region.US_EAST_1) // MinIO ignores region but SDK requires it
            .serviceConfiguration(s3Configuration)
            .build()

    private val presigner: S3Presigner =
        S3Presigner.builder()
            .endpointOverride(URI.create(properties.endpoint))
            .credentialsProvider(credentials)
            .region(Region.US_EAST_1)
            .serviceConfiguration(s3Configuration)
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

