-- ==============================================================================
-- Non-Destructive Database Migration: PostgreSQL 14+
-- File: database/migration-postgres-verified-death.sql
-- Compatible with PostgreSQL 14+ (Render PostgreSQL compatible)
-- Safely applies schema updates for Verified Death Confirmation & Asset Release
-- Preserves all existing data in users, trusted_contacts, vault_assets, etc.
-- ==============================================================================

-- 1. Ensure verification_token_expires_at column exists in trusted_contacts
ALTER TABLE trusted_contacts ADD COLUMN IF NOT EXISTS verification_token_expires_at TIMESTAMP NULL;

-- 2. Safely update inactivity_status check constraint on users
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_user_inactivity_status;
ALTER TABLE users ADD CONSTRAINT chk_user_inactivity_status 
    CHECK (inactivity_status IN ('ACTIVE', 'WARNING_GRACE_PERIOD', 'INACTIVE_PENDING_VERIFICATION', 'DEATH_VERIFIED', 'SETTLED'));

-- 3. Safely update status check constraint on grace_period_reminders
ALTER TABLE grace_period_reminders DROP CONSTRAINT IF EXISTS chk_reminder_status;
ALTER TABLE grace_period_reminders ADD CONSTRAINT chk_reminder_status 
    CHECK (status IN ('SENT', 'SIMULATED', 'FAILED', 'ACKNOWLEDGED_BY_USER', 'EXPIRED_WITHOUT_RESPONSE'));

-- 4. Create death_claims table if not already existing
CREATE TABLE IF NOT EXISTS death_claims (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    claimant_email VARCHAR(255) NOT NULL,
    claim_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_OWNER_CHALLENGE',

    -- Supporting evidence document metadata
    evidence_file_name VARCHAR(255) NULL,
    evidence_file_type VARCHAR(100) NULL,
    evidence_file_size BIGINT NULL,
    evidence_storage_path VARCHAR(500) NULL,
    evidence_notes TEXT NULL,

    -- Mandatory owner protection and challenge window
    challenge_window_expires_at TIMESTAMP NOT NULL,

    -- Authorized reviewer evaluation
    review_notes TEXT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_claim_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_claim_contact
        FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_claim_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT chk_claim_status
        CHECK (claim_status IN ('PENDING_OWNER_CHALLENGE', 'PENDING_MANUAL_REVIEW', 'APPROVED_DEATH_VERIFIED', 'REJECTED', 'VETOED_BY_OWNER'))
);

CREATE INDEX IF NOT EXISTS idx_claim_user ON death_claims (user_id);
CREATE INDEX IF NOT EXISTS idx_claim_contact ON death_claims (contact_id);
CREATE INDEX IF NOT EXISTS idx_claim_status ON death_claims (claim_status);

-- 5. Create asset_releases table if not already existing
CREATE TABLE IF NOT EXISTS asset_releases (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    permission_level VARCHAR(50) NOT NULL DEFAULT 'VIEW_ONLY',
    release_status VARCHAR(50) NOT NULL DEFAULT 'RELEASED',
    access_token_hash VARCHAR(255) NULL,
    access_token_expires_at TIMESTAMP NULL,
    released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_release_claim
        FOREIGN KEY (claim_id) REFERENCES death_claims(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_release_asset
        FOREIGN KEY (asset_id) REFERENCES vault_assets(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_release_contact
        FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT uk_release_asset_contact
        UNIQUE (asset_id, contact_id),

    CONSTRAINT chk_release_status
        CHECK (release_status IN ('RELEASED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_release_claim ON asset_releases (claim_id);
CREATE INDEX IF NOT EXISTS idx_release_asset ON asset_releases (asset_id);
CREATE INDEX IF NOT EXISTS idx_release_contact ON asset_releases (contact_id);
CREATE INDEX IF NOT EXISTS idx_release_status ON asset_releases (release_status);
