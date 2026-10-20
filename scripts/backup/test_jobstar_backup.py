import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

from botocore.response import StreamingBody

import jobstar_backup as backup


class BackupSafetyTests(unittest.TestCase):
    def test_download_preserves_bytes_and_closes_the_sdk_stream(self):
        source = io.BytesIO(b"sample resume contents")
        body = StreamingBody(source, len(source.getvalue()))
        client = Mock()
        client.get_object.return_value = {"Body": body, "ContentType": "application/pdf"}
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "resume.bin"
            content_type = backup.download_file(client, "resumes", "users/1/resume.pdf", path)
            self.assertEqual(b"sample resume contents", path.read_bytes())
            self.assertEqual("application/pdf", content_type)
            self.assertTrue(source.closed)

    def test_restore_rejects_remote_database_and_development_database(self):
        with self.assertRaises(backup.BackupError):
            backup.validate_restore_target({"RESTORE_DB_HOST": "example.supabase.com"}, "jobstar_recovery_check")
        for name in ("jobstar", "jobstar_test", "postgres", 'jobstar_recovery_x";drop database jobstar;'):
            with self.subTest(name=name), self.assertRaises(backup.BackupError):
                backup.validate_restore_target({"RESTORE_DB_HOST": "127.0.0.1"}, name)
        backup.validate_restore_target({"RESTORE_DB_HOST": "127.0.0.1"}, "jobstar_recovery_check")

    def test_backups_cannot_be_written_inside_repository(self):
        for path in (backup.ROOT, backup.ROOT / "backups"):
            with self.assertRaises(backup.BackupError):
                backup.outside_repository(path)

    def test_placeholder_passwords_are_rejected(self):
        with self.assertRaises(backup.BackupError):
            backup.required({"BACKUP_DB_PASSWORD": "REPLACE_WITH_PASSWORD"}, "BACKUP_DB_PASSWORD")

    def make_backup(self, folder):
        (folder / "files").mkdir()
        (folder / "COMPLETE").touch()
        dump = folder / "database.dump"
        dump.write_bytes(b"test archive")
        key = "users/1/resumes/test.pdf"
        relative = "files/" + hashlib.sha256(key.encode()).hexdigest() + ".bin"
        document = folder / relative
        document.write_bytes(b"test resume")
        manifest = {"format": 1, "completed": True, "database_sha256": backup.sha256(dump),
                    "files": [{"key": key, "path": relative, "bytes": document.stat().st_size,
                               "sha256": backup.sha256(document)}]}
        self.save_manifest(folder, manifest)
        return manifest

    def save_manifest(self, folder, manifest):
        (folder / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    def test_corrupt_database_and_file_copies_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary).resolve()
            manifest = self.make_backup(folder)
            backup.verify_files(folder)
            (folder / manifest["files"][0]["path"]).write_bytes(b"changed")
            with self.assertRaises(backup.BackupError):
                backup.verify_files(folder)
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary).resolve()
            self.make_backup(folder)
            (folder / "database.dump").write_bytes(b"changed")
            with self.assertRaises(backup.BackupError):
                backup.verify_files(folder)

    def test_manifest_cannot_reference_arbitrary_paths_or_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary).resolve()
            manifest = self.make_backup(folder)
            original = manifest["files"][0].copy()
            manifest["files"][0]["path"] = "../outside.txt"
            self.save_manifest(folder, manifest)
            with self.assertRaises(backup.BackupError):
                backup.verify_files(folder)
            manifest["files"] = [original, original.copy()]
            self.save_manifest(folder, manifest)
            with self.assertRaises(backup.BackupError):
                backup.verify_files(folder)

    def test_incomplete_backup_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary).resolve()
            with self.assertRaises(backup.BackupError):
                backup.verify_files(folder)
            manifest = self.make_backup(folder)
            (folder / "INCOMPLETE").touch()
            with self.assertRaises(backup.BackupError):
                backup.verify_files(folder)
            (folder / "INCOMPLETE").unlink()
            manifest["completed"] = False
            self.save_manifest(folder, manifest)
            with self.assertRaises(backup.BackupError):
                backup.verify_files(folder)


if __name__ == "__main__":
    unittest.main()
