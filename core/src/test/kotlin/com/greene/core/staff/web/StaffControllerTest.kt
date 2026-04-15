package com.greene.core.staff.web

import com.greene.core.exception.PlatformException
import com.greene.core.staff.dto.StaffUserResponse
import com.greene.core.staff.dto.UpdatedStatusResponse
import com.greene.core.staff.service.StaffService
import com.greene.core.web.GlobalExceptionHandler
import com.greene.core.web.TestSecurityConfig
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@WebMvcTest(StaffController::class)
@Import(GlobalExceptionHandler::class, TestSecurityConfig::class)
class StaffControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var staffService: StaffService

    private val staffId    = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private val staffEmail = "priya@greene.in"
    private val staffName  = "Priya Sharma"
    private val staffPhone = "+919876543210"
    private val createdAt  = Instant.parse("2026-04-14T10:00:00Z")
    private val updatedAt  = Instant.parse("2026-04-14T10:05:00Z")

    // ── POST /api/v1/staff ────────────────────────────────────────────────────

    @Test
    @WithMockUser
    fun `createStaff returns 201 with all StaffUserResponse fields`() {
        every { staffService.create(any()) } returns StaffUserResponse(
            id        = staffId,
            name      = staffName,
            email     = staffEmail,
            phone     = staffPhone,
            role      = "STAFF",
            status    = "ACTIVE",
            createdAt = createdAt,
        )

        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$staffName", "email": "$staffEmail", "phone": "$staffPhone"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.id")        { value(staffId.toString()) }
            jsonPath("$.data.name")      { value(staffName) }
            jsonPath("$.data.email")     { value(staffEmail) }
            jsonPath("$.data.phone")     { value(staffPhone) }
            jsonPath("$.data.role")      { value("STAFF") }
            jsonPath("$.data.status")    { value("ACTIVE") }
            jsonPath("$.data.createdAt") { exists() }
            jsonPath("$.meta.version")   { value("1.0") }
        }
    }

    @Test
    @WithMockUser
    fun `createStaff returns 400 VALIDATION_ERROR with details for all three fields when body is empty`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")           { value("VALIDATION_ERROR") }
            jsonPath("$.error.message")        { value("Validation failed") }
            jsonPath("$.error.details")        { isArray() }
            jsonPath("$.error.details.length()") { value(3) }
        }
    }

    @Test
    @WithMockUser
    fun `createStaff returns 400 VALIDATION_ERROR with name detail when name is missing`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "$staffEmail", "phone": "$staffPhone"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.details.length()") { value(1) }
            jsonPath("$.error.details[0].field") { value("name") }
        }
    }

    @Test
    @WithMockUser
    fun `createStaff returns 400 VALIDATION_ERROR with email detail when email format is invalid`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$staffName", "email": "not-an-email", "phone": "$staffPhone"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.details.length()") { value(1) }
            jsonPath("$.error.details[0].field") { value("email") }
        }
    }

    @Test
    @WithMockUser
    fun `createStaff returns 400 VALIDATION_ERROR with phone detail when phone format is not E164`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$staffName", "email": "$staffEmail", "phone": "0987654321"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.details.length()") { value(1) }
            jsonPath("$.error.details[0].field") { value("phone") }
        }
    }

    @Test
    @WithMockUser
    fun `createStaff returns 400 VALIDATION_ERROR with name detail when name is too short`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "A", "email": "$staffEmail", "phone": "$staffPhone"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.details.length()") { value(1) }
            jsonPath("$.error.details[0].field") { value("name") }
        }
    }

    @Test
    @WithMockUser
    fun `createStaff returns 409 EMAIL_ALREADY_REGISTERED when service throws that error`() {
        every { staffService.create(any()) } throws PlatformException(
            "EMAIL_ALREADY_REGISTERED",
            "An account with this email is already registered",
            HttpStatus.CONFLICT,
        )

        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$staffName", "email": "$staffEmail", "phone": "$staffPhone"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code")    { value("EMAIL_ALREADY_REGISTERED") }
            jsonPath("$.error.message") { value("An account with this email is already registered") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser
    fun `createStaff returns 409 PHONE_ALREADY_REGISTERED when service throws that error`() {
        every { staffService.create(any()) } throws PlatformException(
            "PHONE_ALREADY_REGISTERED",
            "An account with this phone number already exists",
            HttpStatus.CONFLICT,
        )

        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$staffName", "email": "$staffEmail", "phone": "$staffPhone"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code")    { value("PHONE_ALREADY_REGISTERED") }
            jsonPath("$.error.message") { value("An account with this phone number already exists") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    // ── PATCH /api/v1/staff/{id}/status ──────────────────────────────────────

    @Test
    @WithMockUser
    fun `updateStatus returns 200 with SUSPENDED status when suspending an active staff`() {
        every { staffService.updateStatus(staffId, "SUSPENDED") } returns UpdatedStatusResponse(
            id        = staffId,
            status    = "SUSPENDED",
            updatedAt = updatedAt,
        )

        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "SUSPENDED"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id")        { value(staffId.toString()) }
            jsonPath("$.data.status")    { value("SUSPENDED") }
            jsonPath("$.data.updatedAt") { exists() }
            jsonPath("$.meta.version")   { value("1.0") }
        }
    }

    @Test
    @WithMockUser
    fun `updateStatus returns 200 with ACTIVE status when reactivating a suspended staff`() {
        every { staffService.updateStatus(staffId, "ACTIVE") } returns UpdatedStatusResponse(
            id        = staffId,
            status    = "ACTIVE",
            updatedAt = updatedAt,
        )

        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "ACTIVE"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id")        { value(staffId.toString()) }
            jsonPath("$.data.status")    { value("ACTIVE") }
            jsonPath("$.data.updatedAt") { exists() }
        }
    }

    @Test
    @WithMockUser
    fun `updateStatus returns 400 VALIDATION_ERROR when status field is missing`() {
        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.details.length()") { value(1) }
            jsonPath("$.error.details[0].field") { value("status") }
        }
    }

    @Test
    @WithMockUser
    fun `updateStatus returns 400 VALIDATION_ERROR when status value is not ACTIVE or SUSPENDED`() {
        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "DELETED"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.details.length()") { value(1) }
            jsonPath("$.error.details[0].field") { value("status") }
        }
    }

    @Test
    @WithMockUser
    fun `updateStatus returns 404 STAFF_NOT_FOUND when service throws that error`() {
        every { staffService.updateStatus(staffId, "SUSPENDED") } throws PlatformException(
            "STAFF_NOT_FOUND",
            "No staff account found for the given id",
            HttpStatus.NOT_FOUND,
        )

        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "SUSPENDED"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code")    { value("STAFF_NOT_FOUND") }
            jsonPath("$.error.message") { value("No staff account found for the given id") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser
    fun `updateStatus returns 422 INVALID_STATUS_TRANSITION when service throws that error`() {
        every { staffService.updateStatus(staffId, "SUSPENDED") } throws PlatformException(
            "INVALID_STATUS_TRANSITION",
            "Account is already suspended",
            HttpStatus.UNPROCESSABLE_ENTITY,
        )

        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "SUSPENDED"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code")    { value("INVALID_STATUS_TRANSITION") }
            jsonPath("$.error.message") { value("Account is already suspended") }
            jsonPath("$.error.details") { isArray() }
        }
    }
}

