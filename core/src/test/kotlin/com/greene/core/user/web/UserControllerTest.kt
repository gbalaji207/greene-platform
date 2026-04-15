package com.greene.core.user.web

import com.greene.core.auth.security.JwtAccessDeniedHandler
import com.greene.core.auth.security.JwtAuthenticationEntryPoint
import com.greene.core.auth.security.JwtAuthenticationFilter
import com.greene.core.config.SecurityConfig
import com.greene.core.exception.PlatformException
import com.greene.core.user.dto.ChangeRoleResponse
import com.greene.core.user.service.UserService
import com.greene.core.web.GlobalExceptionHandler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import java.time.Instant
import java.util.UUID

/**
 * Security and business-logic tests for [UserController].
 *
 * Uses the real [SecurityConfig] (no [com.greene.core.web.TestSecurityConfig]) so
 * that the JWT filter, `@PreAuthorize`, and the 401/403 error handlers behave
 * exactly as in production.
 *
 * Tests that reach the controller method body use
 * [authentication] post-processor with a UUID String principal, because
 * `SecurityContextHolder.getContext().authentication.principal` is cast to `String`
 * inside [UserController.changeRole].  Tests that are rejected before the method
 * body (401 / 403 / 400) may use `@WithMockUser` since the cast is never reached.
 */
@WebMvcTest(UserController::class)
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    JwtAuthenticationEntryPoint::class,
    JwtAccessDeniedHandler::class,
)
class UserControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    private val targetId  = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private val callerId  = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private val updatedAt = Instant.parse("2026-04-15T10:00:00Z")

    /** Authentication token whose principal is a UUID string — mirrors what the real JWT filter sets. */
    private fun adminAuth(id: UUID = callerId) = UsernamePasswordAuthenticationToken(
        id.toString(),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    // ── 401 — no authentication ───────────────────────────────────────────────

    @Test
    fun `changeRole_whenNoJwt_shouldReturn401WithUnauthorizedEnvelope`() {
        mockMvc.patch("/api/v1/users/$targetId/role") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"role": "STAFF"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code")    { value("UNAUTHORIZED") }
            jsonPath("$.error.message") { value("Authentication required. Please log in.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    // ── 403 — insufficient role ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = ["CLIENT"])
    fun `changeRole_whenCallerIsClient_shouldReturn403WithForbiddenEnvelope`() {
        mockMvc.patch("/api/v1/users/$targetId/role") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"role": "STAFF"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code")    { value("FORBIDDEN") }
            jsonPath("$.error.message") { value("You do not have permission to perform this action.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    // ── 400 — validation failures ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `changeRole_whenBodyIsEmpty_shouldReturn400ValidationError`() {
        // @PreAuthorize passes; Spring MVC validation fires before method body.
        mockMvc.patch("/api/v1/users/$targetId/role") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.message")          { value("Validation failed") }
            jsonPath("$.error.details")          { isArray() }
            jsonPath("$.error.details[0].field") { value("role") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `changeRole_whenRoleIsUnknownValue_shouldReturn400ValidationError`() {
        mockMvc.patch("/api/v1/users/$targetId/role") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"role": "JANITOR"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.details[0].field") { value("role") }
        }
    }

    // ── 400 — SUPER_ADMIN rejected by @Pattern validation ────────────────────

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `changeRole_whenRoleIsSuperAdmin_shouldReturn422InvalidRoleChange`() {
        // ChangeRoleRequest's @Pattern regexp is "^(CLIENT|STAFF|ADMIN)$" — SUPER_ADMIN
        // does not match, so Bean Validation returns 400 before the service is called.
        mockMvc.patch("/api/v1/users/$targetId/role") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"role": "SUPER_ADMIN"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.message")          { value("Validation failed") }
            jsonPath("$.error.details")          { isArray() }
            jsonPath("$.error.details[0].field") { value("role") }
        }
    }

    // ── 422 — self role change ────────────────────────────────────────────────

    @Test
    fun `changeRole_whenCallerChangesOwnRole_shouldReturn422InvalidRoleChange`() {
        every { userService.changeRole(eq(callerId), any(), eq(callerId)) } throws
            PlatformException("INVALID_ROLE_CHANGE", "You cannot change your own role", HttpStatus.UNPROCESSABLE_ENTITY)

        // Use callerId as both path variable and principal so targetId == callerId.
        mockMvc.patch("/api/v1/users/$callerId/role") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"role": "STAFF"}"""
            with(authentication(adminAuth(callerId)))
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code")    { value("INVALID_ROLE_CHANGE") }
            jsonPath("$.error.message") { value("You cannot change your own role") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    // ── 404 — user not found ──────────────────────────────────────────────────

    @Test
    fun `changeRole_whenUserNotFound_shouldReturn404UserNotFound`() {
        every { userService.changeRole(any(), any(), any()) } throws
            PlatformException("USER_NOT_FOUND", "No user found for the given id", HttpStatus.NOT_FOUND)

        mockMvc.patch("/api/v1/users/$targetId/role") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"role": "STAFF"}"""
            with(authentication(adminAuth()))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code")    { value("USER_NOT_FOUND") }
            jsonPath("$.error.message") { value("No user found for the given id") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    // ── 200 — happy path ──────────────────────────────────────────────────────

    @Test
    fun `changeRole_whenAdminCallerAndValidRequest_shouldReturn200WithUpdatedUser`() {
        every { userService.changeRole(targetId, "STAFF", callerId) } returns ChangeRoleResponse(
            id        = targetId,
            name      = "Priya Sharma",
            email     = "priya@greene.in",
            role      = "STAFF",
            updatedAt = updatedAt,
        )

        mockMvc.patch("/api/v1/users/$targetId/role") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"role": "STAFF"}"""
            with(authentication(adminAuth()))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id")        { value(targetId.toString()) }
            jsonPath("$.data.name")      { value("Priya Sharma") }
            jsonPath("$.data.email")     { value("priya@greene.in") }
            jsonPath("$.data.role")      { value("STAFF") }
            jsonPath("$.data.updatedAt") { exists() }
            jsonPath("$.meta.version")   { value("1.0") }
        }
    }
}

