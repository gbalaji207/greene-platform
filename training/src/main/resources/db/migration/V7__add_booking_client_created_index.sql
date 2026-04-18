-- V7: Covering index for GET /api/v1/bookings/me
-- Supports efficient lookup of a client's bookings ordered by creation time

CREATE INDEX idx_bookings_client_id_created_at
    ON bookings(client_id, created_at DESC);

