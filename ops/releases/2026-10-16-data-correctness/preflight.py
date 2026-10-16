#!/usr/bin/env python3
"""Read-only release checks. Emits aggregate JSON; nonzero means stop, never auto-repair."""
import argparse
import csv
from datetime import datetime, timezone
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import zlib

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent


def checksum(path):
    # Flyway SQL checksum: UTF-8 lines without BOM or line terminators, signed CRC32.
    value = zlib.crc32("".join(path.read_text(encoding="utf-8-sig").splitlines()).encode())
    return value if value < 2**31 else value - 2**32


def migration_blockers(history, files, expected):
    blockers = []
    versions = set()
    for row in history:
        version = str(row["version"])
        versions.add(version)
        if not row["success"]:
            blockers.append(f"failed migration V{version}")
        if version not in files:
            blockers.append(f"applied V{version} is absent from this checkout; do not repair Flyway history")
        elif row["checksum"] != checksum(files[version]):
            blockers.append(f"checksum mismatch V{version}; do not repair Flyway history")
    required = {v for v in files if int(v) <= expected}
    if versions != required:
        blockers.append(f"history does not match the expected V{expected} release boundary")
    return blockers


def parse_sections(output):
    sections = {}
    for row in csv.reader(io.StringIO(output)):
        if not row or row == ["section", "data"]:
            continue
        if len(row) != 2:
            raise ValueError("unexpected probe output")
        sections.setdefault(row[0], []).append(json.loads(row[1]))
    return sections


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", required=True)
    parser.add_argument("--expected-version", type=int, choices=(82, 98), required=True)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location(
        "goldys_readonly_probe", ROOT / "docs/audits/data-foundation/scripts/run_probes.py")
    probe = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(probe)
    metadata_sql = """
SELECT 'database' AS section, json_build_object('server_version_num',current_setting('server_version_num')::int,'read_only',current_setting('default_transaction_read_only'),'principal',current_user,'can_create_role',(SELECT rolsuper OR rolcreaterole FROM pg_roles WHERE rolname=current_user))::text AS data;
SELECT 'history' AS section, row_to_json(h)::text AS data FROM (SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank) h;
SELECT 'migration_hazards' AS section,json_build_object('duplicate_current_product_refs',(SELECT count(*) FROM (SELECT source_system,source_record_ref FROM canonical_product_sales WHERE superseded_at IS NULL GROUP BY source_system,source_record_ref HAVING count(*)>1) x),'inventory_missing_raw_refs',(SELECT count(*) FROM (SELECT raw_record_id FROM canonical_stocktake UNION ALL SELECT raw_record_id FROM canonical_stock_count UNION ALL SELECT raw_record_id FROM canonical_wastage) c LEFT JOIN raw_record r ON r.id=c.raw_record_id WHERE r.id IS NULL))::text AS data;
SELECT 'parent_column' AS section,json_build_object('present',EXISTS(SELECT FROM information_schema.columns WHERE table_name='canonical_invoice_line' AND table_schema='public' AND column_name='invoice_logical_id'))::text AS data;
SELECT 'analytical_role' AS section,json_build_object('present',EXISTS(SELECT FROM pg_roles WHERE rolname='goldys_ro'),'member',CASE WHEN EXISTS(SELECT FROM pg_roles WHERE rolname='goldys_ro') THEN pg_has_role(current_user,'goldys_ro','USAGE') ELSE false END,'raw_read',CASE WHEN EXISTS(SELECT FROM pg_roles WHERE rolname='goldys_ro') THEN has_table_privilege('goldys_ro','raw_record','SELECT') ELSE false END)::text AS data;
"""
    metadata = parse_sections(probe.run_sql(args.container, metadata_sql))
    files = {p.name.split("__")[0][1:]: p for p in
             (ROOT / "backend/src/main/resources/db/migration").glob("V*__*.sql")}
    blockers = migration_blockers(metadata["history"], files, args.expected_version)
    db = metadata["database"][0]
    if db["read_only"] != "on" or not 160000 <= db["server_version_num"] < 170000:
        blockers.append("requires PostgreSQL 16 and a read-only audit session")
    if any(metadata["migration_hazards"][0].values()):
        blockers.append("current product identities or inventory raw references block migration; investigate before rollout")
    role = metadata["analytical_role"][0]
    if args.expected_version == 82 and not role["present"] and not db["can_create_role"]:
        blockers.append("migration principal cannot create goldys_ro")
    if role["raw_read"]:
        blockers.append("goldys_ro can read raw_record; review grants before rollout")
    if args.expected_version == 98 and not role["member"]:
        blockers.append("audit/app principal lacks goldys_ro membership")
    # Stop before impact queries if the schema/history is unexpected.
    impact = {}
    if not blockers:
        sql = (HERE / "impact.sql").read_text()
        parent_filter = "AND l.invoice_logical_id IS NULL" if metadata["parent_column"][0]["present"] else ""
        impact = parse_sections(probe.run_sql(args.container, sql.replace("/*LEGACY_PARENT_FILTER*/", parent_filter)))
    report = {"captured_at": datetime.now(timezone.utc).isoformat(),
              "working_tree_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip()),
              "release_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
              "expected_version": args.expected_version, "metadata": metadata,
              "impact": impact, "blockers": blockers}
    print(json.dumps(report, indent=2))
    return 1 if blockers else 0


if __name__ == "__main__":
    sys.exit(main())
