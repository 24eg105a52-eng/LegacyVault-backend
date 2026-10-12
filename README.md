# Digital Legacy Vault (LegacyVault)
**Secure Digital Asset Access & Inheritance Management System**

LegacyVault is a production-conscious full-stack platform engineered to allow individuals to securely store critical digital assets, assign beneficiaries, define conditional access rules, and execute safe, verified digital inheritance protocols.

---

## 🛠️ Technology Stack

- **Frontend:** React 18, Vite, React Router v6, Axios, Vanilla CSS (Modern Dark Mode UI).
- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security, Spring Data JPA, Hibernate 6, Jakarta Mail.
- **Databases:**
  - **Local Development:** MySQL 8.0
  - **Cloud Deployment:** PostgreSQL 14+ (Render PostgreSQL)
- **Cryptography & Security:**
  - Password Hashing: BCrypt
  - User Authentication: Stateless HMAC-SHA256 JWT
  - Vault Document Encryption: AES-256-GCM authenticated symmetric encryption
  - Contact Verification: Cryptographically secure random tokens (SHA-256 hashed at rest)
- **Deployment Targets:**
  - Frontend: Vercel Single Page App
  - Backend: Render Web Service (Docker containerized)

---

## 📁 Repository & Directory Structure

LegacyVault is divided into two separate Git repositories:

```text
D:\LegacyVault(TT)/
├── frontend/             # Frontend repository (LegacyVault-frontend)
│   ├── src/
│   │   ├── components/   # Navbar, Footer
│   │   ├── pages/        # Dashboard, Vault, Contacts, EmergencyAccess, BeneficiaryVault, VerifyContact, Profile
│   │   └── services/     # Axios API client with automatic JWT token attachment
│   ├── package.json
│   ├── vercel.json       # SPA routing rewrite configuration for Vercel
│   └── vite.config.js
├── backend/              # Backend repository (LegacyVault-backend)
│   ├── database/         # SQL Schemas and DB-specific migration scripts
│   │   ├── schema.sql                         # Full MySQL 8.0 schema
│   │   ├── schema-postgres.sql                # Full PostgreSQL 14+ schema
│   │   ├── migration-mysql-verified-death.sql    # Non-destructive MySQL 8.0 migration
│   │   └── migration-postgres-verified-death.sql # Non-destructive PostgreSQL migration
│   ├── src/main/java/com/legacyvault/
│   │   ├── config/       # SecurityConfig, DatabaseConfig, WebMvcConfig
│   │   ├── controller/   # Auth, User, Vault, Contact, Policy, DeathClaim, Beneficiary, Health
│   │   ├── model/        # JPA Entities (User, VaultAsset, TrustedContact, AccessPolicy, DeathClaim, AssetRelease, etc.)
│   │   ├── repository/   # Spring Data JPA Repositories
│   │   ├── security/     # JwtUtil, JwtAuthenticationFilter, EncryptionService
│   │   └── service/      # DeathVerification, AssetRelease, Policy, Contact, Vault, FileStorage, Notification
│   ├── Dockerfile        # Multi-stage production build for Java 21 Temurin
│   ├── render.yaml       # Blueprint configuration for Render web service & PostgreSQL
│   └── pom.xml
└── database/             # Root copy of database schemas and migrations
```

---

## 🛡️ Core Capabilities & Security Model

### 1. Account Management & Server-Side Authorization
- Registrations are assigned strictly `ROLE_USER`. Role escalation via self-service APIs is blocked.
- Reviewer actions (`/api/claims/review-queue`, `/api/claims/{id}/review`) require `ROLE_ADMIN` or `ROLE_LEGAL_VERIFIER`.
- Every API endpoint strictly validates caller identity via JWT `AuthenticationPrincipal` — no user can access another user's vault items, policies, claims, or contacts.

### 2. Digital Vault & File Encryption
- Vault documents and evidence files are encrypted at rest with **AES-256-GCM** authenticated encryption using secure, unique random IVs per file.
- Path traversal defenses prevent arbitrary filesystem manipulation.
- Assets marked `VIEW_ONLY` permit viewing decrypted metadata/notes but forbid raw attachment downloads (enforced server-side with `403 Forbidden`).

### 3. Trusted Contacts & Single-Use Verification
- Contacts are invited with cryptographically unpredictable 32-byte tokens (`SecureRandom`).
- Only the SHA-256 hash is persisted in the database with a 7-day expiration timestamp.
- Verification tokens are strictly single-use; replay attacks or reuse of expired tokens are rejected.

### 4. Heartbeat Inactivity Watchdog
- Users maintain a configurable check-in threshold (default 30 days) and grace window (default 14 days).
- Inactivity notifications track accurate delivery status (`SENT`, `SIMULATED`, `FAILED`) — unconfigured SMTP is never reported as `SENT`.
- **CRITICAL SECURITY INVARIANT:** Missed check-ins or expired grace periods **NEVER** release digital assets or prove death. They transition the user to `INACTIVE_PENDING_VERIFICATION`, requiring formal verification.

### 5. Death Verification & 7-Day Veto Window
- Death claims require supporting certified documentation (death certificate, court order) encrypted upon upload.
- **Mandatory 7-Day Veto Window:** Owners receive immediate alerts and can veto false claims with one click (`/api/claims/{id}/veto`), resetting account standing.
- **Veto Enforcement:** The veto window is enforced both at reviewer evaluation time AND at the asset release execution point.
- **Reviewer Conflict of Interest:** Claimants cannot evaluate their own claims; owners cannot review claims against themselves. Written justification notes are mandatory.

