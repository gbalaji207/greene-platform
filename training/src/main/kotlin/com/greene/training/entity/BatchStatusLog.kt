package com.greene.training.entity

import com.greene.training.domain.BatchStatus
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Maps the `batch_status_logs` table.
 *
 * Records every successful status transition made via
 * `PATCH /api/v1/batches/{id}/status` for audit purposes.
 *
 * [changedAt] is managed by Hibernate via [@CreationTimestamp] — no manual assignment needed.
 * [batchId] and [changedBy] are stored as plain UUIDs (no @ManyToOne joins).
 */
@Entity
@Table(name = "batch_status_logs")
class BatchStatusLog(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    var id: UUID? = null,

    /** FK → batches.id — stored as plain UUID, no join. */
    @Column(name = "batch_id", nullable = false, updatable = false)
    var batchId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 20)
    var fromStatus: BatchStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    var toStatus: BatchStatus,

    /** FK → users.id — populated from the JWT principal. */
    @Column(name = "changed_by", nullable = false, updatable = false)
    var changedBy: UUID,

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    var changedAt: OffsetDateTime = OffsetDateTime.now(),
)

