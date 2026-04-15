-- ============================================================
-- Migration: V3__add_profile_photo_url.sql
-- Story:     E2-US5 Client Profile Management
-- Module:    :core (profile)
-- ============================================================

-- Storage key for the user's profile photo (e.g. profiles/{userId}/{uuid}.jpg).
-- NULL means no photo has been uploaded yet.
-- The application layer derives the pre-signed URL from this key on every read.
ALTER TABLE users ADD COLUMN profile_photo_url VARCHAR(500) NULL;

