package com.greene.core.user.service

import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import com.greene.core.auth.domain.UserStatus
import com.greene.core.auth.repository.UserRepository
import com.greene.core.exception.PlatformException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Optional
import java.util.UUID

class UserServiceTest {

    private val userRepository: UserRepository = mockk()

    private lateinit var userService: UserService

    private val targetId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private val callerId  = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private val now       = Instant.parse("2026-04-15T10:00:00Z")

    @BeforeEach
    fun setUp() {
        userService = UserService(userRepository)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUser(
        id: UUID    = targetId,
        role: UserRole = UserRole.CLIENT,
    ) = UserEntity(
        id            = id,
        email         = "priya@greene.in",
        name          = "Priya Sharma",
        phone         = "+919876543210",
        role          = role,
        status        = UserStatus.ACTIVE,
        phoneVerified = false,
        createdAt     = now,
        updatedAt     = now,
    )

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `changeRole - happy path - returns ChangeRoleResponse with updated role`() {
        val user = buildUser(role = UserRole.CLIENT)
        every { userRepository.findById(targetId) } returns Optional.of(user)
        every { userRepository.save(any()) } returns user

        val result = userService.changeRole(targetId, "STAFF", callerId)

        assertEquals(targetId,        result.id)
        assertEquals("STAFF",         result.role)
        assertEquals("Priya Sharma",  result.name)
        assertEquals("priya@greene.in", result.email)
    }

    @Test
    fun `changeRole - saves UserEntity with the requested role before returning`() {
        val user = buildUser(role = UserRole.CLIENT)
        every { userRepository.findById(targetId) } returns Optional.of(user)
        every { userRepository.save(any()) } returns user

        userService.changeRole(targetId, "ADMIN", callerId)

        // Verify save was called with the role already mutated to ADMIN
        verify(exactly = 1) { userRepository.save(match { it.role == UserRole.ADMIN }) }
    }

    // ── Error: user not found ─────────────────────────────────────────────────

    @Test
    fun `changeRole - unknown targetUserId - throws USER_NOT_FOUND 404`() {
        every { userRepository.findById(targetId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            userService.changeRole(targetId, "STAFF", callerId)
        }

        assertEquals("USER_NOT_FOUND",    ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
        assertEquals("No user found for the given id", ex.message)
    }

    // ── Error: SUPER_ADMIN assignment ─────────────────────────────────────────

    @Test
    fun `changeRole - requestedRole is SUPER_ADMIN - throws INVALID_ROLE_CHANGE 422`() {
        val user = buildUser()
        every { userRepository.findById(targetId) } returns Optional.of(user)

        val ex = assertThrows<PlatformException> {
            userService.changeRole(targetId, "SUPER_ADMIN", callerId)
        }

        assertEquals("INVALID_ROLE_CHANGE",              ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,    ex.httpStatus)
        assertEquals("SUPER_ADMIN role cannot be assigned", ex.message)
    }

    // ── Error: self-change ────────────────────────────────────────────────────

    @Test
    fun `changeRole - targetUserId equals callerId - throws INVALID_ROLE_CHANGE 422`() {
        val user = buildUser(id = callerId)
        every { userRepository.findById(callerId) } returns Optional.of(user)

        val ex = assertThrows<PlatformException> {
            userService.changeRole(callerId, "STAFF", callerId)
        }

        assertEquals("INVALID_ROLE_CHANGE",           ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("You cannot change your own role", ex.message)
    }

    // ── Validation ordering ───────────────────────────────────────────────────

    @Test
    fun `changeRole - USER_NOT_FOUND is checked before SUPER_ADMIN guard`() {
        every { userRepository.findById(targetId) } returns Optional.empty()

        // Even though requestedRole == SUPER_ADMIN, USER_NOT_FOUND must fire first
        val ex = assertThrows<PlatformException> {
            userService.changeRole(targetId, "SUPER_ADMIN", callerId)
        }

        assertEquals("USER_NOT_FOUND", ex.code)
    }

    @Test
    fun `changeRole - SUPER_ADMIN guard is checked before self-change guard`() {
        // targetUserId == callerId AND requestedRole == SUPER_ADMIN
        val user = buildUser(id = callerId)
        every { userRepository.findById(callerId) } returns Optional.of(user)

        // SUPER_ADMIN check must fire before the self-change check
        val ex = assertThrows<PlatformException> {
            userService.changeRole(callerId, "SUPER_ADMIN", callerId)
        }

        assertEquals("INVALID_ROLE_CHANGE", ex.code)
        assertEquals("SUPER_ADMIN role cannot be assigned", ex.message)
    }
}

