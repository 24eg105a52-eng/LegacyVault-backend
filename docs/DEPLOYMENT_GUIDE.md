# LegacyVault Production Deployment Guide
**Target Stack:** Vercel (Frontend) + Render Web Service (Backend, Java 21) + Render PostgreSQL (Database)

---

## Architecture Overview

```
                      ┌───────────────────────────────────────┐
                      │            Vercel Frontend            │
                      │  https://<your-project>.vercel.app    │
                      └──────────────────┬────────────────────┘
                                         │ HTTPS / REST (JWT)
                                         ▼
                      ┌───────────────────────────────────────┐
                      │       Render Backend Web Service      │
                      │  https://<service-name>.onrender.com  │
                      └──────────┬─────────────────┬──────────┘
                                 │                 │
                JDBC / TLS (5432)│                 │ Persistent Disk Mount
                                 ▼                 ▼
             ┌─────────────────────────┐     ┌────────────────────────┐
             │    Render PostgreSQL    │     │   Render Disk Mount    │
             │      (Free / Starter)   │     │  /var/data/vault_files │
             │  (8 Tables + CHECKs)    │     │  (AES-256-GCM Files)   │
             └─────────────────────────┘     └────────────────────────┘
```

---

## Phase 1: Push Code to GitHub

1. Ensure all code is committed:
   ```bash
   git add .
   git commit -m "feat: configure cloud deployment for Render and Vercel"
   ```
2. Create a new **private** or **public** repository on GitHub (e.g., `legacyvault`).
3. Push your repository:
   ```bash
   git remote add origin https://github.com/<your-username>/legacyvault.git
   git branch -M main
   git push -u origin main
   ```

> [!NOTE]
> `.gitignore` already ensures `.env`, local uploaded files (`storage/`), and compiled outputs are never committed to GitHub.

---

## Phase 2: Create Render PostgreSQL Database

