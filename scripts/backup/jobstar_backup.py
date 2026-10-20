"""Back up JobStar data and referenced files; verify recovery in a new local DB."""

import argparse
from contextlib import closing
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
TABLES = ("user_accounts", "resume_version", "application", "contact", "interview", "follow_up_reminder")


class BackupError(Exception):
    pass


def load_config(path):
    config = {}
    for source in (ROOT / "backend" / ".env", path):
        if not source.is_file():
            raise BackupError(f"Missing configuration file: {source}")
        for line in source.read_text(encoding="utf-8-sig").splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            key, separator, value = line.partition("=")
            if not separator:
                raise BackupError("Configuration must use KEY=value lines.")
            config[key.strip()] = value.strip()
    return config


def required(config, key):
    value = config.get(key, "")
    if not value or value.startswith("REPLACE_"):
        raise BackupError(f"Set {key} privately in backend/.env.backup first.")
    return value


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def json_digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


class Postgres:
    def __init__(self, config, prefix):
        self.bin = Path(required(config, "PG_BIN"))
        self.host = required(config, prefix + "_HOST")
        self.port = required(config, prefix + "_PORT")
        self.user = required(config, prefix + "_USER")
        self.env = {key: value for key, value in os.environ.items() if not key.upper().startswith("PG")}
        self.env.update(PGPASSWORD=required(config, prefix + "_PASSWORD"),
                        PGSSLMODE=config.get(prefix + "_SSLMODE", "prefer"),
                        PGCONNECT_TIMEOUT="15", PGAPPNAME="jobstar_backup",
                        PGOPTIONS="-c timezone=UTC -c datestyle=ISO,YMD")
        if prefix == "BACKUP_DB":
            if self.host not in ("localhost", "127.0.0.1", "::1") and self.env["PGSSLMODE"] not in ("require", "verify-ca", "verify-full"):
                raise BackupError("Remote backups require an encrypted database connection.")
            self.env["PGOPTIONS"] += " -c default_transaction_read_only=on"

    def run(self, tool, database, options):
        executable = self.bin / (tool + (".exe" if os.name == "nt" else ""))
        if not executable.is_file():
            raise BackupError(f"Cannot find {tool}; check PG_BIN.")
        result = subprocess.run([str(executable), "--host", self.host, "--port", self.port,
                                 "--username", self.user, "--dbname", database, "--no-password", *options],
                                env=self.env, capture_output=True, encoding="utf-8", errors="replace")
        if result.returncode:
            # PostgreSQL errors can include private row values; do not send them to logs.
            raise BackupError(f"{tool} failed (exit {result.returncode}). Check connectivity, credentials, tool/server versions and permissions. Private database error details were not printed.")
        return result.stdout

    def query(self, database, sql):
        return self.run("psql", database, ["-X", "-A", "-t", "--set", "ON_ERROR_STOP=1", "--command", sql]).strip()


def inventory(pg, database):
    tables = {}
    for table in TABLES:
        rows = json.loads(pg.query(database, f"select coalesce(json_agg(to_jsonb(t) order by t.id), '[]'::json) from public.{table} t"))
        tables[table] = {"rows": len(rows), "sha256": json_digest(rows)}
    sequences = []
    for table in TABLES:
        name = pg.query(database, f"select pg_get_serial_sequence('public.{table}', 'id')")
        if not re.fullmatch(r"public\.[a-z_]+", name):
            raise BackupError(f"Missing or unexpected ID sequence for {table}.")
        state = json.loads(pg.query(database, f"select row_to_json(s) from (select last_value, is_called from {name}) s"))
        sequences.append({"name": name, **state})
    return {"tables": tables, "sequences": sequences}


def references(pg, database):
    return json.loads(pg.query(database, """
        select coalesce(json_agg(json_build_object('key', storage_key, 'size', file_size)
            order by storage_key), '[]'::json)
        from public.resume_version where storage_key is not null
        """))


def outside_repository(path):
    resolved = path.resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise BackupError("Backups must be outside the Git repository.")
    return resolved


