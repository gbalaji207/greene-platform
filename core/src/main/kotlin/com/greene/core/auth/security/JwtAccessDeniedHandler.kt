package com.greene.core.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.greene.core.api.error.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/**
 * Invoked by Spring Security when a request arrives with a valid JWT but the
 * caller's role is insufficient for the target endpoint (e.g. CLIENT calling
 * an ADMIN-only route).
 *
 * Returns a 403 JSON error envelope that matches the platform's standard format,
 * rather than Spring Security's default redirect or HTML response.
 */
@Component
class JwtAccessDeniedHandler(private val objectMapper: ObjectMapper) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status      = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(
            response.writer,
            ApiError.of("FORBIDDEN", "You do not have permission to perform this action."),
        )
    }
}

