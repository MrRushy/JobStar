# JobStar Backup and Recovery

## Scope

The helper in `scripts/backup/jobstar_backup.py` exports JobStar's six application
tables, their owned ID sequences, and every uploaded resume referenced by those
records. It does not export Supabase's internal `auth` or `storage` schemas,
provider credentials, unreferenced bucket objects, or Render configuration.
JobStar accounts/password hashes are in `public.user_accounts`, not Supabase Auth.

Storage files need their own copy: a database export contains references to
files, not the contents. See [Supabase backups](https://supabase.com/docs/guides/platform/backups).
The helper uses PostgreSQL's custom archive format; see
[pg_dump](https://www.postgresql.org/docs/18/app-pgdump.html).

## Prerequisites and Private Configuration

- Python 3.10 or newer and PostgreSQL client tools at least as new as the source
  server's major version. Use the same or a newer major version for local recovery.
- An active Supabase project and working private S3 credentials.
- A running local PostgreSQL server and permission to create a new database.
- No application edits/uploads/deletions during backup. The database and object
  store do not share an atomic snapshot. The helper compares database contents
  before and after copying; changes cause it to reject the backup.

From the repository root in PowerShell:

```powershell
python -m venv .venv-backup
.\.venv-backup\Scripts\python.exe -m pip install -r scripts/backup/requirements.txt
Copy-Item scripts/backup/.env.example backend/.env.backup
```

Only copy the example on first setup; do not overwrite an existing private
configuration. Edit `backend/.env.backup` privately. Set the Supabase database
password and local recovery password. The helper reads the existing S3 settings
from `backend/.env`, then applies overrides from `.env.backup`. The application
itself never reads `.env.backup`. Keep the normal development DB settings intact.

Use the Supabase **Session pooler** host, port 5432, database `postgres`, and exact
pooled username. Do not use the transaction pooler. Keep passwords as separate
values, without URL encoding or surrounding quotes. Remote database backups
require TLS. `require` encrypts traffic; certificate verification requires
additional CA configuration. Keep all credentials local and out of chat/Git.

## Take and Check a Backup

```powershell
.\.venv-backup\Scripts\python.exe scripts/backup/jobstar_backup.py backup
```

The default destination is `%USERPROFILE%\JobStarBackups`, outside the repository.
Each run creates a new timestamped directory. A failed run leaves an `INCOMPLETE`
marker; do not use it for recovery. A completed run has:

- `database.dump`: application data, schema, constraints, and sequence state.
- `files/`: resume contents under hashed local filenames.
- `manifest.json`: original storage keys, content types, file sizes/checksums,
  table counts/content fingerprints, and ID sequence state.
- `COMPLETE`: completion marker.

The manifest preserves original storage paths even though the local file names
are hashed. Keep the whole directory together. Linked external documents are
preserved as URLs; the helper does not download third-party documents.

Recheck a copy after moving it or periodically during storage:

```powershell
.\.venv-backup\Scripts\python.exe scripts/backup/jobstar_backup.py verify-files "C:\Users\YOUR_USER\JobStarBackups\YOUR_BACKUP_FOLDER"
```

Backups are not encrypted by this helper. They include private account data,
password hashes, application notes, and resumes. Store them in a private,
access-controlled/encrypted location and keep a second protected copy on another
device or storage destination. Git-ignore rules do not encrypt files.

## Verify Local Recovery

```powershell
.\.venv-backup\Scripts\python.exe scripts/backup/jobstar_backup.py restore-local "C:\Users\YOUR_USER\JobStarBackups\YOUR_BACKUP_FOLDER" --database jobstar_recovery_20261020
```

Use a new suffix for each drill. The helper only accepts local hosts and new
database names beginning `jobstar_recovery_`. It never drops or overwrites an
existing database, including the regular `jobstar` or `jobstar_test` databases.
If restoration fails, the new database may remain; choose another name on retry.
Restore only trusted archives: restoring a PostgreSQL dump executes its SQL.

The drill restores schema/data in a transaction and checks table counts,
content fingerprints, ID sequences, restored constraints, file hashes, and
every stored resume-file reference. Its private report is saved beside the backup.

This verifies local data recovery. It does not, by itself, verify login in a
restored application or uploading objects into a replacement bucket. Never
point a recovery app at the production bucket for write testing: deleting a
resume there could delete the live file.

## Full Replacement-Project Recovery

In an actual incident, use a separate replacement project and a private bucket.
Restore only JobStar's application tables with `pg_restore --no-owner
--no-privileges`, using a trusted administrator connection and an empty target.
Reapply the deployment security settings, including the disabled Data API.
For every manifest entry, upload its local file under its original `key` and
`content_type`. Do not restore internal Supabase Storage tables manually.

Configure a separate test deployment to use the restored database and new bucket.
Verify login, application details, resume opening/selection, and ownership checks
before changing the live deployment's connection settings. This production
cutover is deliberately not automated by the local recovery helper.

## Schedule and Evidence

For light personal use, take a backup after meaningful data changes and before
schema migrations. Repeat the restore drill after changing the backup procedure
and periodically thereafter. Waking a paused project is not a backup.

Record the backup date, archive/file verification, local recovery database name,
and limitations in the verification record. Do not commit archives, manifests,
recovery reports, private data, or credentials. A successful export alone does
not count as a verified restore.

Run the helper's safety tests with:

```powershell
.\.venv-backup\Scripts\python.exe -m unittest discover -s scripts/backup -p "test_*.py" -v
```

## Verification Record

- Production backup: Passed - 2026-10-20; all six application tables and one referenced resume file.
- Database archive and referenced-file checksums: Passed - 2026-10-20.
- Isolated local restore and content/sequence checks: Passed - 2026-10-20 in `jobstar_recovery_20261020`.
- Re-upload to a separate bucket and restored-app login: not exercised
- Second protected backup copy: user-managed, not verified
