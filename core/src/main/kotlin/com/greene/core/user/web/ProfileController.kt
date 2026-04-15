package com.greene.core.user.web

import com.greene.core.api.response.ApiResponse
import com.greene.core.user.dto.PhotoUploadResponse
import com.greene.core.user.dto.ProfileResponse
import com.greene.core.user.service.ProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.StringToClassMapItem
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OasRequestBody
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Profile management endpoints for the authenticated user (E2-US5).
 *
 * User identity is always resolved from the JWT principal set by
 * [com.greene.core.auth.security.JwtAuthenticationFilter] — there is
 * intentionally no `{id}` path parameter on any of these endpoints.
 *
 * No [@PreAuthorize] needed — all routes require authentication via
 * the global `.anyRequest().authenticated()` rule in SecurityConfig.
 *
 * Error codes are thrown from [ProfileService] as [com.greene.core.exception.PlatformException]
 * and translated to the correct HTTP status by [com.greene.core.web.GlobalExceptionHandler].
 */
@RestController
@RequestMapping("/api/v1/users/me")
class ProfileController(private val profileService: ProfileService) {

    /**
     * GET /api/v1/users/me
     *
     * Returns the authenticated user's full profile.
     * [ProfileResponse.profilePhotoUrl] is a pre-signed URL if a photo exists, otherwise null.
     */
    @GetMapping
    fun getProfile(): ResponseEntity<ApiResponse<ProfileResponse>> {
        val userId = currentUserId()
        return ResponseEntity.ok(ApiResponse.of(profileService.getProfile(userId)))
    }

    /**
     * PATCH /api/v1/users/me/profile
     *
     * Updates name and/or phone. At least one field must be non-null.
     * Email is not present in [UpdateProfileRequest] and is silently ignored
     * at the JSON deserialization level.
     */
    @PatchMapping("/profile")
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ResponseEntity<ApiResponse<ProfileResponse>> {
        val userId = currentUserId()
        return ResponseEntity.ok(ApiResponse.of(profileService.updateProfile(userId, request)))
    }

    /**
     * POST /api/v1/users/me/profile/photo
     *
     * Uploads or replaces the user's profile photo.
     * Accepts `multipart/form-data` with a single field named `photo`.
     *
     * Content-type is validated by magic bytes (not file extension).
     * Accepted: jpg / jpeg / png, up to 5 MB.
     */
    @Operation(summary = "Upload profile photo")
    @OasRequestBody(
        content = [Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = Schema(
                type = "object",
                requiredProperties = ["photo"],
                properties = [StringToClassMapItem(key = "photo", value = MultipartFile::class)],
            ),
        )],
    )
    @PostMapping(
        value = ["/profile/photo"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun uploadPhoto(
        @RequestParam("photo") file: MultipartFile,
    ): ResponseEntity<ApiResponse<PhotoUploadResponse>> {
        val userId = currentUserId()
        return ResponseEntity.ok(ApiResponse.of(profileService.uploadPhoto(userId, file)))
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun currentUserId(): UUID =
        UUID.fromString(
            SecurityContextHolder.getContext().authentication.principal as String,
        )
}

