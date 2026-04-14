package com.greene.core.auth.repository

import com.greene.core.auth.domain.OtpPurpose
import com.greene.core.auth.domain.OtpTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface OtpTokenRepository : JpaRepository<OtpTokenEntity, UUID> {

    /**
     * Returns the single active OTP for a user+purpose combination.
     * Active = not yet used and not yet invalidated.
     * The partial index `idx_otp_tokens_active` in the DB makes this lookup O(1).
     */
    @Query(
        """
        SELECT o FROM OtpTokenEntity o
        WHERE o.userId   = :userId
          AND o.purpose  = :purpose
          AND o.usedAt   IS NULL
          AND o.invalidatedAt IS NULL
        """
    )
    fun findActiveByUserIdAndPurpose(
        @Param("userId")  userId:  UUID,
        @Param("purpose") purpose: OtpPurpose,
    ): OtpTokenEntity?

    /**
     * Returns the most recently created OTP for a user+purpose regardless of state.
     * Used during OTP verification to distinguish OTP_ALREADY_USED (usedAt != null)
     * from a genuinely missing / never-created OTP.
     */
    fun findFirstByUserIdAndPurposeOrderByCreatedAtDesc(
        userId:  UUID,
        purpose: OtpPurpose,
    ): OtpTokenEntity?

    /**
     * Bulk-invalidates every active OTP for a given user+purpose.
     * Called before inserting a newly generated OTP so only one active OTP
     * exists per (userId, purpose) pair at any time.
     *
     * Returns the number of rows updated.
     */
    @Modifying
    @Transactional
    @Query(
        """
        UPDATE OtpTokenEntity o
        SET    o.invalidatedAt = :now
        WHERE  o.userId   = :userId
          AND  o.purpose  = :purpose
          AND  o.usedAt   IS NULL
          AND  o.invalidatedAt IS NULL
        """
    )
    fun invalidateAllActiveByUserIdAndPurpose(
        @Param("userId")  userId:  UUID,
        @Param("purpose") purpose: OtpPurpose,
        @Param("now")     now:     Instant,
    ): Int
}

