"""Tests for the Phase 1 data-foundation audit harness.

Run with:  python3 -m unittest tests.test_audit_harness

These tests regenerate the derived artifacts (schema inventory, dataset
inventory, field lineage) and assert:
  1. the schema inventory is non-empty and captures the current schema,
  2. every table referenced by the read-only probes exists (so a probe can
     never silently query a stale or invented table),
  3. the dataset inventory is self-consistent (every table/parser/fetcher it
     names resolves in the schema or code),
  4. the field-lineage matrix covers canonical, resolved and dropped fields.

No database is required; these validate the *code-derived* side of the audit.
"""
import csv
import json
import pathlib
import re
import subprocess
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
AUDIT = ROOT / "docs/audits/data-foundation"
SCRIPTS = AUDIT / "scripts"
PROBES = AUDIT / "probes"

# Flyway/system catalogs referenced by the schema probe but not in migrations.
ALLOWLIST = {"flyway_schema_history"}
PG_PREFIX = "pg_"


def run(script):
    r = subprocess.run([sys.executable, str(script)], capture_output=True,
                       text=True, cwd=ROOT)
    assert r.returncode == 0, f"{script.name} failed:\n{r.stderr}"
    return r.stdout


def load_schema():
    return json.loads((AUDIT / "schema-inventory.json").read_text())


TABLE_PATTERN = re.compile(r"^(canonical_|resolved_|ingestion_|pg_)")
KNOWN_TABLES = {"raw_record", "flyway_schema_history", "permission",
                "invoice_ingest_flag", "saved_dashboard", "user_account",
                "resolution_rule"}


def table_refs(sql):
    refs = set()
    for m in re.finditer(r"(?:FROM|JOIN)\s+(?:public\.)?\"?([a-z_][a-z0-9_]*)\"?",
                         sql):
        name = m.group(1)
        if TABLE_PATTERN.match(name) or name in KNOWN_TABLES:
            refs.add(name)
    return refs


def read_rows(path):
    with path.open() as f:
        return list(csv.DictReader(f))


class SchemaInventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        run(SCRIPTS / "schema_inventory.py")
        cls.schema = load_schema()

    def test_schema_is_non_empty(self):
        self.assertGreater(len(self.schema), 50)

    def test_raw_ledger_authoritative_bytes(self):
        cols = {c["column"] for c in self.schema["raw_record"]}
        for c in ("payload_bytes", "payload_sha256", "payload_byte_length",
                  "source_system", "fetcher_identity", "ingestion_run_id"):
            self.assertIn(c, cols)

    def test_core_entities_present(self):
        for t in ("canonical_invoice", "canonical_invoice_line",
                  "canonical_payment", "canonical_sale_item",
                  "resolved_daily_sales", "ingestion_stage"):
            self.assertIn(t, self.schema)


class ProbeReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        run(SCRIPTS / "schema_inventory.py")
        cls.schema = load_schema()
        cls.known = set(cls.schema) | ALLOWLIST

    def test_every_probe_table_exists(self):
        bad = []
        for p in sorted(PROBES.glob("*.sql")):
            for ref in table_refs(p.read_text()):
                if ref in self.known:
                    continue
                if ref.startswith(PG_PREFIX):
                    continue
                bad.append((p.name, ref))
        self.assertEqual(bad, [], f"probes reference unknown tables: {bad}")


class DatasetInventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        run(SCRIPTS / "schema_inventory.py")
        cls.schema = load_schema()
        run(SCRIPTS / "build_inventory.py")
        cls.rows = read_rows(AUDIT / "dataset-inventory.csv")

    def test_inventory_non_empty(self):
        self.assertGreater(len(self.rows), 20)

    def test_all_tables_and_fetchers_resolve(self):
        for r in self.rows:
            self.assertEqual(r["validation"], "ok",
                             f"{r['dataset']}: {r['validation']}")
            for t in r["canonical"].split(",") + r["resolved"].split(","):
                if t:
                    self.assertIn(t, self.schema, f"unknown table {t}")


class FieldLineageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        run(SCRIPTS / "schema_inventory.py")
        run(SCRIPTS / "build_lineage.py")
        cls.rows = read_rows(AUDIT / "field-lineage.csv")

    def test_has_all_row_types(self):
        kinds = {r["row_type"] for r in self.rows}
        self.assertIn("canonical_column", kinds)
        self.assertIn("resolved_column", kinds)
        self.assertIn("source_field_dropped", kinds)

    def test_system_columns_flagged(self):
        sys_rows = [r for r in self.rows
                    if r["name"] == "superseded_at"
                    and r["row_type"] == "canonical_column"]
        self.assertTrue(sys_rows)
        for r in sys_rows:
            self.assertEqual(r["classification"], "system")


if __name__ == "__main__":
    unittest.main()
