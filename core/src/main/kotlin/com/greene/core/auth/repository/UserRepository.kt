package com.greene.core.auth.repository

import com.greene.core.auth.domain.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {

    fun findByEmail(email: String): UserEntity?

    fun findByPhone(phone: String): UserEntity?

    fun existsByEmail(email: String): Boolean

    fun existsByPhone(phone: String): Boolean

    /** True when [phone] belongs to any account whose email is NOT [email] (i.e. a different user). */
    fun existsByPhoneAndEmailNot(phone: String, email: String): Boolean
}

