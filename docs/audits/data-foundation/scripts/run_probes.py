#!/usr/bin/env python3
"""Operator-safe, read-only audit probe runner (Phase 1 data-foundation audit).

Runs reviewed SELECT-only SQL against a target Postgres container through the
same read-only wrapper as docs/audits/production-data/checks/probe.py:

  BEGIN READ ONLY; ... ROLLBACK;
  default_transaction_read_only=on, statement_timeout=8s, lock_timeout=1s,
  idle_in_transaction_session_timeout=15s, psql -X -q --csv -v ON_ERROR_STOP=1.

The connection role is the container's configured role; no credentials are read
or printed by this script (they live in the container environment). This wrapper
is NOT a security boundary for arbitrary SQL — only reviewed probe files should
be run through it.

Usage:
  python3 run_probes.py --container <postgres-container> [--out DIR]

The target is REQUIRED so an audit never accidentally points at a database the
operator did not intend. For a local fixture:  goldys-audit-fixture
For production (operator-approved, read-only): goldys-prod-postgres-1

Outputs (CSV, multiple result-set headers preserved) are written to --out.
"""
import argparse
import csv
import io
import json
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
PROBES = HERE / ".." / "probes"

SAFE_OPTS = ('-c default_transaction_read_only=on '
             '-c statement_timeout=8000 '
             '-c lock_timeout=1000 '
             '-c idle_in_transaction_session_timeout=15000')

KEY_COLUMNS = {
    "source_system", "logical_entity_id", "product_name_key", "stock_code",
    "supplier_name", "invoice_number", "staff_ref", "source_record_ref",
}


def run_sql(container, sql):
    cmd = ["docker", "exec", "-i", container, "sh", "-c",
           f'PGOPTIONS="{SAFE_OPTS}" psql -X -q --csv -v ON_ERROR_STOP=1 '
           '-U "$POSTGRES_USER" -d "$POSTGRES_DB"']
    proc = subprocess.run(cmd, input="BEGIN READ ONLY;\n" + sql + "\nROLLBACK;\n",
                          text=True, capture_output=True)
    if proc.returncode != 0:
        sys.stderr.write(f"probe failed (rc={proc.returncode}):\n{proc.stderr}\n")
        sys.exit(proc.returncode)
    return proc.stdout


def generate_population(schema_csv):
    """Build aggregate-only population probes from the schema inventory."""
    cols = {}
    in_cols = False
    for row in csv.reader(io.StringIO(schema_csv)):
        if not row:
            continue
        # S01 column section header: table_name, ordinal, column_name, ...
        if row[0] == "table_name" and len(row) > 2 and row[2] == "column_name":
            in_cols = True
            continue
        # next section header (constraints/indexes/views/...) -> stop
        if row[0] == "table_name" and in_cols:
            break
        if in_cols:
            cols.setdefault(row[0], []).append(row)

    statements = ["-- P01: aggregate-only population/missingness (generated)."]
    for table, rows in cols.items():
        names = {r[2] for r in rows}
        exprs = ["count(*) AS row_count"]
        if "superseded_at" in names:
            exprs += ["count(*) FILTER (WHERE superseded_at IS NULL) AS current_rows",
                      "count(*) FILTER (WHERE superseded_at IS NOT NULL) AS superseded_rows"]
        for r in rows:
            col, typ = r[2], r[3]
            exprs.append(f'count(*) FILTER (WHERE "{col}" IS NULL) AS "{col}_nulls"')
            if typ == "date" or typ.startswith("timestamp"):
                exprs += [f'min("{col}") AS "{col}_min"', f'max("{col}") AS "{col}_max"']
            if col in KEY_COLUMNS:
                exprs.append(f'count(DISTINCT "{col}") AS "{col}_distinct"')
        statements.append(
            f"SELECT '{table}' AS table_name,row_to_json(s) AS aggregate "
            f"FROM (SELECT " + ",".join(exprs) + f' FROM public."{table}") s;')
    return "\n".join(statements) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--container", required=True)
    ap.add_argument("--out", default=str(HERE / ".." / "evidence"))
    ap.add_argument("--skip-population", action="store_true")
    args = ap.parse_args()

    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    # 1. schema
    schema = run_sql(args.container, (PROBES / "s1_schema.sql").read_text())
    (out / "schema.csv").write_text(schema)

    # 2. population (generated from schema)
    if not args.skip_population:
        pop = generate_population(schema)
        (out / "population.csv").write_text(
            run_sql(args.container, pop))

    # 3. remaining probes
    for name in ["c1_coverage", "c2_stage", "i1_integrity",
                 "i2_identity", "t1_temporal"]:
        sql = (PROBES / f"{name}.sql").read_text()
        (out / f"{name}.csv").write_text(run_sql(args.container, sql))
        print(f"ran {name}.sql -> {out / (name + '.csv')}")

    print(f"\nDone. Evidence written to {out}")


if __name__ == "__main__":
    main()
