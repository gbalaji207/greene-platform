package com.greene.core.user.web

import com.greene.core.api.response.ApiResponse
import com.greene.core.user.dto.ChangeRoleResponse
import com.greene.core.user.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * User account management endpoints.
 *
 * Error codes thrown from [UserService] are mapped to the correct HTTP status
 * by [com.greene.core.web.GlobalExceptionHandler] via
 * [com.greene.core.exception.PlatformException.httpStatus].
 */
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    /**
     * PATCH /api/v1/users/{id}/role
     *
     * Changes the role of the target user account.
     * Restricted to ADMIN and SUPER_ADMIN callers.
     *
     * The caller's own user ID is extracted from the JWT principal set by
     * [com.greene.core.auth.security.JwtAuthenticationFilter] so that the
     * service layer can enforce the self-change guard.
     *
     * Returns 200 OK with the updated user summary.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/{id}/role")
    fun changeRole(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ChangeRoleRequest,
    ): ResponseEntity<ApiResponse<ChangeRoleResponse>> {
        val callerId = UUID.fromString(
            SecurityContextHolder.getContext().authentication.principal as String,
        )
        return ResponseEntity.ok(ApiResponse.of(userService.changeRole(id, request.role!!, callerId)))
    }
}

