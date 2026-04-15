package com.greene.core.user.service

import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import com.greene.core.storage.StorageService
import com.greene.core.user.web.UpdateProfileRequest
import io.mockk.every
import io.mockk.just
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ProfileServiceTest {

    private val userRepository: UserRepository = mockk()
    private val storageService: StorageService  = mockk()

    private lateinit var profileService: ProfileService

    private val userId         = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private val now            = Instant.parse("2026-04-15T10:00:00Z")
    private val existingKey    = "profiles/$userId/old-photo.jpg"
    private val presignedUrl   = "http://localhost:9000/greene-dev/$existingKey?X-Amz-Signature=abc123"

    @BeforeEach
    fun setUp() {
        profileService = ProfileService(userRepository, storageService)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUser(
        phone: String         = "+919876543210",
        name: String          = "Arun Kumar",
        phoneVerified: Boolean = false,
        profilePhotoUrl: String? = null,
    ) = UserEntity(
        id             = userId,
        email          = "arun@greene.in",
        name           = name,
        phone          = phone,
        role           = UserRole.CLIENT,
        status         = UserStatus.ACTIVE,
        phoneVerified  = phoneVerified,
        createdAt      = now,
        updatedAt      = now,
        profilePhotoUrl = profilePhotoUrl,
    )

    /** Minimal JPG bytes (FF D8 FF + padding). */
    private fun jpgContent(totalSize: Int = 128): ByteArray =
        ByteArray(totalSize).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xD8.toByte()
            it[2] = 0xFF.toByte()
        }

    /** Minimal PNG bytes (89 50 4E 47 + padding). */
    private fun pngContent(totalSize: Int = 128): ByteArray =
        ByteArray(totalSize).also {
            it[0] = 0x89.toByte()
            it[1] = 0x50.toByte()
            it[2] = 0x4E.toByte()
            it[3] = 0x47.toByte()
        }

    /** PDF magic bytes (%PDF) — not an accepted image type. */
    private fun pdfContent(): ByteArray =
        ByteArray(128).also {
            it[0] = 0x25.toByte()   // %
            it[1] = 0x50.toByte()   // P
            it[2] = 0x44.toByte()   // D
            it[3] = 0x46.toByte()   // F
        }

    private fun jpgFile(content: ByteArray = jpgContent()) =
        MockMultipartFile("photo", "photo.jpg", "image/jpeg", content)

    private fun pngFile(content: ByteArray = pngContent()) =
        MockMultipartFile("photo", "photo.png", "image/png", content)

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    fun `getProfile_whenNoProfilePhoto_shouldReturnNullPhotoUrlAndNeverCallPresigner`() {
        every { userRepository.findById(userId) } returns Optional.of(buildUser())

        val result = profileService.getProfile(userId)

        assertNull(result.profilePhotoUrl)
        verify(exactly = 0) { storageService.getPresignedUrl(any()) }
    }

    @Test
    fun `getProfile_whenPhotoKeyPresent_shouldCallGetPresignedUrlAndReturnIt`() {
        every { userRepository.findById(userId) } returns
            Optional.of(buildUser(profilePhotoUrl = existingKey))
        every { storageService.getPresignedUrl(existingKey) } returns presignedUrl

        val result = profileService.getProfile(userId)

        assertEquals(presignedUrl, result.profilePhotoUrl)
        verify(exactly = 1) { storageService.getPresignedUrl(existingKey) }
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    fun `updateProfile_whenBothFieldsNull_shouldThrowAtLeastOneFieldRequired400`() {
        val ex = assertThrows<PlatformException> {
            profileService.updateProfile(userId, UpdateProfileRequest(name = null, phone = null))
        }

        assertEquals("AT_LEAST_ONE_FIELD_REQUIRED", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        assertEquals("At least one field must be provided", ex.message)
        // Neither the DB nor the storage layer should be touched
        verify(exactly = 0) { userRepository.findById(any()) }
    }

    @Test
    fun `updateProfile_whenPhoneBelongsToAnotherAccount_shouldThrowPhoneAlreadyRegistered409`() {
        val newPhone = "+911234567890"
        every { userRepository.findById(userId) } returns Optional.of(buildUser())
        every { userRepository.existsByPhoneAndIdNot(newPhone, userId) } returns true

        val ex = assertThrows<PlatformException> {
            profileService.updateProfile(userId, UpdateProfileRequest(phone = newPhone))
        }

        assertEquals("PHONE_ALREADY_REGISTERED", ex.code)
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
        assertEquals("An account with this phone number already exists", ex.message)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `updateProfile_whenPhoneChanged_shouldResetPhoneVerifiedToFalse`() {
        val newPhone = "+911234567890"
        val user = buildUser(phoneVerified = true)
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.existsByPhoneAndIdNot(newPhone, userId) } returns false
        every { userRepository.save(any()) } answers { firstArg() }

        profileService.updateProfile(userId, UpdateProfileRequest(phone = newPhone))

        verify(exactly = 1) { userRepository.save(match { !it.phoneVerified }) }
    }

    @Test
    fun `updateProfile_whenPhoneSameAsCurrent_shouldNotResetPhoneVerifiedAndNotCheckDuplicates`() {
        val samePhone = "+919876543210"
        val user = buildUser(phone = samePhone, phoneVerified = true)
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }

        profileService.updateProfile(userId, UpdateProfileRequest(phone = samePhone))

        // phoneVerified must remain true
        verify(exactly = 1) { userRepository.save(match { it.phoneVerified }) }
        // No duplicate-phone check for an unchanged phone
        verify(exactly = 0) { userRepository.existsByPhoneAndIdNot(any(), any()) }
    }

    @Test
    fun `updateProfile_whenNameOnly_shouldUpdateNameAndLeavePhoneUnchanged`() {
        val originalPhone = "+919876543210"
        val user = buildUser(phone = originalPhone)
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }

        val result = profileService.updateProfile(
            userId, UpdateProfileRequest(name = "Renamed User"),
        )

        assertEquals("Renamed User", result.name)
        assertEquals(originalPhone, result.phone)
        // No phone-conflict check when phone is not being changed
        verify(exactly = 0) { userRepository.existsByPhoneAndIdNot(any(), any()) }
    }

    @Test
    fun `updateProfile_whenPhoneOnly_shouldUpdatePhoneAndLeaveNameUnchanged`() {
        val originalName = "Arun Kumar"
        val newPhone = "+911234567890"
        val user = buildUser(name = originalName)
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.existsByPhoneAndIdNot(newPhone, userId) } returns false
        every { userRepository.save(any()) } answers { firstArg() }

        val result = profileService.updateProfile(
            userId, UpdateProfileRequest(phone = newPhone),
        )

        assertEquals(originalName, result.name)
        assertEquals(newPhone, result.phone)
    }

    // ── uploadPhoto ───────────────────────────────────────────────────────────

    @Test
    fun `uploadPhoto_whenValidJpgMagicBytes_shouldUploadWithJpegContentTypeAndReturnUrl`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns Optional.of(user)
        justRun { storageService.upload(any(), any(), any()) }
        every { userRepository.save(any()) } answers { firstArg() }
        every { storageService.getPresignedUrl(any()) } returns presignedUrl

        val result = profileService.uploadPhoto(userId, jpgFile())

        assertEquals(presignedUrl, result.profilePhotoUrl)
        verify(exactly = 1) { storageService.upload(any(), any(), eq("image/jpeg")) }
    }

    @Test
    fun `uploadPhoto_whenValidPngMagicBytes_shouldUploadWithPngContentTypeAndReturnUrl`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns Optional.of(user)
        justRun { storageService.upload(any(), any(), any()) }
        every { userRepository.save(any()) } answers { firstArg() }
        every { storageService.getPresignedUrl(any()) } returns presignedUrl

        val result = profileService.uploadPhoto(userId, pngFile())

        assertEquals(presignedUrl, result.profilePhotoUrl)
        verify(exactly = 1) { storageService.upload(any(), any(), eq("image/png")) }
    }

    @Test
    fun `uploadPhoto_whenPdfMagicBytes_shouldThrowInvalidFileType415`() {
        val ex = assertThrows<PlatformException> {
            profileService.uploadPhoto(
                userId,
                MockMultipartFile("photo", "document.pdf", "application/pdf", pdfContent()),
            )
        }

        assertEquals("INVALID_FILE_TYPE", ex.code)
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.httpStatus)
        assertEquals("Only jpg, jpeg, and png files are accepted", ex.message)
        // Must fail before touching storage or DB
        verify(exactly = 0) { storageService.upload(any(), any(), any()) }
        verify(exactly = 0) { userRepository.findById(any()) }
    }

    @Test
    fun `uploadPhoto_whenFileSizeExceeds5MB_shouldThrowFileTooLarge413`() {
        // 5 MB + 1 byte with valid JPG header
        val oversized = ByteArray(5 * 1024 * 1024 + 1).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xD8.toByte()
            it[2] = 0xFF.toByte()
        }

        val ex = assertThrows<PlatformException> {
            profileService.uploadPhoto(userId, jpgFile(oversized))
        }

        assertEquals("FILE_TOO_LARGE", ex.code)
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.httpStatus)
        assertEquals("File size must not exceed 5MB", ex.message)
        verify(exactly = 0) { storageService.upload(any(), any(), any()) }
    }

    @Test
    fun `uploadPhoto_whenFileSizeExactly5MB_shouldBeAccepted`() {
        // Exactly 5 * 1024 * 1024 bytes — must NOT be rejected
        val exactly5MB = ByteArray(5 * 1024 * 1024).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xD8.toByte()
            it[2] = 0xFF.toByte()
        }
        val user = buildUser()
        every { userRepository.findById(userId) } returns Optional.of(user)
        justRun { storageService.upload(any(), any(), any()) }
        every { userRepository.save(any()) } answers { firstArg() }
        every { storageService.getPresignedUrl(any()) } returns presignedUrl

        val result = profileService.uploadPhoto(userId, jpgFile(exactly5MB))

        assertNotNull(result.profilePhotoUrl)
        verify(exactly = 1) { storageService.upload(any(), any(), any()) }
    }

    @Test
    fun `uploadPhoto_whenNoExistingPhoto_shouldNotCallStorageDelete`() {
        val user = buildUser(profilePhotoUrl = null)
        every { userRepository.findById(userId) } returns Optional.of(user)
        justRun { storageService.upload(any(), any(), any()) }
        every { userRepository.save(any()) } answers { firstArg() }
        every { storageService.getPresignedUrl(any()) } returns presignedUrl

        profileService.uploadPhoto(userId, jpgFile())

        verify(exactly = 0) { storageService.delete(any()) }
    }

    @Test
    fun `uploadPhoto_whenExistingPhotoPresent_shouldDeleteOldKeyBeforeUploading`() {
        val user = buildUser(profilePhotoUrl = existingKey)
        every { userRepository.findById(userId) } returns Optional.of(user)
        justRun { storageService.delete(existingKey) }
        justRun { storageService.upload(any(), any(), any()) }
        every { userRepository.save(any()) } answers { firstArg() }
        every { storageService.getPresignedUrl(any()) } returns presignedUrl

        profileService.uploadPhoto(userId, jpgFile())

        verify(exactly = 1) { storageService.delete(existingKey) }
    }

    @Test
    fun `uploadPhoto_storageKey_shouldStartWithProfilesUserIdPrefixAndMatchExtension`() {
        val user = buildUser()
        val capturedKey = slot<String>()
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { storageService.upload(capture(capturedKey), any(), any()) } just runs
        every { userRepository.save(any()) } answers { firstArg() }
        every { storageService.getPresignedUrl(any()) } returns presignedUrl

        profileService.uploadPhoto(userId, jpgFile())

        assertTrue(
            capturedKey.captured.startsWith("profiles/$userId/"),
            "Key should start with 'profiles/$userId/' but was '${capturedKey.captured}'",
        )
        assertTrue(
            capturedKey.captured.endsWith(".jpg"),
            "Key should end with '.jpg' but was '${capturedKey.captured}'",
        )
    }
}

