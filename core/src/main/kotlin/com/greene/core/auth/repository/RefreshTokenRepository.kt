package com.greene.core.auth.repository

import com.greene.core.auth.domain.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * Looks up a token by its SHA-256 hash.
     * Called on every token-refresh request; the unique index on `token_hash`
     * guarantees O(1) lookup.
     */
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    /**
     * Revokes every non-revoked refresh token belonging to a user.
     * Used on logout and (in a future story) on password reset.
     *
     * Returns the number of rows updated.
     */
    @Modifying
    @Transactional
    @Query(
        """
        UPDATE RefreshTokenEntity r
        SET    r.revokedAt = :now
        WHERE  r.userId     = :userId
          AND  r.revokedAt IS NULL
        """
    )
    fun revokeAllActiveByUserId(
        @Param("userId") userId: UUID,
        @Param("now")    now:    Instant,
    ): Int
}

