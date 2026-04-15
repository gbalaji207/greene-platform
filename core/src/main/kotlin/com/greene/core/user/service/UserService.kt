package com.greene.core.user.service

import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import com.greene.core.user.dto.ChangeRoleResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Domain service for user account management operations.
 *
 * Handles role changes as defined in E2-US4.
 * Business rules are enforced in the exact order specified in error-scenarios.md
 * so that callers always receive the most specific error first.
 */
@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
) {

    /**
     * Changes the role of [targetUserId] to [requestedRole].
     * [callerId] is the ID of the authenticated user making the request,
     * used to enforce the self-change guard.
     *
     * Validation order (from error-scenarios.md):
     *  1. Load user → 404 USER_NOT_FOUND
     *  2. requestedRole == SUPER_ADMIN → 422 INVALID_ROLE_CHANGE
     *  3. targetUserId == callerId → 422 INVALID_ROLE_CHANGE
     *  4. Update role, save, return [ChangeRoleResponse]
     */
    fun changeRole(targetUserId: UUID, requestedRole: String, callerId: UUID): ChangeRoleResponse {

        // Step 1 — load the user; treat unknown id as not found
        val user = userRepository.findById(targetUserId).orElseThrow {
            PlatformException(
                "USER_NOT_FOUND",
                "No user found for the given id",
                HttpStatus.NOT_FOUND,
            )
        }

        // Step 2 — SUPER_ADMIN assignment is never permitted
        if (requestedRole == UserRole.SUPER_ADMIN.name) {
            throw PlatformException(
                "INVALID_ROLE_CHANGE",
                "SUPER_ADMIN role cannot be assigned",
                HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }

        // Step 3 — caller must not change their own role
        if (targetUserId == callerId) {
            throw PlatformException(
                "INVALID_ROLE_CHANGE",
                "You cannot change your own role",
                HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }

        // Step 4 — apply the role change and persist
        user.role = UserRole.valueOf(requestedRole)
        val savedUser = userRepository.save(user)

        return ChangeRoleResponse(
            id        = savedUser.id!!,
            name      = savedUser.name,
            email     = savedUser.email,
            role      = savedUser.role.name,
            updatedAt = savedUser.updatedAt,
        )
    }
}

