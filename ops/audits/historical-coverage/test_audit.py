import io
import unittest
from unittest.mock import patch
from types import SimpleNamespace

import audit
import lightspeed_backfills


class RetainedEvidenceTest(unittest.TestCase):
    def test_database_sessions_are_forced_read_only_and_rolled_back(self):
        result = SimpleNamespace(returncode=0, stdout='{"data": []}\n')
        with patch("audit.subprocess.run", return_value=result) as run:
            self.assertEqual(audit.read("audit-db", "SELECT 1;"), [{"data": []}])
        args, kwargs = run.call_args
        self.assertIn("PGOPTIONS=" + audit.OPTIONS, args[0])
        self.assertTrue(kwargs["input"].startswith("BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;"))
        self.assertTrue(kwargs["input"].endswith("ROLLBACK;\n"))

    def test_database_error_does_not_disclose_source_values(self):
        result = SimpleNamespace(returncode=3, stdout="", stderr="private source value")
        with patch("audit.subprocess.run", return_value=result):
            with self.assertRaisesRegex(RuntimeError, "no complete result") as caught:
                audit.read("audit-db", "SELECT 1;")
        self.assertNotIn("private", str(caught.exception))

    def test_schema_identifiers_cannot_inject_sql(self):
        with self.assertRaises(ValueError):
            audit.identifier("canonical_sale_item; DELETE FROM raw_record")

    def inspect(self, body, exit_code=0):
        process = SimpleNamespace(stdin=io.StringIO(), stdout=io.StringIO(body), wait=lambda: exit_code)
        with patch("lightspeed_backfills.subprocess.Popen", return_value=process):
            return lightspeed_backfills.inspect("audit-db", {"id": "00000000-0000-0000-0000-000000000001"}, {"existing"})

    def test_repeated_rows_do_not_become_extra_missing_sales(self):
        body = "Sales Data Receipt Line ID,Sales Data Sale Closed Date,Sales Data Product Quantity,Sales Data Total Inc Tax,Sales Data Total Tax,Products Product Name,Payment Data Payment Type Name\n"
        body += "new,2025-01-01,1,25,2.27,Chicken Parma,Card\n" * 2
        body += "new,2025-01-01,2,50,4.54,Chicken Parma,Card\n"
        body += "existing,2025-01-01,1,25,2.27,Chicken Parma,Card\n"
        result = self.inspect(body)
        self.assertEqual(result["rows"], 4)
        self.assertEqual(result["unique_keys"], 2)
        self.assertEqual(result["missing_keys_by_year"], {"2025": 1})
        self.assertEqual(result["financial_conflict_keys"], 1)
        self.assertFalse(result["safe_to_replay"])

    def test_failed_or_unsupported_report_is_unknown_not_zero(self):
        for body, code in (("", 3), ("unrelated_header\nvalue\n", 0)):
            with self.subTest(code=code):
                result = self.inspect(body, code)
                self.assertFalse(result["inspection_succeeded"])
                self.assertIsNone(result["unique_keys"])
                self.assertIsNone(result["missing_keys_by_year"])


if __name__ == "__main__":
    unittest.main()
