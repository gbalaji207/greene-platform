package com.greene.core.user.web

import com.greene.core.exception.PlatformException
import com.greene.core.user.dto.PhotoUploadResponse
import com.greene.core.user.dto.ProfileResponse
import com.greene.core.user.service.ProfileService
import com.greene.core.web.GlobalExceptionHandler
import com.greene.core.web.TestSecurityConfig
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
import java.time.Instant
import java.util.UUID

/**
 * Slice tests for [ProfileController].
 *
 * Uses [TestSecurityConfig] (permits any authenticated request) instead of the full
 * production [com.greene.core.config.SecurityConfig] so that the JWT filter chain
 * is not loaded — all route protection here is just `.anyRequest().authenticated()`.
 *
 * Tests that reach the controller body (happy paths, service-level errors) use the
 * [authentication] post-processor with a UUID-string principal, matching exactly what
 * [com.greene.core.auth.security.JwtAuthenticationFilter] sets in production.
 *
 * Tests where Spring rejects the request before the controller body runs
 * (Bean Validation, missing @RequestParam) may use @WithMockUser because
 * [ProfileController.currentUserId] is never invoked in those paths.
 */
@WebMvcTest(ProfileController::class)
@Import(GlobalExceptionHandler::class, TestSecurityConfig::class)
class ProfileControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var profileService: ProfileService

    private val userId    = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private val createdAt = Instant.parse("2026-04-01T10:00:00Z")
    private val photoUrl  = "http://localhost:9000/greene-dev/profiles/$userId/photo.jpg?X-Amz-Signature=abc"

    /** Authentication token whose principal is a UUID string — mirrors the real JWT filter. */
    private fun clientAuth(id: UUID = userId) = UsernamePasswordAuthenticationToken(
        id.toString(),
        null,
        listOf(SimpleGrantedAuthority("ROLE_CLIENT")),
    )

    private fun buildProfileResponse(profilePhotoUrl: String? = null) = ProfileResponse(
        id             = userId,
        name           = "Arun Kumar",
        email          = "arun@greene.in",
        phone          = "+919876543210",
        role           = "CLIENT",
        status         = "ACTIVE",
        profilePhotoUrl = profilePhotoUrl,
        createdAt      = createdAt,
    )

    // ── GET /api/v1/users/me ──────────────────────────────────────────────────

    @Test
    fun `getProfile_whenAuthenticated_shouldReturn200WithAllProfileFields`() {
        every { profileService.getProfile(userId) } returns buildProfileResponse(photoUrl)

        mockMvc.get("/api/v1/users/me") {
            with(authentication(clientAuth()))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id")              { value(userId.toString()) }
            jsonPath("$.data.name")            { value("Arun Kumar") }
            jsonPath("$.data.email")           { value("arun@greene.in") }
            jsonPath("$.data.phone")           { value("+919876543210") }
            jsonPath("$.data.role")            { value("CLIENT") }
            jsonPath("$.data.status")          { value("ACTIVE") }
            jsonPath("$.data.profilePhotoUrl") { value(photoUrl) }
            jsonPath("$.data.createdAt")       { exists() }
            jsonPath("$.meta.version")         { value("1.0") }
        }
    }

    @Test
    fun `getProfile_whenUserHasNoPhoto_shouldReturnNullProfilePhotoUrl`() {
        every { profileService.getProfile(userId) } returns buildProfileResponse(profilePhotoUrl = null)

        mockMvc.get("/api/v1/users/me") {
            with(authentication(clientAuth()))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.profilePhotoUrl") { value(null as Any?) }
        }
    }

    // ── PATCH /api/v1/users/me/profile ────────────────────────────────────────

    @Test
    fun `updateProfile_whenEmptyBody_shouldReturn400AtLeastOneFieldRequired`() {
        // {} deserialises to UpdateProfileRequest(name=null, phone=null).
        // Bean Validation passes (both fields nullable); service enforces the guard.
        every { profileService.updateProfile(any(), any()) } throws PlatformException(
            "AT_LEAST_ONE_FIELD_REQUIRED",
            "At least one field must be provided",
            HttpStatus.BAD_REQUEST,
        )

        mockMvc.patch("/api/v1/users/me/profile") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{}"""
            with(authentication(clientAuth()))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")    { value("AT_LEAST_ONE_FIELD_REQUIRED") }
            jsonPath("$.error.message") { value("At least one field must be provided") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser
    fun `updateProfile_whenNameTooShort_shouldReturn400ValidationErrorWithFieldDetail`() {
        // @Size(min=2) fires before the controller body — @WithMockUser is safe here.
        mockMvc.patch("/api/v1/users/me/profile") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"name": "A"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.message")          { value("Validation failed") }
            jsonPath("$.error.details")          { isArray() }
            jsonPath("$.error.details[0].field") { value("name") }
        }
    }

    @Test
    @WithMockUser
    fun `updateProfile_whenPhoneInvalidFormat_shouldReturn400ValidationErrorWithFieldDetail`() {
        // @Pattern fires before the controller body — @WithMockUser is safe here.
        mockMvc.patch("/api/v1/users/me/profile") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"phone": "09876543210"}"""   // missing leading '+'
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.message")          { value("Validation failed") }
            jsonPath("$.error.details")          { isArray() }
            jsonPath("$.error.details[0].field") { value("phone") }
        }
    }

    @Test
    fun `updateProfile_whenDuplicatePhone_shouldReturn409PhoneAlreadyRegistered`() {
        every { profileService.updateProfile(any(), any()) } throws PlatformException(
            "PHONE_ALREADY_REGISTERED",
            "An account with this phone number already exists",
            HttpStatus.CONFLICT,
        )

        mockMvc.patch("/api/v1/users/me/profile") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"phone": "+911234567890"}"""
            with(authentication(clientAuth()))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code")    { value("PHONE_ALREADY_REGISTERED") }
            jsonPath("$.error.message") { value("An account with this phone number already exists") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    fun `updateProfile_whenNameOnlyProvided_shouldReturn200WithUpdatedProfile`() {
        val updated = buildProfileResponse().copy(name = "Renamed User")
        every { profileService.updateProfile(any(), any()) } returns updated

        mockMvc.patch("/api/v1/users/me/profile") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"name": "Renamed User"}"""
            with(authentication(clientAuth()))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name")  { value("Renamed User") }
            jsonPath("$.data.phone") { value("+919876543210") }
            jsonPath("$.meta")       { exists() }
        }
    }

    @Test
    fun `updateProfile_whenPhoneOnlyProvided_shouldReturn200WithUpdatedProfile`() {
        val updated = buildProfileResponse().copy(phone = "+911234567890")
        every { profileService.updateProfile(any(), any()) } returns updated

        mockMvc.patch("/api/v1/users/me/profile") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"phone": "+911234567890"}"""
            with(authentication(clientAuth()))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name")  { value("Arun Kumar") }
            jsonPath("$.data.phone") { value("+911234567890") }
        }
    }

    // ── POST /api/v1/users/me/profile/photo ───────────────────────────────────

    @Test
    @WithMockUser
    fun `uploadPhoto_whenNoFileInRequest_shouldReturn400ValidationError`() {
        // MissingServletRequestParameterException is thrown before the controller body.
        // @WithMockUser is safe because currentUserId() is never reached.
        mockMvc.multipart("/api/v1/users/me/profile/photo") {
            // intentionally no file added
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")    { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Validation failed") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    fun `uploadPhoto_whenServiceThrowsInvalidFileType_shouldReturn415`() {
        every { profileService.uploadPhoto(any(), any()) } throws PlatformException(
            "INVALID_FILE_TYPE",
            "Only jpg, jpeg, and png files are accepted",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        )

        mockMvc.multipart("/api/v1/users/me/profile/photo") {
            file(MockMultipartFile("photo", "doc.pdf", "application/pdf", ByteArray(64)))
            with(authentication(clientAuth()))
        }.andExpect {
            status { isUnsupportedMediaType() }
            jsonPath("$.error.code")    { value("INVALID_FILE_TYPE") }
            jsonPath("$.error.message") { value("Only jpg, jpeg, and png files are accepted") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    fun `uploadPhoto_whenServiceThrowsFileTooLarge_shouldReturn413`() {
        every { profileService.uploadPhoto(any(), any()) } throws PlatformException(
            "FILE_TOO_LARGE",
            "File size must not exceed 5MB",
            HttpStatus.PAYLOAD_TOO_LARGE,
        )

        val mvcResult = mockMvc.multipart("/api/v1/users/me/profile/photo") {
            file(MockMultipartFile("photo", "big.jpg", "image/jpeg", ByteArray(64)))
            with(authentication(clientAuth()))
        }.andExpect {
            jsonPath("$.error.code")    { value("FILE_TOO_LARGE") }
            jsonPath("$.error.message") { value("File size must not exceed 5MB") }
            jsonPath("$.error.details") { isArray() }
        }.andReturn()

        assertEquals(413, mvcResult.response.status)
    }

    @Test
    fun `uploadPhoto_whenValidJpg_shouldReturn200WithProfilePhotoUrl`() {
        every { profileService.uploadPhoto(any(), any()) } returns
            PhotoUploadResponse(profilePhotoUrl = photoUrl)

        val jpgBytes = ByteArray(128).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xD8.toByte()
            it[2] = 0xFF.toByte()
        }

        mockMvc.multipart("/api/v1/users/me/profile/photo") {
            file(MockMultipartFile("photo", "photo.jpg", "image/jpeg", jpgBytes))
            with(authentication(clientAuth()))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.profilePhotoUrl") { value(photoUrl) }
            jsonPath("$.meta.version")         { value("1.0") }
        }
    }
}




