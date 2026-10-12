-- ==============================================================================
-- Project: Digital Legacy Vault (LegacyVault)
-- Target DBMS: PostgreSQL 14+ (Render PostgreSQL compatible)
-- File: database/schema-postgres.sql
-- Description: Complete Relational Schema for Secure Digital Asset Access,
--              Beneficiary Management, Inactivity Protocols, and Audit Logging.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. USERS TABLE
-- Stores registered vault owners, password hashes, and inactivity configurations.
-- Password hash: Stored strictly as BCrypt hashes (never plaintext).
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(30) NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Inactivity & Dead-Man Switch Preferences
    inactivity_threshold_days INTEGER NOT NULL DEFAULT 30,
    grace_period_days INTEGER NOT NULL DEFAULT 14,
    emergency_delay_hours INTEGER NOT NULL DEFAULT 168,
    last_heartbeat_at TIMESTAMP NULL,
    inactivity_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_user_inactivity_status 
        CHECK (inactivity_status IN ('ACTIVE', 'WARNING_GRACE_PERIOD', 'INACTIVE_PENDING_VERIFICATION', 'DEATH_VERIFIED', 'SETTLED'))
);

CREATE INDEX IF NOT EXISTS idx_users_inactivity_status ON users (inactivity_status);
CREATE INDEX IF NOT EXISTS idx_users_last_heartbeat ON users (last_heartbeat_at);

-- ------------------------------------------------------------------------------
-- 2. TRUSTED CONTACTS TABLE
-- Designated individuals who can be assigned assets or request emergency access.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trusted_contacts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(30) NULL,
    relationship VARCHAR(100) NOT NULL,
    role_type VARCHAR(50) NOT NULL DEFAULT 'BENEFICIARY',
    verification_status VARCHAR(50) NOT NULL DEFAULT 'INVITED',
    verification_token_hash VARCHAR(255) NULL,
    verification_token_expires_at TIMESTAMP NULL,
    verified_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_contacts_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT uk_user_contact_email UNIQUE (user_id, email),
    CONSTRAINT chk_contact_role_type 
        CHECK (role_type IN ('BENEFICIARY', 'EMERGENCY_DEPUTY', 'LEGAL_VERIFIER')),
    CONSTRAINT chk_contact_verification_status 
        CHECK (verification_status IN ('INVITED', 'PENDING_VERIFICATION', 'VERIFIED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_contacts_user ON trusted_contacts (user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_email ON trusted_contacts (email);
CREATE INDEX IF NOT EXISTS idx_contacts_status ON trusted_contacts (verification_status);

-- ------------------------------------------------------------------------------
-- 3. VAULT ASSETS TABLE
-- Encrypted digital legacy assets (documents, directives, personal instructions).
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vault_assets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'DOCUMENT',
    description TEXT NULL,
    encrypted_content TEXT NULL,
    encryption_iv VARCHAR(64) NULL,

    -- Optional attached document file metadata
    file_name VARCHAR(255) NULL,
    file_type VARCHAR(100) NULL,
    file_size_bytes BIGINT NULL,
    storage_path VARCHAR(500) NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_assets_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_asset_category 
        CHECK (category IN ('DOCUMENT', 'PERSONAL_INSTRUCTION', 'FINANCIAL_DIRECTIVE', 'SUBSCRIPTION_INVENTORY', 'HARDWARE_RECOVERY_KEY'))
);

CREATE INDEX IF NOT EXISTS idx_assets_user ON vault_assets (user_id);
CREATE INDEX IF NOT EXISTS idx_assets_category ON vault_assets (category);

-- ------------------------------------------------------------------------------
-- 4. ACCESS POLICIES TABLE
-- Connects specific assets with specific trusted contacts and defines release rules.
-- Assets are NEVER released automatically on a missed check-in alone.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS access_policies (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    permission_level VARCHAR(50) NOT NULL DEFAULT 'VIEW_ONLY',
    release_trigger VARCHAR(50) NOT NULL DEFAULT 'VERIFIED_INACTIVITY_EXPIRY',
    requires_legal_verification BOOLEAN NOT NULL DEFAULT FALSE,
    is_authorized BOOLEAN NOT NULL DEFAULT FALSE,
    authorized_at TIMESTAMP NULL,
    authorization_notes TEXT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_policy_asset
        FOREIGN KEY (asset_id) REFERENCES vault_assets(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_policy_contact
        FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT uk_policy_asset_contact UNIQUE (asset_id, contact_id),
    CONSTRAINT chk_policy_permission_level 
        CHECK (permission_level IN ('VIEW_ONLY', 'FULL_TRANSFER', 'EXECUTOR_DEPUTY')),
    CONSTRAINT chk_policy_release_trigger 
        CHECK (release_trigger IN ('VERIFIED_INACTIVITY_EXPIRY', 'APPROVED_EMERGENCY_CLAIM', 'LEGAL_CONFIRMATION_REQUIRED'))
);

CREATE INDEX IF NOT EXISTS idx_policy_contact ON access_policies (contact_id);
CREATE INDEX IF NOT EXISTS idx_policy_authorized ON access_policies (is_authorized);

-- ------------------------------------------------------------------------------
-- 5. CHECK-IN LOGS TABLE
-- Records every heartbeat or login confirmation from the vault owner.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS check_in_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    check_in_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL_PORTAL_PING',
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_checkin_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_checkin_type 
        CHECK (check_in_type IN ('MANUAL_PORTAL_PING', 'SESSION_LOGIN', 'EMAIL_CONFIRMATION_LINK'))
);

CREATE INDEX IF NOT EXISTS idx_checkin_user_date ON check_in_logs (user_id, recorded_at);

-- ------------------------------------------------------------------------------
-- 6. GRACE PERIOD REMINDERS TABLE
-- Tracks multi-step warnings sent to owner before any policy evaluation.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grace_period_reminders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reminder_number INTEGER NOT NULL,
    sent_to_email VARCHAR(255) NOT NULL,
    grace_period_expires_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SENT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reminders_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_reminder_status 
        CHECK (status IN ('SENT', 'SIMULATED', 'FAILED', 'ACKNOWLEDGED_BY_USER', 'EXPIRED_WITHOUT_RESPONSE'))
);

