package com.greene.core.auth.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * Invoked by Spring Security when a request reaches a protected endpoint
 * with no (or no valid) authentication in the [SecurityContext].
 *
 * Returns a JSON error envelope that matches the platform's standard error format,
 * rather than Spring Security's default HTML/redirect response.
 */
@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status      = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(BODY)
    }

    companion object {
        private val BODY = """
            {"error":{"code":"UNAUTHORIZED","message":"Authentication required. Provide a valid Bearer token.","details":[]}}
        """.trimIndent()
    }
}

