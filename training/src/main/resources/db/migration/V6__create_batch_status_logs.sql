-- V6: Create batch_status_logs table for audit trail of batch status transitions

CREATE TABLE batch_status_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id     UUID NOT NULL REFERENCES batches(id),
    from_status  VARCHAR(20) NOT NULL,
    to_status    VARCHAR(20) NOT NULL,
    changed_by   UUID NOT NULL REFERENCES users(id),
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_batch_status_logs_batch_id  ON batch_status_logs(batch_id);
CREATE INDEX idx_batch_status_logs_changed_at ON batch_status_logs(changed_at);