CREATE INDEX IF NOT EXISTS idx_reminders_user ON grace_period_reminders (user_id);
CREATE INDEX IF NOT EXISTS idx_reminders_status ON grace_period_reminders (status);

-- ------------------------------------------------------------------------------
-- 7. EMERGENCY ACCESS REQUESTS TABLE
-- Handles time-delayed emergency requests initiated by trusted contacts/deputies.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS emergency_access_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    reason TEXT NOT NULL,
    request_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_OWNER_REVIEW',
    veto_window_expires_at TIMESTAMP NOT NULL,
    temporary_token_hash VARCHAR(255) NULL,
    access_granted_at TIMESTAMP NULL,
    access_expires_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_emergency_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_emergency_contact
        FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_emergency_request_status 
        CHECK (request_status IN ('PENDING_OWNER_REVIEW', 'APPROVED_BY_OWNER', 'VETOED_BY_OWNER', 'RELEASED_POST_GRACE_PERIOD', 'REVOKED_EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_emergency_user ON emergency_access_requests (user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_contact ON emergency_access_requests (contact_id);
CREATE INDEX IF NOT EXISTS idx_emergency_status ON emergency_access_requests (request_status);

-- ------------------------------------------------------------------------------
-- 8. AUDIT LOGS TABLE
-- Immutable security audit trail recording all critical actions.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NULL,
    actor_type VARCHAR(50) NOT NULL,
    actor_identifier VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id VARCHAR(100) NULL,
    details TEXT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT chk_audit_actor_type 
        CHECK (actor_type IN ('VAULT_OWNER', 'BENEFICIARY', 'SYSTEM_SCHEDULER', 'ADMIN'))
);

CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_logs (created_at);

-- ------------------------------------------------------------------------------
-- 9. DEATH CLAIMS TABLE
-- Strict verification and evidence review workflow for verified death confirmation.
-- Inactivity alone or grace period expiration is NEVER proof of death.
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 10. ASSET RELEASES TABLE
-- Tracks explicit digital asset releases authorized exclusively after death verification.
-- ------------------------------------------------------------------------------
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

