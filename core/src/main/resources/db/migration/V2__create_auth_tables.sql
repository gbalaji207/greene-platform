-- ============================================================
-- Migration: V2__create_auth_tables.sql
-- Story:     E2-US1 Client Self-Registration
-- Module:    :core (auth)
-- ============================================================

-- ------------------------------------------------------------
-- Table: users
-- Stores all platform users across all roles.
-- Role-specific data lives in separate profile tables.
-- ------------------------------------------------------------
CREATE TABLE users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identity
    email               VARCHAR(255)    NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    phone               VARCHAR(20)     NOT NULL,

    -- Role & Status
    role                VARCHAR(20)     NOT NULL DEFAULT 'CLIENT',
                                        -- Allowed: CLIENT, STAFF, ADMIN, SUPER_ADMIN
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING_VERIFICATION',
                                        -- Allowed: PENDING_VERIFICATION, ACTIVE, SUSPENDED

    -- Phone verification flag (SMS OTP deferred — flag is forward-looking plumbing)
    phone_verified      BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_login_at       TIMESTAMPTZ     NULL
);

-- Unique constraints
ALTER TABLE users ADD CONSTRAINT uq_users_email  UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT uq_users_phone  UNIQUE (phone);

-- Check constraints
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('CLIENT', 'STAFF', 'ADMIN', 'SUPER_ADMIN'));

ALTER TABLE users ADD CONSTRAINT chk_users_status
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED'));

-- Indexes
CREATE INDEX idx_users_email  ON users (email);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_role   ON users (role);


-- ------------------------------------------------------------
-- Table: otp_tokens
-- Stores OTP records for email verification and login.
-- One active OTP per email at a time.
-- Old OTPs are invalidated (not deleted) when a new one is generated.
-- ------------------------------------------------------------
CREATE TABLE otp_tokens (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Linked user (nullable — OTP may be generated before user record exists... 
    -- but in our flow user record always exists at OTP generation time)
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- OTP value stored as bcrypt hash (never stored in plaintext)
    otp_hash            VARCHAR(255)    NOT NULL,

    -- Purpose of this OTP
    purpose             VARCHAR(30)     NOT NULL DEFAULT 'EMAIL_VERIFICATION',
                                        -- Allowed: EMAIL_VERIFICATION, LOGIN, PASSWORD_RESET

    -- Lifecycle
    expires_at          TIMESTAMPTZ     NOT NULL,
    used_at             TIMESTAMPTZ     NULL,       -- Set when OTP is consumed successfully
    invalidated_at      TIMESTAMPTZ     NULL,       -- Set when superseded by a new OTP

    -- Resend tracking
    resend_count        SMALLINT        NOT NULL DEFAULT 0,
    last_resent_at      TIMESTAMPTZ     NULL,

    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Check constraints
ALTER TABLE otp_tokens ADD CONSTRAINT chk_otp_purpose
    CHECK (purpose IN ('EMAIL_VERIFICATION', 'LOGIN', 'PASSWORD_RESET'));

-- Indexes
CREATE INDEX idx_otp_tokens_user_id   ON otp_tokens (user_id);
CREATE INDEX idx_otp_tokens_purpose   ON otp_tokens (purpose);
-- Partial index: fast lookup of active (non-expired, non-used, non-invalidated) OTPs
CREATE INDEX idx_otp_tokens_active    ON otp_tokens (user_id, purpose)
    WHERE used_at IS NULL AND invalidated_at IS NULL;


-- ------------------------------------------------------------
-- Table: refresh_tokens
-- Stores hashed refresh tokens for authenticated sessions.
-- Rotated on every use (old token invalidated, new one issued).
-- ------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Token stored as SHA-256 hash (never in plaintext)
    token_hash          VARCHAR(255)    NOT NULL,

    expires_at          TIMESTAMPTZ     NOT NULL,
    revoked_at          TIMESTAMPTZ     NULL,       -- Set on logout, password reset, or rotation

    -- Device/session hint (optional, for future session management)
    user_agent          VARCHAR(500)    NULL,
    ip_address          INET            NULL,

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ     NULL
);

-- Unique constraint on token hash
ALTER TABLE refresh_tokens ADD CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash);

-- Indexes
CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens (user_id);
-- Partial index: fast lookup of valid (non-expired, non-revoked) tokens
CREATE INDEX idx_refresh_tokens_active     ON refresh_tokens (user_id)
    WHERE revoked_at IS NULL;


-- ------------------------------------------------------------
-- Trigger: auto-update users.updated_at
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- Design Notes
-- ============================================================
--
-- 1. OTP storage:
--    otp_hash stores a bcrypt hash of the 6-digit OTP.
--    Plaintext OTP is never persisted. Comparison is done by
--    hashing the submitted value and comparing against otp_hash.
--
-- 2. One active OTP per user per purpose:
--    When a new OTP is generated, the service layer sets
--    invalidated_at = now() on all existing active OTPs for
--    that user + purpose before inserting the new record.
--    This is enforced in service logic, not by a DB constraint,
--    to allow atomic invalidate + insert.
--
-- 3. phone_verified:
--    Stored as FALSE for all registrations in Phase 1.
--    SMS OTP verification is deferred. The column is present
--    as forward-looking plumbing for Phase 2 or later.
--
-- 4. Refresh token rotation:
--    On each token refresh, the old refresh_tokens row has
--    revoked_at set, and a new row is inserted.
--    All rows for a user are revoked on logout or password reset.
--
-- 5. No hard deletes on otp_tokens or refresh_tokens:
--    Records are invalidated/revoked by setting the relevant
--    timestamp column. This preserves an audit trail.
--    A periodic cleanup job can archive or purge rows older
--    than a configured retention window (future concern).
--
-- 6. PENDING_VERIFICATION records:
--    Never deleted. If a user re-registers with the same email,
--    the service layer updates the existing PENDING_VERIFICATION
--    record (name, phone) and generates a fresh OTP.
--    Records with no activity simply remain — no TTL cleanup
--    is implemented in Phase 1.
-- ============================================================
