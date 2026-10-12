# LegacyVault — Backend API

RESTful API backend for **LegacyVault**, built with Spring Boot 3, Java 21, Spring Security, and Spring Data JPA.

## Tech Stack
- **Runtime:** Java 21 (Eclipse Temurin)
- **Framework:** Spring Boot 3.3.4
- **Security:** Spring Security (BCrypt + Stateless JWT)
- **Databases Supported:**
  - Local Development: MySQL 8.0
  - Cloud Production: PostgreSQL 14+ (Render PostgreSQL)
- **File Encryption:** AES-256-GCM authenticated server-side encryption
- **Deployment Platform:** Render Web Service (Docker)

## Getting Started (Local Development)

### Prerequisites
- JDK 21 installed and configured
- Apache Maven 3.9+
- MySQL 8.0 running locally on port 3306

### Local Environment Setup
Set your local database credentials (e.g. in your environment or PowerShell session):
```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
```

### Running the Application
```bash
mvn spring-boot:run
```
The server will start at `http://localhost:8080`.

### Running Tests
```bash
mvn test
```

## Cloud Deployment (Render Web Service)
1. Deploy as a **Docker** web service using the included `Dockerfile`.
2. Set Health Check Path to `/api/health`.
3. Provide environment variables:
   - `DATABASE_URL`: Render PostgreSQL connection URL.
   - `JWT_SECRET`: 64+ char random hex string.
   - `VAULT_ENCRYPTION_SECRET`: Master encryption key for AES-256-GCM.
   - `CORS_ALLOWED_ORIGINS`: Comma-separated list of allowed frontend domains (e.g. `https://legacyvault.vercel.app,https://*.vercel.app`).
   - `VAULT_STORAGE_DIR`: Persistent disk mount path (e.g. `/var/data/vault_files`).
4. Full step-by-step instructions: see `docs/DEPLOYMENT_GUIDE.md`.
