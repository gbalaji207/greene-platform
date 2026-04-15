package com.greene.core.auth.repository

import com.greene.core.auth.domain.UserEntity
import com.greene.core.auth.domain.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {

    fun findByEmail(email: String): UserEntity?

    fun findByPhone(phone: String): UserEntity?

    fun existsByEmail(email: String): Boolean

    fun existsByPhone(phone: String): Boolean

    /** True when [phone] belongs to any account whose email is NOT [email] (i.e. a different user). */
    fun existsByPhoneAndEmailNot(phone: String, email: String): Boolean

    /**
     * Looks up a user by primary key only when their role matches [role].
     * Used by staff management endpoints so that a CLIENT or ADMIN id is treated
     * as not found rather than leaking cross-role information.
     */
    fun findByIdAndRole(id: UUID, role: UserRole): UserEntity?
}

