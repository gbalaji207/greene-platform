package com.greene.core.staff.web

import com.greene.core.auth.security.JwtAccessDeniedHandler
import com.greene.core.auth.security.JwtAuthenticationEntryPoint
import com.greene.core.auth.security.JwtAuthenticationFilter
import com.greene.core.config.SecurityConfig
import com.greene.core.staff.dto.StaffUserResponse
import com.greene.core.staff.dto.UpdatedStatusResponse
import com.greene.core.staff.service.StaffService
import com.greene.core.web.GlobalExceptionHandler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

/**
 * Security-layer tests for [StaffController].
 *
 * Uses the real [SecurityConfig] (no [com.greene.core.web.TestSecurityConfig]) so that
 * [com.greene.core.auth.security.JwtAuthenticationFilter], `@PreAuthorize`, and the
 * 401 / 403 error handlers are exercised exactly as they run in production.
 *
 * Business-logic scenarios (validation, service errors) are covered in
 * [StaffControllerTest]; here we only verify auth enforcement.
 */
@WebMvcTest(StaffController::class)
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    JwtAuthenticationEntryPoint::class,
    JwtAccessDeniedHandler::class,
)
class StaffControllerSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var staffService: StaffService

    private val staffId   = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private val updatedAt = Instant.parse("2026-04-15T10:05:00Z")

    private val validCreateBody = """
        {"name": "Priya Sharma", "email": "priya@greene.in", "phone": "+919876543210"}
    """.trimIndent()

    private val validStatusBody = """{"status": "SUSPENDED"}"""

    // ── POST /api/v1/staff ────────────────────────────────────────────────────

    @Test
    fun `createStaff_whenNoJwt_shouldReturn401WithUnauthorizedEnvelope`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content     = validCreateBody
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code")    { value("UNAUTHORIZED") }
            jsonPath("$.error.message") { value("Authentication required. Please log in.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser(roles = ["CLIENT"])
    fun `createStaff_whenCallerIsClient_shouldReturn403WithForbiddenEnvelope`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content     = validCreateBody
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code")    { value("FORBIDDEN") }
            jsonPath("$.error.message") { value("You do not have permission to perform this action.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser(roles = ["STAFF"])
    fun `createStaff_whenCallerIsStaff_shouldReturn403WithForbiddenEnvelope`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content     = validCreateBody
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code")    { value("FORBIDDEN") }
            jsonPath("$.error.message") { value("You do not have permission to perform this action.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `createStaff_whenCallerIsAdmin_shouldReachControllerAndReturn201`() {
        every { staffService.create(any()) } returns staffUserResponse()

        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content     = validCreateBody
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.id") { value(staffId.toString()) }
        }
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `createStaff_whenCallerIsSuperAdmin_shouldReachControllerAndReturn201`() {
        every { staffService.create(any()) } returns staffUserResponse()

        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content     = validCreateBody
        }.andExpect {
            status { isCreated() }
        }
    }

    // ── PATCH /api/v1/staff/{id}/status ──────────────────────────────────────

    @Test
    fun `updateStatus_whenNoJwt_shouldReturn401WithUnauthorizedEnvelope`() {
        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content     = validStatusBody
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code")    { value("UNAUTHORIZED") }
            jsonPath("$.error.message") { value("Authentication required. Please log in.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser(roles = ["CLIENT"])
    fun `updateStatus_whenCallerIsClient_shouldReturn403WithForbiddenEnvelope`() {
        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content     = validStatusBody
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code")    { value("FORBIDDEN") }
            jsonPath("$.error.message") { value("You do not have permission to perform this action.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `updateStatus_whenCallerIsAdmin_shouldReachControllerAndReturn200`() {
        every { staffService.updateStatus(staffId, "SUSPENDED") } returns UpdatedStatusResponse(
            id        = staffId,
            status    = "SUSPENDED",
            updatedAt = updatedAt,
        )

        mockMvc.patch("/api/v1/staff/$staffId/status") {
            contentType = MediaType.APPLICATION_JSON
            content     = validStatusBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("SUSPENDED") }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun staffUserResponse() = StaffUserResponse(
        id        = staffId,
        name      = "Priya Sharma",
        email     = "priya@greene.in",
        phone     = "+919876543210",
        role      = "STAFF",
        status    = "ACTIVE",
        createdAt = Instant.parse("2026-04-15T10:00:00Z"),
    )
}

