# Deploying JobStar: Render + Supabase

## Architecture and Status

One Render Docker Web Service serves the React frontend and Spring Boot API at
the same HTTPS address. Supabase provides PostgreSQL and the private `resumes`
bucket. No separate static host or custom domain is required. Local development
continues to use Vite on port 5173 and Spring Boot on port 8080.

The Docker build sets `VITE_API_ROOT=/api` and bundles React into the JAR.
Page/assets are public; protected API endpoints still require authentication,
and mutations require CSRF tokens. Production sessions use Secure, HttpOnly,
SameSite=Lax cookies. Deployment is complete only after live verification.

## 1. Prepare Supabase

Use the existing project with your resume bucket. JobStar keeps its own Spring
Security accounts; it does not use Supabase Auth.

1. Open the **Data API** integration/settings and turn **Enable Data API** off.
   JobStar uses JDBC, not Supabase's generated REST/GraphQL table endpoints.
   Disabling these prevents a second API from exposing application data without
   JobStar ownership checks. Keep Storage/S3 enabled.
   See [Supabase API security](https://supabase.com/docs/guides/api/securing-your-api).
2. Click **Connect**, select **Session pooler**, and copy the actual host, port,
   database, and username. Use port **5432**, not transaction mode on 6543.
   Session mode supports IPv4 and prepared statements.
   See [Supabase connections](https://supabase.com/docs/guides/database/connecting-to-postgres).
3. Build the password-free JDBC URL using those parameters:

   ```text
   jdbc:postgresql://YOUR-SESSION-POOLER-HOST:5432/postgres?sslmode=require
   ```

4. Copy the pooled username exactly, normally `postgres.YOUR-PROJECT-REF`.
   The database password is the password chosen when creating the project,
   not an API key or S3 secret. Enter it as a separate Render variable, without
   URL encoding.
5. Confirm the `resumes` bucket is private, its size limit is 10 MB, and the
   allowed MIME types are `application/pdf`, `application/msword`, and
   `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
6. Keep the existing S3 endpoint, region, access key, and secret key. These are
   separate from the database credentials and belong only on the backend.

`sslmode=require` encrypts database traffic but does not verify server identity.
For verified TLS, mount the Supabase database CA certificate as a Render secret
file and use `sslmode=verify-full&sslrootcert=/etc/secrets/YOUR-CERTIFICATE-FILE`.
Verify that configuration against the chosen endpoint before relying on it.

On an empty database, Hibernate creates JobStar's tables at startup. The current
release uses `ddl-auto=update`, not versioned schema migrations. Back up before
future schema changes. Do not modify Supabase's internal schemas.

Local accounts/applications are not copied automatically. Keep local `.env`
pointed at local PostgreSQL. Start production with fresh accounts and uploads
unless a separate data migration is planned. The existing bucket can contain
local development uploads; do not delete objects that local records still use.

## 2. Prepare the Repository

Run the frontend build and backend tests against the dedicated local test database:

```powershell
# From frontend
npm run build
# From backend
.\mvnw.cmd clean test
```

Commit the Docker/configuration/security changes, tests, and documentation.
Never commit `.env`, keys, database dumps, or personal resumes.
`.dockerignore` also excludes local secrets from the container build context.

Push the deployment feature branch for the first Render verification. After
successful verification and PR merge, change Render's deployment branch to
`main` before deleting the feature branch.

## 3. Create the Render Service

Choose **New > Web Service**, connect the GitHub repository, and configure:

| Setting | Value |
| --- | --- |
| Repository | Your JobStar repository |
| Branch | Pushed deployment branch for initial verification |
| Runtime / Language | Docker |
| Root Directory | Empty (repository root) |
| Dockerfile Path | `./Dockerfile` |
| Docker Build Context | Repository root (`.`), if shown |
| Docker Command | Empty; use the Dockerfile entrypoint |
| Instance Type | Free |
| Region | Prefer a region near Supabase |
| Health Check Path | `/api/health` |
| Auto Deploy | Disabled initially |

Do not create a separate static site or Render PostgreSQL instance. Docker
builds with Node 22 and Java 21; compilation still targets Java 17. The final
container runs as a non-root user, respects Render's `PORT`, and limits Java
heap allocation. See [Render Docker deployment](https://render.com/docs/docker).

## 4. Render Environment Variables

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | Session-pooler JDBC URL from step 1 |
| `DB_USERNAME` | Exact pooled username |
| `DB_PASSWORD` | Supabase database password |
| `APP_CORS_ALLOWED_ORIGIN` | Actual Render HTTPS site origin; no path or trailing slash |
| `SESSION_COOKIE_SAME_SITE` | `Lax` |
| `SUPABASE_STORAGE_ENDPOINT` | Existing S3 endpoint |
| `SUPABASE_STORAGE_REGION` | Region shown in S3 settings |
| `SUPABASE_STORAGE_ACCESS_KEY` | Existing S3 access key |
| `SUPABASE_STORAGE_SECRET_KEY` | Existing S3 secret key |
| `SUPABASE_STORAGE_BUCKET` | `resumes` |

Copy Render's actual `onrender.com` address rather than guessing it from the
service name. If it appears only after service creation, update the CORS origin
and redeploy before testing accounts. Do not upload your local `.env`.
`VITE_API_ROOT=/api` is set during the Docker build; do not configure it in
Render. Secure session cookies are forced by `prod`. The production connection
pool is limited to three database connections.

## 5. Live Verification

1. Deploy and inspect logs for successful frontend/JAR builds, database
   connection, and Spring Boot startup on Render's port.
2. Open `https://YOUR-ACTUAL-SITE.onrender.com/api/health`; expect
   `JobStar backend is running!`. This checks server responsiveness, not
   comprehensive ongoing database/storage health.
3. Open the site's root. Verify styles/scripts load while signed out, and
   browser API requests use this site's `/api`, never localhost.
4. Register, log out, log in, and refresh. Inspect the session cookie for Secure,
   HttpOnly, and SameSite=Lax.
5. Test application create/edit/search/filter/delete, plus interviews, contacts,
   reminders, and descriptions.
6. Upload a small disposable PDF, view it, edit label/notes, select it for two
   applications, then unlink and delete it.
7. In a second account/private window, verify another user's application and
   resume IDs cannot be read, edited, assigned, downloaded, or deleted.
   An unauthenticated browser must not open an uploaded file URL.
8. Restart Render. Data/files must persist; in-memory sessions end, so sign in again.
9. Remove test records and record the deployed URL, commit, date, and results
   in the PR. Mark deployment complete only after these checks pass.

## Troubleshooting

- Database failure: check session host, port 5432, pooled username, password,
  TLS settings, and whether Supabase is paused.
- Missing configuration: enter the variable in Render and redeploy. Production
  deliberately has no local database/CORS fallback.
- Login failure: check HTTPS, relative `/api` requests, and exact CORS origin.
  Keep CSRF protection enabled.
- Upload failure: check bucket, S3 credentials, endpoint, region, MIME types,
  and Storage quotas.
- Large upload rejected: the 10 MB multipart request limit includes form
  overhead, so test with files slightly below 10 MB.
- Slow first visit: Render's free service can sleep; allow time to start.
- Memory restart: inspect Render metrics/logs. Heap limits help but do not
  guarantee the whole Java process fits the free instance.

## Backups and Free-Tier Operation

Render's filesystem is disposable; persistent data/files live in Supabase.
Free Render services can sleep and free Supabase projects can pause. Check
[Render's free limits](https://render.com/docs/free) and
[Supabase pricing](https://supabase.com/pricing) before launch.

Back up both JobStar database tables (including IDs and storage keys) and the
corresponding bucket files with their object paths. A database backup does not
include resume file contents. Keep backups private, outside Git, and test a
restore to a separate project. Back up before schema changes and periodically
when adding meaningful data. Waking the service is not a backup.

## Deployment Record

- Live URL: pending
- Deployed commit: pending
- Supabase database connection verified: pending
- Private file access verified: pending
- Production smoke test: pending
- Backup/restore exercise: pending