1. Log in to [Render Dashboard](https://dashboard.render.com).
2. Click **New +** → **PostgreSQL**.
3. Configure the database details:
   - **Name:** `legacyvault-postgres`
   - **Database Name:** `legacy_vault`
   - **User:** `legacyvault`
   - **Region:** Choose the region nearest to you (e.g., *Oregon (US West)* or *Frankfurt (EU)*).
   - **Plan:** *Free*
4. Click **Create Database**.
5. When created, Render will display connection strings:
   - Note down the **Internal Database URL** (e.g. `postgres://legacyvault:...@dpg-...:5432/legacy_vault`) if deploying the backend in the same Render region, OR the **External Database URL**.
6. **Initialize the Schema**:
   - In Render dashboard, click on your database → open the **Connect** tab or **Shell / PSQL** tab.
   - Alternatively, connect via pgAdmin, DBeaver, or command line using the External Connection String.
   - Run the contents of [`database/schema-postgres.sql`](file:///d:/LegacyVault(TT)/database/schema-postgres.sql).
   - This creates all 8 tables with proper foreign keys, `BIGSERIAL` primary keys, and ENUM `CHECK` constraints safely.

---

## Phase 3: Deploy Spring Boot Backend to Render

### Option A: Using Render Docker (Recommended)
The project includes a multi-stage `backend/Dockerfile` using Eclipse Temurin Java 21 and Maven.

1. In Render Dashboard, click **New +** → **Web Service**.
2. Connect your GitHub repository.
3. Configure settings:
   - **Name:** `legacyvault-backend`
   - **Region:** Same region as your PostgreSQL database.
   - **Language / Runtime:** **Docker**
   - **Dockerfile Path:** `backend/Dockerfile`
   - **Health Check Path:** `/api/health`
4. Add **Environment Variables**:
   | Variable | Value / Description |
   | :--- | :--- |
   | `DATABASE_URL` | Paste your Render PostgreSQL Database URL (Render internal or external URL). The backend automatically translates this to JDBC format. |
   | `JWT_SECRET` | Generate a 64+ char key (e.g., run `openssl rand -hex 32` or a secure random 64-char string). |
   | `VAULT_ENCRYPTION_SECRET` | Master encryption secret for AES-256-GCM vault assets. *Store this safely — if changed, previously encrypted items cannot be decrypted.* |
   | `CORS_ALLOWED_ORIGINS` | `https://*.vercel.app,http://localhost:5173` *(Update with your exact Vercel production domain once deployed)* |
   | `PORT` | `8080` *(Render sets this automatically, but you can declare 8080)* |
   | `VAULT_STORAGE_DIR` | `/var/data/vault_files` |
5. *(Optional for Starter/Paid plans)* **Persistent Disk**:
   - If using Render Starter plan ($7/mo), click **Disks** → **Add Disk**:
     - **Name:** `vault-data`
     - **Mount Path:** `/var/data/vault_files`
     - **Size:** 1 GB+
   - If using Render Free tier, files are stored on the service disk (ephemeral across restarts).
6. Click **Deploy Web Service**.
7. Once deployed, test your health check in a browser:
   `https://<your-service>.onrender.com/api/health` → should return `{"status":"UP", ...}`.

---

## Phase 4: Deploy React Frontend to Vercel

1. Log in to [Vercel Dashboard](https://vercel.com).
2. Click **Add New...** → **Project**.
3. Import your GitHub repository (`legacyvault`).
4. In the project configuration screen:
   - **Framework Preset:** `Vite`
   - **Root Directory:** Click **Edit** and select `frontend`.
   - **Build Command:** `npm run build` (detected automatically)
   - **Output Directory:** `dist` (detected automatically)
5. In **Environment Variables**, add:
   | Variable | Value |
   | :--- | :--- |
   | `VITE_API_BASE_URL` | `https://<your-service>.onrender.com/api` |
6. Click **Deploy**.
7. Vercel will build and assign a domain (e.g., `https://legacyvault-frontend.vercel.app`).
8. Deep link routing is automatically handled by [`frontend/vercel.json`](file:///d:/LegacyVault(TT)/frontend/vercel.json).

---

## Phase 5: Final CORS Handshake

1. Return to your Render Web Service dashboard → **Environment**.
2. Update `CORS_ALLOWED_ORIGINS` to include your exact Vercel URL:
   `https://<your-app>.vercel.app,https://*.vercel.app,http://localhost:5173`
3. Save changes (Render will automatically redeploy with the updated CORS policy).

---

## Phase 6: Post-Deployment Smoke Test

Perform the following checks on your live Vercel website:

1. **User Registration:**
   - Navigate to `/register`.
   - Register a new account (e.g., `testowner@example.com`).
   - Verify immediate successful account creation.
2. **User Login:**
   - Navigate to `/login`.
   - Log in with credentials.
   - Verify JWT token is stored and redirects to `/dashboard`.
3. **Empty States:**
   - Verify Dashboard shows 0 vault assets and 0 contacts for this new user.
4. **Digital Vault Item:**
   - Navigate to `/vault`.
   - Create a text credential or directive.
   - Verify it appears in the list and can be edited.
5. **Document Upload & Download:**
   - Upload a test PDF, TXT, or image file.
   - Confirm upload succeeds and file details are shown.
   - Click Download to verify AES-256-GCM decryption in browser.
6. **Profile & Password Update:**
   - Navigate to `/profile`.
   - Verify user details are populated from the backend.
7. **Logout:**
   - Click Sign Out and verify session clears.

---

## Preserving Local Development (MySQL)

Your local development environment remains 100% intact:
- If no `DATABASE_URL` or `DB_HOST` is supplied in the environment, the backend automatically connects to your local MySQL database (`localhost:3306/legacy_vault`) using the existing credentials in `backend/src/main/resources/application.properties`.
- Frontend automatically points to `http://localhost:8080/api` when `VITE_API_BASE_URL` is omitted.