### 6. Beneficiary Vault
- Assets are released only after an approved claim (`DEATH_VERIFIED`) and only for explicit matching owner access policies.
- Verified beneficiaries access decrypted records and authorized downloads via `/beneficiary-vault`.

---

## ⚙️ Local Installation & Run Instructions (Windows)

### Prerequisites
- Java 21 JDK (Eclipse Temurin 21 recommended)
- Maven 3.9+
- Node.js 18+ and npm
- MySQL 8.0+ running on port 3306

### 1. Database Setup (MySQL)
Open MySQL Workbench or MySQL CLI:
```sql
CREATE DATABASE IF NOT EXISTS legacy_vault CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
For a fresh install, execute `backend/database/schema.sql`.  
For an existing database with users, execute `backend/database/migration-mysql-verified-death.sql`.

### 2. Backend Startup
In a PowerShell window:
```powershell
cd D:\LegacyVault(TT)\backend
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
mvn spring-boot:run
```
Backend starts at `http://localhost:8080`. Health check: `http://localhost:8080/api/health`.

### 3. Frontend Startup
In a separate command window:
```cmd
cd D:\LegacyVault(TT)\frontend
npm install
npm run dev
```
Frontend starts at `http://localhost:5173`.

---

## 🗄️ Database Setup & Migration Guide

### Fresh Database Setup
- **MySQL 8.0 (Local):** Run `backend/database/schema.sql`.
- **PostgreSQL 14+ (Render / Production):** Run `backend/database/schema-postgres.sql`.

### Upgrading an Existing Database (Non-Destructive)
- **MySQL 8.0:**
  Execute `backend/database/migration-mysql-verified-death.sql`.
  Updates ENUMs safely and creates `death_claims` and `asset_releases` tables without data loss.
- **PostgreSQL 14+:**
  Execute `backend/database/migration-postgres-verified-death.sql`.
  Applies `ALTER TABLE ... ADD CONSTRAINT` and creates `death_claims` and `asset_releases` using `BIGSERIAL`.

---

## 🧪 Testing Commands & Test Coverage

### Backend Test Suite
```cmd
cd D:\LegacyVault(TT)\backend
mvn clean test
```
**Results:** **55 tests passed, 0 failures, 0 errors** (`BUILD SUCCESS`).  
Covers:
- Authentication & JWT token security
- AES-256-GCM encryption and IV uniqueness
- Contact single-use token verification, expiration, and replay prevention
- Inactivity expiration segregation (`INACTIVE_PENDING_VERIFICATION != DEATH_VERIFIED`)
- Death claim evidence requirement and AES-256-GCM evidence upload
- Owner veto protection and account restoration
- Reviewer authorization, mandatory notes, and conflict-of-interest defense
- 7-day challenge window enforcement at claim review and release execution point
- `VIEW_ONLY` download restriction (`403 Forbidden`)
- Cross-user isolation and duplicate release prevention
- PostgreSQL dialect compatibility

### Frontend Production Build
```cmd
cd D:\LegacyVault(TT)\frontend
npm run build
```
**Results:** `vite build` succeeded with 0 errors (`99 modules transformed`, bundle in `dist/`).

---

## 🚀 Cloud Deployment Guide

### Backend: Render Web Service
1. Link GitHub repository `LegacyVault-backend` to Render.
2. Select **Docker** environment.
3. Configure environment variables in the Render dashboard:
   - `DATABASE_URL`: Connection URL of your Render PostgreSQL instance.
   - `JWT_SECRET`: Random 64+ char secret string.
   - `VAULT_ENCRYPTION_SECRET`: Master encryption key for AES-256-GCM.
   - `CORS_ALLOWED_ORIGINS`: Your Vercel frontend URL (e.g. `https://legacyvault-frontend.vercel.app`).
   - `VAULT_STORAGE_DIR`: Path to persistent disk (e.g. `/var/data/vault_files`).
4. Set Health Check path to `/api/health`.

### Frontend: Vercel
1. Link GitHub repository `LegacyVault-frontend` to Vercel.
2. Framework Preset: **Vite**.
3. Set environment variable:
   - `VITE_API_BASE_URL`: `https://your-backend-service.onrender.com/api`
4. Deploy. Client-side routes are automatically handled by `vercel.json`.

---

## ⚠️ Known Limitations & College Project Notes

1. **Human Death Verification:**
   Death claims are reviewed manually by designated system operators (`ROLE_ADMIN` or `ROLE_LEGAL_VERIFIER`) inspecting uploaded certified documentation. LegacyVault **does not integrate with government birth/death registry APIs** (such as national civil registration databases), as these require government-level institutional partnerships.
2. **Email Delivery in Local Development:**
   When `MAIL_USERNAME` and `MAIL_PASSWORD` are not configured in local development, email delivery is safely simulated and logged to the server console. It is never falsely reported as delivered.
3. **Persistent File Storage in Cloud:**
   On containerized platforms (like Render Free Tier), local disk storage is ephemeral unless an external volume or persistent disk is attached. For permanent cloud storage, attach a Render Disk to `VAULT_STORAGE_DIR` or configure cloud object storage (e.g. AWS S3).
