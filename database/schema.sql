-- ==============================================================================
-- Project: Digital Legacy Vault (LegacyVault)
-- Target DBMS: MySQL 8.0+
-- File: database/schema.sql
-- Description: Complete Relational Schema for Secure Digital Asset Access,
--              Beneficiary Management, Inactivity Protocols, and Audit Logging.
-- ==============================================================================

-- Create Database with modern UTF-8 support
CREATE DATABASE IF NOT EXISTS legacy_vault
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE legacy_vault;

-- ------------------------------------------------------------------------------
-- Clean Drop Tables (Ordered to respect Foreign Key Constraints)
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS emergency_access_requests;
DROP TABLE IF EXISTS grace_period_reminders;
DROP TABLE IF EXISTS check_in_logs;
DROP TABLE IF EXISTS access_policies;
DROP TABLE IF EXISTS vault_assets;
DROP TABLE IF EXISTS trusted_contacts;
DROP TABLE IF EXISTS users;

-- ==============================================================================
-- 1. USERS TABLE
-- Stores registered vault owners, password hashes, and inactivity configurations.
-- Password hash: Stored strictly as BCrypt/Argon2 hashes (never plaintext).
-- ==============================================================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(30) NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER' COMMENT 'ROLE_USER or ROLE_ADMIN',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Inactivity & Dead-Man Switch Preferences
    inactivity_threshold_days INT NOT NULL DEFAULT 30 COMMENT 'Days without activity before grace period triggers',
    grace_period_days INT NOT NULL DEFAULT 14 COMMENT 'Warning duration before policies can be evaluated',
    emergency_delay_hours INT NOT NULL DEFAULT 168 COMMENT 'Owner veto window (168h = 7 days)',
    last_heartbeat_at TIMESTAMP NULL COMMENT 'Updated whenever user logs in or sends manual heartbeat ping',
    inactivity_status ENUM('ACTIVE', 'WARNING_GRACE_PERIOD', 'INACTIVE_PENDING_VERIFICATION', 'SETTLED')
        NOT NULL DEFAULT 'ACTIVE' COMMENT 'State of the user account in the inheritance lifecycle',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_users_inactivity_status (inactivity_status),
    INDEX idx_users_last_heartbeat (last_heartbeat_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 2. TRUSTED CONTACTS TABLE
-- Designated individuals who can be assigned assets or request emergency access.
-- Contacts do not see assets immediately; authorization is conditional.
-- ==============================================================================
CREATE TABLE trusted_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'The vault owner who designated this contact',
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(30) NULL,
    relationship VARCHAR(100) NOT NULL COMMENT 'e.g. Spouse, Child, Sibling, Attorney, Trustee',
    role_type ENUM('BENEFICIARY', 'EMERGENCY_DEPUTY', 'LEGAL_VERIFIER')
        NOT NULL DEFAULT 'BENEFICIARY' COMMENT 'Role definition in inheritance process',
    verification_status ENUM('INVITED', 'PENDING_VERIFICATION', 'VERIFIED', 'REVOKED')
        NOT NULL DEFAULT 'INVITED' COMMENT 'Two-factor or identity confirmation status',
    verification_token_hash VARCHAR(255) NULL COMMENT 'Hash of confirmation token sent via secure channel',
    verified_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_contacts_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Prevent duplicate contact entries with same email under one user
    UNIQUE KEY uk_user_contact_email (user_id, email),
    INDEX idx_contacts_user (user_id),
    INDEX idx_contacts_email (email),
    INDEX idx_contacts_status (verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 3. VAULT ASSETS TABLE
-- Encrypted digital legacy assets (documents, directives, personal instructions).
-- Excludes plaintext third-party account passwords.
-- ==============================================================================
CREATE TABLE vault_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'Owner of the asset',
    title VARCHAR(200) NOT NULL,
    category ENUM('DOCUMENT', 'PERSONAL_INSTRUCTION', 'FINANCIAL_DIRECTIVE', 'SUBSCRIPTION_INVENTORY', 'HARDWARE_RECOVERY_KEY')
        NOT NULL DEFAULT 'DOCUMENT',
    description TEXT NULL COMMENT 'Unencrypted high-level descriptor for indexing',
    encrypted_content LONGTEXT NULL COMMENT 'AES-256-GCM encrypted payload / text content',
    encryption_iv VARCHAR(64) NULL COMMENT 'Hex or Base64 IV used for AES-GCM encryption',

    -- Optional attached document file metadata
    file_name VARCHAR(255) NULL,
    file_type VARCHAR(100) NULL,
    file_size_bytes BIGINT NULL,
    storage_path VARCHAR(500) NULL COMMENT 'Encrypted file location on local secure disk',

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_assets_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_assets_user (user_id),
    INDEX idx_assets_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 4. ACCESS POLICIES TABLE
-- Connects specific assets with specific trusted contacts and defines release rules.
-- Assets are NEVER released automatically on a missed check-in alone.
-- ==============================================================================
CREATE TABLE access_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    permission_level ENUM('VIEW_ONLY', 'FULL_TRANSFER', 'EXECUTOR_DEPUTY')
        NOT NULL DEFAULT 'VIEW_ONLY',
    release_trigger ENUM('VERIFIED_INACTIVITY_EXPIRY', 'APPROVED_EMERGENCY_CLAIM', 'LEGAL_CONFIRMATION_REQUIRED')
        NOT NULL DEFAULT 'VERIFIED_INACTIVITY_EXPIRY',
    requires_legal_verification BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Mandates certified proof (e.g. death certificate)',
    is_authorized BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Flipped only after full verification & policy evaluation',
    authorized_at TIMESTAMP NULL,
    authorization_notes TEXT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_policy_asset
        FOREIGN KEY (asset_id) REFERENCES vault_assets(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_policy_contact
        FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- Only one policy per asset-contact pair
    UNIQUE KEY uk_policy_asset_contact (asset_id, contact_id),
    INDEX idx_policy_contact (contact_id),
    INDEX idx_policy_authorized (is_authorized)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 5. CHECK-IN LOGS TABLE
-- Records every heartbeat or login confirmation from the vault owner.
-- ==============================================================================
CREATE TABLE check_in_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    check_in_type ENUM('MANUAL_PORTAL_PING', 'SESSION_LOGIN', 'EMAIL_CONFIRMATION_LINK')
        NOT NULL DEFAULT 'MANUAL_PORTAL_PING',
    ip_address VARCHAR(45) NULL COMMENT 'IPv4 or IPv6',
    user_agent VARCHAR(255) NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_checkin_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_checkin_user_date (user_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 6. GRACE PERIOD REMINDERS TABLE
-- Tracks multi-step warnings sent to owner before any policy evaluation.
-- Part of the non-immediate, safe dead-man switch workflow.
-- ==============================================================================
CREATE TABLE grace_period_reminders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reminder_number INT NOT NULL COMMENT '1 = First Warning, 2 = Midpoint Warning, 3 = Final Notice',
    sent_to_email VARCHAR(255) NOT NULL,
    grace_period_expires_at TIMESTAMP NOT NULL,
    status ENUM('SENT', 'ACKNOWLEDGED_BY_USER', 'EXPIRED_WITHOUT_RESPONSE')
        NOT NULL DEFAULT 'SENT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reminders_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_reminders_user (user_id),
    INDEX idx_reminders_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 7. EMERGENCY ACCESS REQUESTS TABLE
-- Handles time-delayed emergency requests initiated by trusted contacts/deputies.
-- The vault owner has a mandatory delay window (veto window) to reject requests.
-- ==============================================================================
CREATE TABLE emergency_access_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'Vault owner targeted by request',
    contact_id BIGINT NOT NULL COMMENT 'Trusted contact requesting access',
    reason TEXT NOT NULL COMMENT 'Explanation/justification for emergency access claim',
    request_status ENUM('PENDING_OWNER_REVIEW', 'APPROVED_BY_OWNER', 'VETOED_BY_OWNER', 'RELEASED_POST_GRACE_PERIOD', 'REVOKED_EXPIRED')
        NOT NULL DEFAULT 'PENDING_OWNER_REVIEW',
    veto_window_expires_at TIMESTAMP NOT NULL COMMENT 'Time until request auto-approves if unvetoed',
    temporary_token_hash VARCHAR(255) NULL COMMENT 'Single-use cryptographic token for temporary access',
    access_granted_at TIMESTAMP NULL,
    access_expires_at TIMESTAMP NULL COMMENT 'Time when temporary emergency access window closes',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_emergency_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_emergency_contact
        FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_emergency_user (user_id),
    INDEX idx_emergency_contact (contact_id),
    INDEX idx_emergency_status (request_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 8. AUDIT LOGS TABLE
-- Immutable security audit trail recording all critical actions.
-- Includes actors, actions, targets, IP addresses, and timestamps.
-- ==============================================================================
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL COMMENT 'Vault owner associated with action (NULL if actor deleted)',
    actor_type ENUM('VAULT_OWNER', 'BENEFICIARY', 'SYSTEM_SCHEDULER', 'ADMIN') NOT NULL,
    actor_identifier VARCHAR(255) NOT NULL COMMENT 'Email or principal who performed the action',
    action VARCHAR(100) NOT NULL COMMENT 'e.g. AUTH_LOGIN, ASSET_CREATED, POLICY_EVALUATED, EMERGENCY_CLAIM_VETOED',
    resource_type VARCHAR(60) NOT NULL COMMENT 'e.g. USER, VAULT_ASSET, ACCESS_POLICY, EMERGENCY_REQUEST',
    resource_id VARCHAR(100) NULL COMMENT 'ID of the affected resource',
    details TEXT NULL COMMENT 'JSON or descriptive audit metadata',
    ip_address VARCHAR(45) NULL COMMENT 'Client IP address',
    user_agent VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,

    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
