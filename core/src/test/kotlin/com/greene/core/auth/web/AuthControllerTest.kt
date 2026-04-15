package com.greene.core.auth.web

import com.greene.core.auth.dto.LogoutResponse
import com.greene.core.auth.dto.TokenPairDto
import com.greene.core.auth.service.AuthService
import com.greene.core.exception.PlatformException
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
import org.springframework.test.web.servlet.post

@WebMvcTest(AuthController::class)
@Import(GlobalExceptionHandler::class, TestSecurityConfig::class)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var authService: AuthService

    // ── POST /api/v1/auth/identify ────────────────────────────────────────────

    @Test
    @WithMockUser
    fun `identify returns 403 ACCOUNT_SUSPENDED when the account is suspended`() {
        every { authService.identify("suspended@greene.in") } throws PlatformException(
            "ACCOUNT_SUSPENDED",
            "Your account has been suspended. Please contact your administrator.",
            HttpStatus.FORBIDDEN,
        )

        mockMvc.post("/api/v1/auth/identify") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "suspended@greene.in"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code")    { value("ACCOUNT_SUSPENDED") }
            jsonPath("$.error.message") {
                value("Your account has been suspended. Please contact your administrator.")
            }
            jsonPath("$.error.details") { isArray() }
        }
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────────

    @Test
    @WithMockUser
    fun `refresh_whenValidToken_shouldReturn200WithTokenPair`() {
        every { authService.refresh("550e8400-e29b-41d4-a716-446655440000") } returns
            TokenPairDto(
                accessToken  = "eyJhbGciOiJSUzI1NiJ9.test",
                refreshToken = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                expiresIn    = 900,
            )

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": "550e8400-e29b-41d4-a716-446655440000"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken")  { value("eyJhbGciOiJSUzI1NiJ9.test") }
            jsonPath("$.data.refreshToken") { value("f47ac10b-58cc-4372-a567-0e02b2c3d479") }
            jsonPath("$.data.expiresIn")    { value(900) }
            jsonPath("$.meta")              { exists() }
        }
    }

    @Test
    @WithMockUser
    fun `refresh_whenEmptyBody_shouldReturn400ValidationError`() {
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")            { value("VALIDATION_ERROR") }
            jsonPath("$.error.message")         { value("Validation failed") }
            jsonPath("$.error.details")         { isArray() }
            jsonPath("$.error.details[0].field") { value("refreshToken") }
        }
    }

    @Test
    @WithMockUser
    fun `refresh_whenBlankRefreshToken_shouldReturn400ValidationError`() {
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": ""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")    { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Validation failed") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser
    fun `refresh_whenServiceThrowsRefreshTokenInvalid_shouldReturn401`() {
        every { authService.refresh(any()) } throws PlatformException(
            "REFRESH_TOKEN_INVALID",
            "Invalid session. Please log in again.",
            HttpStatus.UNAUTHORIZED,
        )

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": "550e8400-e29b-41d4-a716-446655440000"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code")    { value("REFRESH_TOKEN_INVALID") }
            jsonPath("$.error.message") { value("Invalid session. Please log in again.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser
    fun `refresh_whenServiceThrowsRefreshTokenExpired_shouldReturn401`() {
        every { authService.refresh(any()) } throws PlatformException(
            "REFRESH_TOKEN_EXPIRED",
            "Your session has expired. Please log in again.",
            HttpStatus.UNAUTHORIZED,
        )

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": "550e8400-e29b-41d4-a716-446655440000"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code")    { value("REFRESH_TOKEN_EXPIRED") }
            jsonPath("$.error.message") { value("Your session has expired. Please log in again.") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @Test
    @WithMockUser
    fun `logout_whenValidToken_shouldReturn200WithSuccessMessage`() {
        every { authService.logout("550e8400-e29b-41d4-a716-446655440000") } returns
            LogoutResponse(message = "Logged out successfully")

        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": "550e8400-e29b-41d4-a716-446655440000"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.message") { value("Logged out successfully") }
            jsonPath("$.meta")         { exists() }
        }
    }

    @Test
    @WithMockUser
    fun `logout_whenEmptyBody_shouldReturn400ValidationError`() {
        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")             { value("VALIDATION_ERROR") }
            jsonPath("$.error.message")          { value("Validation failed") }
            jsonPath("$.error.details")          { isArray() }
            jsonPath("$.error.details[0].field") { value("refreshToken") }
        }
    }

    @Test
    @WithMockUser
    fun `logout_whenBlankRefreshToken_shouldReturn400ValidationError`() {
        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": ""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")    { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Validation failed") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    @WithMockUser
    fun `logout_whenTokenNotFound_shouldReturn200Idempotent`() {
        // Service returns success regardless — token-not-found is a silent success
        every { authService.logout(any()) } returns LogoutResponse(message = "Logged out successfully")

        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": "550e8400-e29b-41d4-a716-446655440000"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.message") { value("Logged out successfully") }
        }
    }

    @Test
    @WithMockUser
    fun `logout_whenTokenAlreadyRevoked_shouldReturn200Idempotent`() {
        // Already-revoked token is treated identically to a successful logout
        every { authService.logout(any()) } returns LogoutResponse(message = "Logged out successfully")

        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": "550e8400-e29b-41d4-a716-446655440000"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.message") { value("Logged out successfully") }
        }
    }
}

