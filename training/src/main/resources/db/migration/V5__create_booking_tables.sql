-- V5: Create bookings table
-- Includes columns for E3-US3 (confirm/reject note) and E3-US5 (training complete) upfront

CREATE TABLE bookings (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id              UUID        NOT NULL REFERENCES batches(id),
    client_id             UUID        NOT NULL REFERENCES users(id),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    note                  TEXT,
    training_complete     BOOLEAN     NOT NULL DEFAULT FALSE,
    training_completed_at TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT bookings_status_check
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT bookings_unique_client_batch
        UNIQUE (client_id, batch_id)
);

CREATE INDEX idx_bookings_batch_id  ON bookings(batch_id);
CREATE INDEX idx_bookings_client_id ON bookings(client_id);
CREATE INDEX idx_bookings_status    ON bookings(status);

