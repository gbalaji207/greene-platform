package com.greene.training.entity

import com.greene.training.domain.BatchStatus
import com.greene.training.domain.TrainingStatus
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Maps the `batches` table.
 *
 * The `kotlin-plugin-jpa` compiler plugin generates a synthetic no-arg constructor
 * so that Hibernate can instantiate entities via reflection — application code
 * always uses the explicit constructor below.
 *
 * [createdAt] and [updatedAt] are managed by Hibernate via [@CreationTimestamp]
 * and [@UpdateTimestamp] respectively; no manual assignment is needed.
 */
@Entity
@Table(name = "batches")
class Batch(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(length = 2000)
    var description: String? = null,

    @Column(name = "start_date_time", nullable = false)
    var startDateTime: OffsetDateTime,

    @Column(name = "end_date_time")
    var endDateTime: OffsetDateTime? = null,

    @Column(length = 500)
    var location: String? = null,

    @Column(length = 500)
    var topics: String? = null,

    @Column(name = "max_seats")
    var maxSeats: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BatchStatus = BatchStatus.DRAFT,

    @Enumerated(EnumType.STRING)
    @Column(name = "training_status", length = 20)
    var trainingStatus: TrainingStatus? = null,

    /** FK → users.id — populated from the JWT principal, never from the request body. */
    @Column(name = "created_by", nullable = false, updatable = false)
    var createdBy: UUID,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

