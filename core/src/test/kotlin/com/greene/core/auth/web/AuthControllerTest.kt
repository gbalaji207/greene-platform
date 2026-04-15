package com.greene.core.auth.web

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
}

