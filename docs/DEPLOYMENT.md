# Deployment Guide

## Purpose

This guide prepares JobStar for a production deployment. It does not include real credentials, hosting accounts, or a live deployment URL.

## Planned Architecture

JobStar will use four separate services:

- A static host for the React frontend.
- A Java-compatible host for the Spring Boot API.
- A managed PostgreSQL database for application data and user accounts.
- Private S3-compatible object storage for resume uploads in the next feature branch.

The frontend and API should ultimately use subdomains of the same custom domain, such as `app.example.com` and `api.example.com`. This keeps authenticated session cookies first-party while the backend CORS policy allows only the frontend origin.

## Environment Files

Copy each `.env.example` file to `.env` for local development. The real `.env` files are ignored by Git and must never be committed.

### Frontend

| Variable | Purpose | Local value |
| --- | --- | --- |
| `VITE_API_ROOT` | Complete API base URL, including `/api` | `http://localhost:8080/api` |

Vite embeds this value during the production build. It is public configuration, not a secret.

### Backend

| Variable | Purpose |
| --- | --- |
| `DB_URL` | PostgreSQL JDBC connection URL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password; keep secret |
| `APP_CORS_ALLOWED_ORIGIN` | Exact frontend origin allowed to make credentialed API requests |
| `SESSION_COOKIE_SECURE` | `false` locally; production always forces `true` |
| `SESSION_COOKIE_SAME_SITE` | `Lax` locally; use `None` only when the frontend and API must be cross-site |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` for a live backend deployment |

The `prod` profile intentionally has no database or CORS fallbacks. A deployment with missing values fails instead of accidentally using local settings.

## Security Rules

- Never commit `.env` files, database URLs containing credentials, or storage access keys.
- Keep the CORS allowed origin exact. Do not use `*` when requests include cookies.
- Production sessions are `HttpOnly` and `Secure`; HTTPS is required.
- Keep CSRF protection enabled. The frontend must continue fetching the CSRF token before changing data.
- Use a private object-storage bucket for resumes. The backend, not the browser, will authorize future uploads and downloads.

## Provider Requirements

The specific host will be selected during the live deployment, but it must support Java 17, environment variables, HTTPS, PostgreSQL, and custom domains. Avoid treating temporary free databases as permanent storage: [Render documents](https://render.com/docs/free) that its free PostgreSQL instances expire after 30 days and do not include backups. [Cloudflare R2](https://developers.cloudflare.com/r2/pricing/) remains a suitable candidate for the later resume-upload feature because it offers S3-compatible private object storage and a monthly included-use tier.

## Deployment Order

1. Provision a managed PostgreSQL database and create production-only credentials.
2. Deploy the Spring Boot backend with `SPRING_PROFILES_ACTIVE=prod` and the backend variables above.
3. Confirm `GET /api/health` returns successfully over HTTPS.
4. Build and deploy the frontend with `VITE_API_ROOT` set to the deployed backend URL.
5. Set `APP_CORS_ALLOWED_ORIGIN` to the final frontend URL, redeploy the backend, and verify register, login, logout, and a protected application request.
6. Connect custom subdomains before enabling real users or resume uploads.
7. Add private object storage and resume upload support in the next branch.

## Pre-Launch Checklist

- Frontend production build succeeds.
- Backend tests pass.
- The production health endpoint responds over HTTPS.
- Registration, login, logout, and session persistence work from the deployed frontend.
- Requests from an unapproved origin are rejected by CORS.
- No secrets appear in Git history, build logs, or browser-delivered frontend variables.
- Database backups and provider retention policies are understood before personal data is added.
