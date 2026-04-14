package com.greene.core.auth.security

import com.greene.core.auth.service.JwtService
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Lazy
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Stateless JWT authentication filter.
 *
 * [jwtService] is injected lazily so that @WebMvcTest slice contexts — which do not
 * load service beans — can still create this filter without a missing-bean error.
 * The actual JwtService bean is resolved on the first request that carries a
 * Bearer token, which never happens in existing unit test slices.
 *
 * Behaviour per request:
 *  - No `Authorization` header  → unauthenticated; Spring Security enforces 401 for
 *    protected routes via [JwtAuthenticationEntryPoint].
 *  - `Authorization: Bearer <token>` present and VALID → extracts `sub` (userId),
 *    `email`, and `role` claims; places a [UsernamePasswordAuthenticationToken] in
 *    the [SecurityContextHolder] so downstream code can read the principal.
 *  - `Authorization: Bearer <token>` present but INVALID/EXPIRED → returns 401
 *    immediately (filter chain is not continued) so the client gets a clear error
 *    rather than a misleading "no auth" 401 from the entry point.
 *
 * The principal set on the authentication token is the `sub` claim (userId as String).
 * Future E2 stories can extend this to a richer `AuthenticatedUser` object.
 */
@Component
class JwtAuthenticationFilter(
    @Lazy private val jwtService: JwtService,
) : OncePerRequestFilter() {

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private val INVALID_TOKEN_BODY = """
            {"error":{"code":"INVALID_TOKEN","message":"Token is invalid or has expired.","details":[]}}
        """.trimIndent()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")

        // ── No token supplied — continue unauthenticated ──────────────────────
        if (authHeader.isNullOrBlank() || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.removePrefix(BEARER_PREFIX).trim()

        // ── Token present — validate it ───────────────────────────────────────
        try {
            val claims = jwtService.validateAccessToken(token)

            // Only set auth if no authentication is already present (e.g. tests).
            if (SecurityContextHolder.getContext().authentication == null) {
                val userId = claims.subject
                val role   = claims.get("role", String::class.java) ?: "CLIENT"

                val authentication = UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_$role")),
                ).also { it.details = WebAuthenticationDetailsSource().buildDetails(request) }

                SecurityContextHolder.getContext().authentication = authentication
            }

            filterChain.doFilter(request, response)

        } catch (ex: JwtException) {
            // ── Invalid / expired token — reject immediately ──────────────────
            response.status      = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(INVALID_TOKEN_BODY)
            // Do NOT continue the filter chain.
        } catch (ex: IllegalArgumentException) {
            // Blank or malformed token string passed to parser.
            response.status      = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(INVALID_TOKEN_BODY)
        }
    }
}



