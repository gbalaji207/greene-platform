package com.greene.core.user.service

import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import com.greene.core.storage.StorageService
import com.greene.core.util.ImageTypeDetector
import com.greene.core.user.dto.PhotoUploadResponse
import com.greene.core.user.dto.ProfileResponse
import com.greene.core.user.web.UpdateProfileRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Domain service for the Client Profile Management feature (E2-US5).
 *
 * Responsibilities:
 *  - Read the authenticated user's own profile
 *  - Patch name / phone
 *  - Upload / replace the profile photo via [StorageService]
 *
 * All public methods load the entity within the caller's transaction.
 * Photo validation is intentionally checked before the database is touched
 * so that no storage I/O occurs for invalid uploads.
 */
@Service
@Transactional
class ProfileService(
    private val userRepository: UserRepository,
    private val storageService: StorageService,
) {

    // -------------------------------------------------------------------------
    // 1. Get profile
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun getProfile(userId: UUID): ProfileResponse {
        val user = loadUser(userId)
        return user.toProfileResponse()
    }

    // -------------------------------------------------------------------------
    // 2. Update profile (name and / or phone)
    // -------------------------------------------------------------------------

    fun updateProfile(userId: UUID, request: UpdateProfileRequest): ProfileResponse {

        // a) At least one field must be supplied
        if (request.name == null && request.phone == null) {
            throw PlatformException(
                code = "AT_LEAST_ONE_FIELD_REQUIRED",
                message = "At least one field must be provided",
                httpStatus = HttpStatus.BAD_REQUEST,
            )
        }

        val user = loadUser(userId)

        // b / c) Phone: only act if provided; only update when the value actually changes
        if (request.phone != null && request.phone != user.phone) {
            if (userRepository.existsByPhoneAndIdNot(request.phone, userId)) {
                throw PlatformException(
                    code = "PHONE_ALREADY_REGISTERED",
                    message = "An account with this phone number already exists",
                    httpStatus = HttpStatus.CONFLICT,
                )
            }
            user.phone = request.phone
            user.phoneVerified = false
        }

        // d) Name: only act if provided
        if (request.name != null) {
            user.name = request.name
        }

        // e) Persist; re-read through getProfile so the pre-signed URL is resolved
        userRepository.save(user)
        return user.toProfileResponse()
    }

    // -------------------------------------------------------------------------
    // 3. Upload / replace profile photo
    // -------------------------------------------------------------------------

    fun uploadPhoto(userId: UUID, file: MultipartFile): PhotoUploadResponse {

        // a) Detect real content type from magic bytes — never trust the file extension
        val header = file.inputStream.use { it.readNBytes(8) }
        val (contentType, ext) = ImageTypeDetector.detect(header)
            ?: throw PlatformException(
                code = "INVALID_FILE_TYPE",
                message = "Only jpg, jpeg, and png files are accepted",
                httpStatus = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            )

        // b) Size guard — checked after type so the type error is surfaced first
        if (file.size > MAX_PHOTO_BYTES) {
            throw PlatformException(
                code = "FILE_TOO_LARGE",
                message = "File size must not exceed 5MB",
                httpStatus = HttpStatus.PAYLOAD_TOO_LARGE,
            )
        }

        // c) Generate a unique, collision-free storage key
        val key = "profiles/$userId/${UUID.randomUUID()}.$ext"

        // d) Delete the old photo before uploading the replacement
        val user = loadUser(userId)
        user.profilePhotoUrl?.let { storageService.delete(it) }

        // e) Upload the new file
        storageService.upload(key, file.bytes, contentType)

        // f) Persist the new key on the user record
        user.profilePhotoUrl = key
        userRepository.save(user)

        // g) Return the pre-signed URL for the uploaded photo
        return PhotoUploadResponse(profilePhotoUrl = storageService.getPresignedUrl(key))
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun loadUser(userId: UUID): UserEntity =
        userRepository.findById(userId).orElseThrow {
            PlatformException(
                code = "USER_NOT_FOUND",
                message = "No user found for the given id",
                httpStatus = HttpStatus.NOT_FOUND,
            )
        }

    /**
     * Maps a [UserEntity] to [ProfileResponse], resolving the pre-signed URL
     * when a photo key is present.
     */
    private fun UserEntity.toProfileResponse(): ProfileResponse =
        ProfileResponse(
            id = id!!,
            name = name,
            email = email,
            phone = phone,
            role = role.name,
            status = status.name,
            profilePhotoUrl = profilePhotoUrl?.let { storageService.getPresignedUrl(it) },
            createdAt = createdAt,
        )


    companion object {
        private const val MAX_PHOTO_BYTES = 5L * 1024 * 1024   // 5 MB
    }
}

