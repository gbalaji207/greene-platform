package com.greene.core.auth.web

import com.greene.core.auth.dto.IdentifyResponse
import com.greene.core.auth.dto.TokenPairDto
import com.greene.core.auth.security.JwtAccessDeniedHandler
import com.greene.core.auth.security.JwtAuthenticationEntryPoint
import com.greene.core.auth.security.JwtAuthenticationFilter
import com.greene.core.auth.service.AuthService
import com.greene.core.config.SecurityConfig
import com.greene.core.web.GlobalExceptionHandler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * Verifies that all auth endpoints under /api/v1/auth/ remain publicly accessible
 * without any JWT when the real [SecurityConfig] is in effect.
 *
 * These paths are in the permitAll list and skipped by [JwtAuthenticationFilter],
 * so neither the filter nor Spring Security should produce a 401.
 */
@WebMvcTest(AuthController::class)
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    JwtAuthenticationEntryPoint::class,
    JwtAccessDeniedHandler::class,
)
class AuthPublicEndpointSecurityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var authService: AuthService

    // ── POST /api/v1/auth/identify ────────────────────────────────────────────

    @Test
    fun `identify_withNoJwt_isAccessibleAndDoesNotReturn401`() {
        every { authService.identify("priya@greene.in") } returns IdentifyResponse(flow = "LOGIN")

        mockMvc.post("/api/v1/auth/identify") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"email": "priya@greene.in"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.flow") { value("LOGIN") }
        }
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────────

    @Test
    fun `refresh_withNoJwt_isAccessibleAndDoesNotReturn401`() {
        val tokenId = "550e8400-e29b-41d4-a716-446655440000"
        every { authService.refresh(tokenId) } returns TokenPairDto(
            accessToken  = "eyJ.test",
            refreshToken = "f47ac10b-test",
            expiresIn    = 900,
        )

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"refreshToken": "$tokenId"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { exists() }
        }
    }
}


