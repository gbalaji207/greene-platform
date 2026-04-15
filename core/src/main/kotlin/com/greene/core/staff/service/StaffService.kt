package com.greene.core.staff.service

import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.repository.RefreshTokenRepository
import com.greene.core.auth.repository.UserRepository
import com.greene.core.auth.service.EmailService
import com.greene.core.exception.PlatformException
import com.greene.core.staff.dto.StaffUserResponse
import com.greene.core.staff.dto.UpdatedStatusResponse
import com.greene.core.staff.web.CreateStaffRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Orchestrates staff account management as defined in E2-US2.
 *
 * All public methods are transactional: if an outbound call (SES) fails,
 * any partial DB changes are rolled back so the caller can retry cleanly.
 *
 * Email normalisation: all email lookups use lowercase. Callers may pass
 * mixed-case emails and they will be normalised transparently.
 */
@Service
@Transactional
class StaffService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val emailService: EmailService,
) {

    /**
     * Creates a new ACTIVE staff account.
     *
     *  - Duplicate email (any status) → 409 EMAIL_ALREADY_REGISTERED
     *  - Duplicate phone              → 409 PHONE_ALREADY_REGISTERED
     *  - Success                      → persists UserEntity, sends welcome email, returns [StaffUserResponse]
     */
    fun create(request: CreateStaffRequest): StaffUserResponse {
        val normalisedEmail = request.email!!.lowercase()
        val phone           = request.phone!!
        val name            = request.name!!

        // Step 1 — email uniqueness guard (any status).
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw PlatformException(
                "EMAIL_ALREADY_REGISTERED",
                "An account with this email is already registered",
                HttpStatus.CONFLICT,
            )
        }

        // Step 2 — phone uniqueness guard.
        if (userRepository.existsByPhone(phone)) {
            throw PlatformException(
                "PHONE_ALREADY_REGISTERED",
                "An account with this phone number already exists",
                HttpStatus.CONFLICT,
            )
        }

        // Step 3 — create the staff account, immediately ACTIVE (no OTP verification required).
        val user = userRepository.save(
            UserEntity(
                email         = normalisedEmail,
                name          = name,
                phone         = phone,
                role          = UserRole.STAFF,
                status        = UserStatus.ACTIVE,
                phoneVerified = false,
            )
        )

        // Step 4 — send welcome email with login instructions.
        emailService.sendStaffWelcome(normalisedEmail, name)

        return StaffUserResponse(
            id        = user.id!!,
            name      = user.name,
            email     = user.email,
            phone     = user.phone,
            role      = user.role.name,
            status    = user.status.name,
            createdAt = user.createdAt,
        )
    }

    /**
     * Suspends or reactivates a staff account.
     *
     *  - Non-STAFF id (CLIENT, ADMIN) or unknown id → 404 STAFF_NOT_FOUND
     *  - Requested status equals current status     → 422 INVALID_STATUS_TRANSITION
     *  - Suspending                                 → revokes all active refresh tokens first
     *  - Success                                    → saves updated status, returns [UpdatedStatusResponse]
     */
    fun updateStatus(id: UUID, status: String): UpdatedStatusResponse {
        // Step 1 — find by id only when role = STAFF; CLIENT/ADMIN ids are treated as not found.
        val user = userRepository.findByIdAndRole(id, UserRole.STAFF)
            ?: throw PlatformException(
                "STAFF_NOT_FOUND",
                "No staff account found for the given id",
                HttpStatus.NOT_FOUND,
            )

        // Step 2 — reject no-op transitions with a descriptive message.
        val targetStatus = UserStatus.valueOf(status)
        if (user.status == targetStatus) {
            val message = when (targetStatus) {
                UserStatus.SUSPENDED -> "Account is already suspended"
                UserStatus.ACTIVE    -> "Account is already active"
                else                 -> "Account is already in the requested status"
            }
            throw PlatformException("INVALID_STATUS_TRANSITION", message, HttpStatus.UNPROCESSABLE_ENTITY)
        }

        // Step 3 — revoke all active refresh tokens immediately when suspending.
        if (targetStatus == UserStatus.SUSPENDED) {
            refreshTokenRepository.revokeAllActiveByUserId(id, Instant.now())
        }

        // Step 4 — persist the new status.
        user.status = targetStatus
        val savedUser = userRepository.save(user)

        return UpdatedStatusResponse(
            id        = savedUser.id!!,
            status    = savedUser.status.name,
            updatedAt = savedUser.updatedAt,
        )
    }
}

