package com.greene.training.entity

import com.greene.training.domain.BookingStatus
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Maps the `bookings` table.
 *
 * [batchId] and [clientId] are stored as plain UUID columns — no @ManyToOne
 * associations to avoid lazy loading issues.
 *
 * [createdAt] and [updatedAt] are managed by Hibernate via [@CreationTimestamp]
 * and [@UpdateTimestamp] respectively.
 */
@Entity
@Table(name = "bookings")
class Booking(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    var id: UUID? = null,

    /** FK → batches.id — stored as plain UUID, no join. */
    @Column(name = "batch_id", nullable = false, updatable = false)
    var batchId: UUID,

    /** FK → users.id — populated from the JWT principal. */
    @Column(name = "client_id", nullable = false, updatable = false)
    var clientId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BookingStatus = BookingStatus.PENDING,

    /** Staff note added on confirm / reject (E3-US3). */
    @Column(columnDefinition = "TEXT")
    var note: String? = null,

    /** Set to true when training is marked complete (E3-US5). */
    @Column(name = "training_complete", nullable = false)
    var trainingComplete: Boolean = false,

    /** Timestamp when training was marked complete (E3-US5). */
    @Column(name = "training_completed_at")
    var trainingCompletedAt: OffsetDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

