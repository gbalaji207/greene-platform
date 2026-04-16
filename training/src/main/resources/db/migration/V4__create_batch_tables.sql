-- ============================================================
-- Migration: V4__create_batch_tables.sql
-- Story:     E3-US1 Admin Creates a Batch
-- Module:    :training
-- ============================================================

-- ------------------------------------------------------------
-- Table: batches
-- Stores training batch records managed by ADMIN / STAFF.
-- status values:   DRAFT | OPEN | CLOSED | COMPLETED
-- training_status: NULL (not started) | COMPLETED
-- ------------------------------------------------------------
CREATE TABLE batches (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(2000),
    start_date_time TIMESTAMPTZ     NOT NULL,
    end_date_time   TIMESTAMPTZ,
    location        VARCHAR(500),
    topics          VARCHAR(500),
    max_seats       INT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    training_status VARCHAR(20),
    created_by      UUID            NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

