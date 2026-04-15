package com.greene.core.staff.service

import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.repository.RefreshTokenRepository
import com.greene.core.auth.repository.UserRepository
import com.greene.core.auth.service.EmailService
import com.greene.core.exception.PlatformException
import com.greene.core.staff.web.CreateStaffRequest
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.util.UUID

class StaffServiceTest {

    private val userRepository         : UserRepository         = mockk()
    private val refreshTokenRepository : RefreshTokenRepository = mockk()
    private val emailService           : EmailService           = mockk()

    private val service = StaffService(userRepository, refreshTokenRepository, emailService)

    private val staffId    = UUID.randomUUID()
    private val staffEmail = "priya@greene.in"
    private val staffName  = "Priya Sharma"
    private val staffPhone = "+919876543210"

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create saves user with role STAFF status ACTIVE phoneVerified false and returns correct response`() {
        val savedUser = staffUser()
        every { userRepository.existsByEmail(staffEmail) } returns false
        every { userRepository.existsByPhone(staffPhone) } returns false
        every { userRepository.save(any()) } returns savedUser
        justRun { emailService.sendStaffWelcome(staffEmail, staffName) }

        val response = service.create(
            CreateStaffRequest(name = staffName, email = staffEmail, phone = staffPhone)
        )

        assertEquals(staffId,    response.id)
        assertEquals(staffName,  response.name)
        assertEquals(staffEmail, response.email)
        assertEquals(staffPhone, response.phone)
        assertEquals("STAFF",    response.role)
        assertEquals("ACTIVE",   response.status)
        assertNotNull(response.createdAt)

        verify {
            userRepository.save(
                match {
                    it.role          == UserRole.STAFF   &&
                    it.status        == UserStatus.ACTIVE &&
                    it.phoneVerified == false
                }
            )
        }
        verify(exactly = 1) { emailService.sendStaffWelcome(staffEmail, staffName) }
    }

    @Test
    fun `create normalises email to lowercase before persisting and sending welcome email`() {
        val savedUser = staffUser()
        every { userRepository.existsByEmail(staffEmail) } returns false
        every { userRepository.existsByPhone(staffPhone) } returns false
        every { userRepository.save(any()) } returns savedUser
        justRun { emailService.sendStaffWelcome(staffEmail, staffName) }

        service.create(CreateStaffRequest(name = staffName, email = "Priya@Greene.IN", phone = staffPhone))

        verify { userRepository.save(match { it.email == staffEmail }) }
        verify(exactly = 1) { emailService.sendStaffWelcome(staffEmail, staffName) }
    }

    @Test
    fun `create throws EMAIL_ALREADY_REGISTERED when email already exists`() {
        every { userRepository.existsByEmail(staffEmail) } returns true

        val ex = assertThrows<PlatformException> {
            service.create(CreateStaffRequest(name = staffName, email = staffEmail, phone = staffPhone))
        }

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.code)
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { emailService.sendStaffWelcome(any(), any()) }
    }

    @Test
    fun `create throws PHONE_ALREADY_REGISTERED when phone already exists`() {
        every { userRepository.existsByEmail(staffEmail) } returns false
        every { userRepository.existsByPhone(staffPhone) } returns true

        val ex = assertThrows<PlatformException> {
            service.create(CreateStaffRequest(name = staffName, email = staffEmail, phone = staffPhone))
        }

        assertEquals("PHONE_ALREADY_REGISTERED", ex.code)
        assertEquals(HttpStatus.CONFLICT, ex.httpStatus)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `create checks email before phone so EMAIL_ALREADY_REGISTERED wins when both duplicates exist`() {
        every { userRepository.existsByEmail(staffEmail) } returns true
        // phone check must never be reached

        val ex = assertThrows<PlatformException> {
            service.create(CreateStaffRequest(name = staffName, email = staffEmail, phone = staffPhone))
        }

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.code)
        verify(exactly = 0) { userRepository.existsByPhone(any()) }
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    fun `updateStatus suspends ACTIVE staff revokes all refresh tokens and returns SUSPENDED`() {
        val activeStaff = staffUser(status = UserStatus.ACTIVE)
        every { userRepository.findByIdAndRole(staffId, UserRole.STAFF) } returns activeStaff
        every { refreshTokenRepository.revokeAllActiveByUserId(staffId, any()) } returns 2
        every { userRepository.save(any()) } answers { firstArg() }

        val response = service.updateStatus(staffId, "SUSPENDED")

        assertEquals(staffId,      response.id)
        assertEquals("SUSPENDED",  response.status)
        assertNotNull(response.updatedAt)
        verify(exactly = 1) { refreshTokenRepository.revokeAllActiveByUserId(staffId, any()) }
    }

    @Test
    fun `updateStatus reactivates SUSPENDED staff without revoking tokens and returns ACTIVE`() {
        val suspendedStaff = staffUser(status = UserStatus.SUSPENDED)
        every { userRepository.findByIdAndRole(staffId, UserRole.STAFF) } returns suspendedStaff
        every { userRepository.save(any()) } answers { firstArg() }

        val response = service.updateStatus(staffId, "ACTIVE")

        assertEquals(staffId,  response.id)
        assertEquals("ACTIVE", response.status)
        // revokeAllActiveByUserId is not mocked; if called it throws MockKException → test fails
        verify(exactly = 0) { refreshTokenRepository.revokeAllActiveByUserId(any(), any()) }
    }

    @Test
    fun `updateStatus throws STAFF_NOT_FOUND when id does not exist`() {
        every { userRepository.findByIdAndRole(staffId, UserRole.STAFF) } returns null

        val ex = assertThrows<PlatformException> {
            service.updateStatus(staffId, "SUSPENDED")
        }

        assertEquals("STAFF_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `updateStatus throws STAFF_NOT_FOUND when id belongs to a CLIENT account`() {
        // findByIdAndRole filters by role=STAFF; a CLIENT or ADMIN id returns null
        every { userRepository.findByIdAndRole(staffId, UserRole.STAFF) } returns null

        val ex = assertThrows<PlatformException> {
            service.updateStatus(staffId, "SUSPENDED")
        }

        assertEquals("STAFF_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `updateStatus throws INVALID_STATUS_TRANSITION with message when already suspended`() {
        val suspendedStaff = staffUser(status = UserStatus.SUSPENDED)
        every { userRepository.findByIdAndRole(staffId, UserRole.STAFF) } returns suspendedStaff

        val ex = assertThrows<PlatformException> {
            service.updateStatus(staffId, "SUSPENDED")
        }

        assertEquals("INVALID_STATUS_TRANSITION", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Account is already suspended", ex.message)
        verify(exactly = 0) { refreshTokenRepository.revokeAllActiveByUserId(any(), any()) }
    }

    @Test
    fun `updateStatus throws INVALID_STATUS_TRANSITION with message when already active`() {
        val activeStaff = staffUser(status = UserStatus.ACTIVE)
        every { userRepository.findByIdAndRole(staffId, UserRole.STAFF) } returns activeStaff

        val ex = assertThrows<PlatformException> {
            service.updateStatus(staffId, "ACTIVE")
        }

        assertEquals("INVALID_STATUS_TRANSITION", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Account is already active", ex.message)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun staffUser(status: UserStatus = UserStatus.ACTIVE) = UserEntity(
        id            = staffId,
        email         = staffEmail,
        name          = staffName,
        phone         = staffPhone,
        role          = UserRole.STAFF,
        status        = status,
        phoneVerified = false,
    )
}

