# JobStar

**Your guide through the job search.**

JobStar is a full-stack application for organizing job applications, interviews,
contacts, follow-ups, and resumes. It is developed alongside a real job search,
with features shaped by everyday application workflows.

**[Open JobStar](https://jobstar-7fu8.onrender.com/)**

The free hosting service may need time to wake after inactivity.

## Tech Stack

- React, TypeScript, and Vite
- Java, Spring Boot, Spring Security, and Spring Data JPA
- PostgreSQL hosted on Supabase
- Private Supabase Storage through its S3-compatible API
- Docker and Render for production deployment
- Python and PostgreSQL CLI tools for backup/recovery operations

## Current Features

- Private accounts with registration, login, logout, password hashing, and session authentication
- Ownership checks and CSRF protection for account-specific data
- Application creation, editing, deletion, and dedicated detail panels
- Application statuses, dates, locations, job links, notes, and stored job descriptions
- Company/position search, status filtering, and sorting
- Dashboard totals, pipeline breakdown, and upcoming/overdue follow-ups
- Interview tracking and recruiter/contact management
- Notes with preserved paragraphs, bullet points, and tab spacing
- Account-level Resume Library with labels, tailoring notes, and external document links
- Private PDF, DOC, and DOCX uploads, including drag and drop
- One selected resume per application, with reuse across multiple applications
- Resume editing and linked-application visibility, plus confirmation when unlinking/deleting
- Loading/retry states and branded resume-access error pages
- Responsive interface and distinct sign-in/registration forms

## Architecture

In production, a multi-stage Docker build bundles React into the Spring Boot JAR.
One Render Web Service serves the frontend and API from the same HTTPS origin.
Frontend requests use `/api`, allowing secure session cookies without a separate
frontend host or custom domain.

Spring Boot accesses Supabase PostgreSQL through JDBC and authorizes private
resume-file requests before accessing the S3-compatible bucket. Database and
storage credentials stay on the backend. Supabase Auth and the generated Data API
are not used by JobStar.

Local development keeps Vite and Spring Boot separate, with a local PostgreSQL
database. See [the deployment guide](docs/DEPLOYMENT.md) for production settings.

## Local Development

Prerequisites: Node.js 22, Java 17 or newer, and PostgreSQL. The production Docker
images use Java 21; the project compiles to Java 17.

1. Create local databases named `jobstar` and `jobstar_test`. The test database
   must remain separate because integration tests recreate its schema.
2. Create local `.env` files from the examples in `backend` and `frontend`.
   Configure your local database password and browser origin in `backend/.env`.
   Review `backend/src/test/resources/application-test.properties` for the
   dedicated test connection. Never put production credentials in the test profile.
3. Start the backend from `backend`:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

4. Start the frontend from `frontend`:

   ```powershell
   npm ci
   npm run dev
   ```

5. Open `http://localhost:5173` and register a local account.

Private file uploads require the Supabase S3 variables documented in
`backend/.env.example`. Use a separate development bucket when testing file
deletion; sharing the production bucket can affect live files. Other workflows
and external resume links do not require uploading a file.

## Verification

From `frontend`:

```powershell
npm run lint
npm run build
```

From `backend`, with the dedicated local test database available:

```powershell
.\mvnw.cmd clean verify
```

The backend suite includes authentication, application workflows, ownership,
resume metadata protection, and browser file-access errors. The Docker build
packages the app with tests skipped; run verification before merging deployment
changes. Automated CI gating is not configured by this backup/documentation work.

## Backup and Recovery

The backup helper exports only JobStar's application tables and the private
resume files those records reference. Backups stay outside Git. A local recovery
drill restores into a new database and checks table contents, ID sequences, file
hashes, and resume references.

The first production backup and isolated local data-recovery drill passed on
2026-10-20. This does not constitute a replacement-bucket or restored-app login
test. Follow [the backup and recovery guide](docs/BACKUP_AND_RECOVERY.md) for
setup, commands, limitations, and verification evidence.

## Development Status and Roadmap

The full-stack foundation, account ownership, application workflows, interviews,
contacts, follow-ups, job descriptions, resume library, and initial production
deployment are implemented. Deployment documentation and backup/recovery work
complete the current portfolio-quality operational follow-up.

Next: **Job Import Assistant**. Supported publicly accessible job URLs will be
used to suggest available company, position, location, compensation, employment
type, and description information. Users must review suggestions before saving.
When URL extraction is unavailable or unreliable, the planned fallback is
pasting the job description. This feature is planned, not implemented.

Later ideas include application analytics, job-skill analysis, skill-gap insights,
and interview preparation. Their scope will be decided in later feature work.