def download_file(s3, bucket, key, local):
    response = s3.get_object(Bucket=bucket, Key=key)
    with closing(response["Body"]) as body, local.open("xb") as target:
        for chunk in body.iter_chunks(chunk_size=1024 * 1024):
            target.write(chunk)
    return response.get("ContentType", "application/octet-stream")


def backup(config, output_root):
    # Validate configuration before creating files or contacting services.
    import boto3
    from botocore.config import Config

    pg = Postgres(config, "BACKUP_DB")
    database = required(config, "BACKUP_DB_NAME")
    endpoint = required(config, "SUPABASE_STORAGE_ENDPOINT")
    if not endpoint.startswith("https://"):
        raise BackupError("Storage endpoint must use HTTPS.")
    s3 = boto3.client("s3", endpoint_url=endpoint,
                      region_name=required(config, "SUPABASE_STORAGE_REGION"),
                      aws_access_key_id=required(config, "SUPABASE_STORAGE_ACCESS_KEY"),
                      aws_secret_access_key=required(config, "SUPABASE_STORAGE_SECRET_KEY"),
                      config=Config(s3={"addressing_style": "path"}, retries={"max_attempts": 3},
                                    connect_timeout=15, read_timeout=60))
    bucket = config.get("SUPABASE_STORAGE_BUCKET", "resumes")
    started = datetime.now(timezone.utc).isoformat()
    before = inventory(pg, database)
    refs = references(pg, database)
    folder = outside_repository(output_root) / datetime.now(timezone.utc).strftime("jobstar-%Y%m%dT%H%M%S%fZ")
    folder.mkdir(parents=True, exist_ok=False)
    (folder / "INCOMPLETE").touch()
    (folder / "files").mkdir()
    print("Exporting application tables (read-only)...", flush=True)
    dump = folder / "database.dump"
    pg.run("pg_dump", database, ["--format=custom", "--no-owner", "--no-privileges", "--strict-names",
                                 "--file", str(dump), *[f"--table=public.{table}" for table in TABLES]])
    files = []
    seen = set()
    for ref in refs:
        key = ref["key"]
        if key in seen:
            continue
        seen.add(key)
        name = "files/" + hashlib.sha256(key.encode()).hexdigest() + ".bin"
        local = folder / name
        try:
            content_type = download_file(s3, bucket, key, local)
        except Exception as exception:
            response = getattr(exception, "response", {})
            code = response.get("Error", {}).get("Code", type(exception).__name__)
            safe_code = code if re.fullmatch(r"[A-Za-z0-9_]+", str(code)) else "StorageError"
            raise BackupError(f"A referenced resume could not be downloaded ({safe_code}). Check private S3 settings and file availability; this backup is incomplete.") from exception
        size = local.stat().st_size
        if ref["size"] is not None and size != ref["size"]:
            raise BackupError("A resume's stored size does not match its database record; this backup is incomplete.")
        files.append({"key": key, "path": name, "bytes": size, "sha256": sha256(local),
                      "content_type": content_type})
    if inventory(pg, database) != before:
        raise BackupError("Database changed during backup. Pause edits/uploads/deletions and take a new backup. This copy remains incomplete.")
    manifest = {"format": 1, "completed": True, "started_utc": started,
                "completed_utc": datetime.now(timezone.utc).isoformat(), "bucket": bucket,
                "database_sha256": sha256(dump), "inventory": before, "files": files}
    (folder / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (folder / "INCOMPLETE").rename(folder / "COMPLETE")
    print(f"Backup complete: {folder}")
    print(f"Application tables: {len(TABLES)}; referenced resume files: {len(files)}")
    return folder


def verify_files(folder):
    manifest_path = folder / "manifest.json"
    if not manifest_path.is_file() or not (folder / "COMPLETE").is_file() or (folder / "INCOMPLETE").exists():
        raise BackupError("No completed manifest found; this backup must not be used for recovery.")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("format") != 1 or manifest.get("completed") is not True:
        raise BackupError("Unsupported or incomplete backup manifest.")
    dump = folder / "database.dump"
    if not dump.is_file() or sha256(dump) != manifest["database_sha256"]:
        raise BackupError("Database archive is missing or its checksum differs.")
    seen = set()
    for entry in manifest["files"]:
        expected_path = "files/" + hashlib.sha256(entry["key"].encode()).hexdigest() + ".bin"
        if entry["path"] != expected_path or entry["key"] in seen:
            raise BackupError("Invalid or duplicate file mapping in manifest.")
        seen.add(entry["key"])
        path = (folder / entry["path"]).resolve()
        if folder not in path.parents or not path.is_file():
            raise BackupError("A backup file is missing or points outside the backup folder.")
        if path.stat().st_size != entry["bytes"] or sha256(path) != entry["sha256"]:
            raise BackupError("A resume backup file failed its size/checksum check.")
    return manifest


def validate_restore_target(config, name):
    if required(config, "RESTORE_DB_HOST") not in ("127.0.0.1", "localhost", "::1"):
        raise BackupError("Recovery drills are restricted to local PostgreSQL.")
    if not re.fullmatch(r"jobstar_recovery_[a-z0-9_]{1,40}", name):
        raise BackupError("Use a new database named jobstar_recovery_<lowercase_suffix>.")


def restore(config, folder, name):
    validate_restore_target(config, name)
    folder = outside_repository(folder)
    manifest = verify_files(folder)
    pg = Postgres(config, "RESTORE_DB")
    if pg.query("postgres", f"select count(*) from pg_database where datname = '{name}'") != "0":
        raise BackupError("Recovery database already exists. Choose a new name; existing databases are never overwritten.")
    pg.query("postgres", f'create database "{name}" template template0')
    pg.run("pg_restore", name, ["--exit-on-error", "--single-transaction", "--no-owner", "--no-privileges", str(folder / "database.dump")])
    actual = inventory(pg, name)
    if actual != manifest["inventory"]:
        raise BackupError("Restored records or ID sequences differ from the backup inventory.")
    backed_up = {entry["key"]: entry for entry in manifest["files"]}
    for ref in references(pg, name):
        entry = backed_up.get(ref["key"])
        if entry is None or (ref["size"] is not None and ref["size"] != entry["bytes"]):
            raise BackupError("A restored resume record has no matching backed-up file.")
    report = {"verified_utc": datetime.now(timezone.utc).isoformat(), "database": name,
              "table_counts": {table: actual["tables"][table]["rows"] for table in TABLES},
              "sequences_match": True, "file_checksums_match": True,
              "resume_references_match": True, "storage_upload_tested": False,
              "application_login_tested": False}
    report_path = folder / f"recovery-{name}.json"
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Recovery verified in NEW local database: {name}")
    print(f"Report: {report_path}")
    print("Checks passed: table contents/counts, ID sequences, constraints, file hashes and resume references.")
    print("This does not test uploading files to a replacement bucket or signing in to the restored app.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=ROOT / "backend" / ".env.backup")
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("backup")
    create.add_argument("--output-root", type=Path, default=Path.home() / "JobStarBackups")
    check = commands.add_parser("verify-files")
    check.add_argument("folder", type=Path)
    recover = commands.add_parser("restore-local")
    recover.add_argument("folder", type=Path)
    recover.add_argument("--database", required=True)
    args = parser.parse_args()
    try:
        if args.command == "verify-files":
            verify_files(outside_repository(args.folder))
            print("Database archive and resume-file checksums passed.")
        else:
            config = load_config(args.config)
            if args.command == "backup":
                backup(config, args.output_root)
            else:
                restore(config, args.folder, args.database)
    except (BackupError, OSError, ValueError, KeyError, ImportError) as exception:
        if isinstance(exception, BackupError):
            print(str(exception), file=sys.stderr)
        else:
            print("Backup/recovery did not complete. Check setup, dependencies and file accessibility; private error details were not printed.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
