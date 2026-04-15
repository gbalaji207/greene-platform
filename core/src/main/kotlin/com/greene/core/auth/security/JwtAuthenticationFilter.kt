package com.greene.core.auth.security

import com.greene.core.auth.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Lazy
import org.springframework.security.authentication.InsufficientAuthenticationException
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
 *  - Path in permitAll list (auth endpoints, Swagger) → filter is skipped entirely.
 *  - No `Authorization` header → unauthenticated; Spring Security enforces 401 for
 *    protected routes via [JwtAuthenticationEntryPoint].
 *  - `Authorization: Bearer <token>` present and VALID → extracts `sub` (userId) and
 *    `role` claims; places a [UsernamePasswordAuthenticationToken] in the
 *    [SecurityContextHolder] so downstream code can read the principal.
 *  - `Authorization: Bearer <token>` present but INVALID/EXPIRED → calls
 *    [JwtAuthenticationEntryPoint.commence] directly so the client gets a 401
 *    with the standard error envelope (filter chain is not continued).
 *
 * The principal set on the authentication token is the `sub` claim (userId as String).
 */
@Component
class JwtAuthenticationFilter(
    @Lazy private val jwtService: JwtService,
    @Lazy private val entryPoint: JwtAuthenticationEntryPoint,
) : OncePerRequestFilter() {

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }

    /**
     * Skip this filter entirely for all public endpoints so that valid auth headers
     * are never required on paths that appear in the SecurityConfig permitAll list.
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/api/v1/auth/") ||
               path.startsWith("/swagger-ui")   ||
               path.startsWith("/v3/api-docs")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")

        // ── No token supplied — continue unauthenticated ──────────────────────
        // Spring Security will call JwtAuthenticationEntryPoint for any endpoint
        // that requires authentication, producing a correctly formatted 401.
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

        } catch (ex: Exception) {
            // ── Invalid / expired token — reject immediately via entry point ──
            // Using the entry point directly ensures the 401 body matches the
            // standard error envelope ("UNAUTHORIZED") for ALL failure modes.
            entryPoint.commence(
                request,
                response,
                InsufficientAuthenticationException(ex.message ?: "Invalid or expired token", ex),
            )
            // Do NOT continue the filter chain.
        }
    }
}
