"""Isolated release-gate regressions; no Docker/production access."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("preflight", Path(__file__).with_name("preflight.py"))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class ReleaseGateTest(unittest.TestCase):
    def test_checksum_ignores_bom_and_line_endings(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / "a.sql", Path(directory) / "b.sql"
            a.write_bytes(b"\xef\xbb\xbfSELECT 1;\r\nSELECT 2;\r\n")
            b.write_bytes(b"SELECT 1;\nSELECT 2;\n")
            self.assertEqual(p.checksum(a), p.checksum(b))

    def test_gate_rejects_changed_or_foreign_or_failed_history(self):
        with tempfile.TemporaryDirectory() as directory:
            sql = Path(directory) / "V82.sql"
            sql.write_text("SELECT 1;\n")
            history = [{"version": "82", "checksum": p.checksum(sql), "success": True}]
            self.assertEqual(p.migration_blockers(history, {"82": sql}, 82), [])
            sql.write_text("SELECT 2;\n")
            self.assertTrue(any("checksum mismatch" in b for b in p.migration_blockers(history, {"82": sql}, 82)))
            self.assertTrue(any("absent" in b for b in p.migration_blockers(history, {}, 82)))
            history[0]["success"] = False
            self.assertTrue(any("failed migration" in b for b in p.migration_blockers(history, {"82": sql}, 82)))

    def test_gate_rejects_missing_applied_version(self):
        self.assertTrue(p.migration_blockers([], {"82": Path("unused.sql")}, 82))

    def test_repeated_csv_headers_and_section_rows_are_preserved(self):
        self.assertEqual(p.parse_sections('section,data\na,"{""n"":1}"\nsection,data\na,"{""n"":2}"\n'), {"a": [{"n": 1}, {"n": 2}]})


if __name__ == "__main__":
    unittest.main()
