-- ==============================================================================
-- Non-Destructive Database Migration: MySQL 8.0+
-- File: database/migration-mysql-verified-death.sql
-- Compatible with MySQL 8.0+
-- Safely applies schema updates for Verified Death Confirmation & Asset Release
-- Preserves all existing data in users, trusted_contacts, vault_assets, etc.
-- ==============================================================================

USE legacy_vault;

-- 1. Ensure verification_token_expires_at column exists in trusted_contacts
SET @dbname = DATABASE();
SET @tablename = 'trusted_contacts';
SET @columnname = 'verification_token_expires_at';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  'ALTER TABLE trusted_contacts ADD COLUMN verification_token_expires_at TIMESTAMP NULL AFTER verification_token_hash;'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 2. Update inactivity_status ENUM on users to support DEATH_VERIFIED and INACTIVE_PENDING_VERIFICATION
ALTER TABLE users MODIFY COLUMN inactivity_status ENUM('ACTIVE', 'WARNING_GRACE_PERIOD', 'INACTIVE_PENDING_VERIFICATION', 'DEATH_VERIFIED', 'SETTLED') NOT NULL DEFAULT 'ACTIVE';

-- 3. Update status ENUM on grace_period_reminders to support SIMULATED and FAILED delivery tracking
ALTER TABLE grace_period_reminders MODIFY COLUMN status ENUM('SENT', 'SIMULATED', 'FAILED', 'ACKNOWLEDGED_BY_USER', 'EXPIRED_WITHOUT_RESPONSE') NOT NULL DEFAULT 'SENT';

-- 4. Create death_claims table if not already existing
CREATE TABLE IF NOT EXISTS death_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'Vault owner being claimed deceased',
    contact_id BIGINT NOT NULL COMMENT 'Designated trusted contact submitting claim',
    claimant_email VARCHAR(255) NOT NULL,
    claim_status ENUM('PENDING_OWNER_CHALLENGE', 'PENDING_MANUAL_REVIEW', 'APPROVED_DEATH_VERIFIED', 'REJECTED', 'VETOED_BY_OWNER')
        NOT NULL DEFAULT 'PENDING_OWNER_CHALLENGE',
    evidence_file_name VARCHAR(255) NULL,
    evidence_file_type VARCHAR(100) NULL,
    evidence_file_size BIGINT NULL,
    evidence_storage_path VARCHAR(500) NULL,
    evidence_notes TEXT NULL COMMENT 'Official certificate registration number, issuing authority, jurisdiction',
    challenge_window_expires_at TIMESTAMP NOT NULL COMMENT 'Owner veto window deadline',
    review_notes TEXT NULL COMMENT 'Written justification documenting verification decision',
    reviewed_by_user_id BIGINT NULL COMMENT 'Authorized administrator/verifier user ID',
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_claim_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_claim_contact
        FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_claim_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_claim_user (user_id),
    INDEX idx_claim_contact (contact_id),
    INDEX idx_claim_status (claim_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Create asset_releases table if not already existing
CREATE TABLE IF NOT EXISTS asset_releases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    permission_level ENUM('VIEW_ONLY', 'FULL_TRANSFER') NOT NULL DEFAULT 'VIEW_ONLY',
    release_status ENUM('RELEASED', 'REVOKED') NOT NULL DEFAULT 'RELEASED',
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
    INDEX idx_release_claim (claim_id),
    INDEX idx_release_asset (asset_id),
    INDEX idx_release_contact (contact_id),
    INDEX idx_release_status (release_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
