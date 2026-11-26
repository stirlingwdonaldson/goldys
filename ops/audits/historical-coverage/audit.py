#!/usr/bin/env python3
"""Aggregate retained coverage, never certify source completeness or mutate the database.

Run with an explicitly selected container. No environment files, credentials, source
payloads, names, or individual staff/customer records are read or emitted.
"""

import argparse
import json
import re
import subprocess


DATES = (
    "trading_date", "invoice_date", "labour_date", "reservation_at", "reviewed_at",
    "counted_date", "stocktake_date", "wastage_date", "shift_start", "observation_date",
)
OPTIONS = (
    "-c default_transaction_read_only=on -c statement_timeout=30000 "
    "-c lock_timeout=1000 -c idle_in_transaction_session_timeout=15000 "
    "-c timezone=Australia/Melbourne"
)


def identifier(value):
    if not re.fullmatch(r"[a-z][a-z0-9_]*", value):
        raise ValueError("Unexpected schema identifier")
    return '"' + value + '"'


def section(name, query):
    identifier(name)
    return (
        "SELECT jsonb_build_object('section', '" + name
        + "', 'data', coalesce(jsonb_agg(t), '[]'::jsonb)) FROM (" + query + ") t;"
    )


def read(container, sql):
    command = [
        "docker", "exec", "-i", "-e", "PGOPTIONS=" + OPTIONS, container,
        "psql", "-X", "-q", "-At", "-U", "goldys", "-d", "goldys",
        "-v", "ON_ERROR_STOP=1",
    ]
    result = subprocess.run(
        command, input="BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;\n"
        + sql + "\nROLLBACK;\n", text=True, capture_output=True,
    )
    if result.returncode:
        # Do not echo database errors which could contain source values.
        raise RuntimeError("Read-only audit failed; no complete result produced")
    return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]


def queries(schema):
    statements = [section("snapshot", """
      SELECT now() AS captured_at, current_setting('transaction_read_only') AS read_only,
        current_setting('default_transaction_read_only') AS default_read_only,
        current_setting('transaction_isolation') AS isolation
    """), section("raw_sources", """
      SELECT source_system, fetcher_identity, count(*) AS deliveries,
        count(DISTINCT payload_sha256) AS distinct_payloads,
        min(fetched_at) AS first_captured, max(fetched_at) AS last_captured,
        sum(payload_byte_length) AS bytes
      FROM raw_record GROUP BY 1,2 ORDER BY 1,2
    """), section("latest_runs", """
      SELECT DISTINCT ON (source_system, connector_name)
        source_system, connector_name, status, started_at, completed_at,
        fetched_count, persisted_count
      FROM ingestion_run ORDER BY source_system, connector_name, started_at DESC
    """), section("historical_failures", """
      SELECT source_system, failure_type, count(*) AS failures,
        min(occurred_at) AS first_occurred, max(occurred_at) AS last_occurred
      FROM ingestion_failure GROUP BY 1,2 ORDER BY 1,2
    """), section("latest_processing_stages", """
      SELECT r.source_system, r.fetcher_identity, s.stage, s.outcome,
        count(*) AS deliveries
      FROM raw_record r LEFT JOIN (
        SELECT DISTINCT ON (ingestion_run_id, dataset, stage)
          ingestion_run_id, dataset, stage, outcome
        FROM ingestion_stage ORDER BY ingestion_run_id, dataset, stage, occurred_at DESC, id DESC
      ) s ON s.ingestion_run_id = r.ingestion_run_id
      GROUP BY 1,2,3,4 ORDER BY 1,2,3,4
    """)]
    for table, columns in sorted(schema.items()):
        name = identifier(table)
        date = next((d for d in DATES if d in columns), None)
        source = next((s for s in ("source_system", "authoritative_source") if s in columns), None)
        group = identifier(source) if source else "'UNSPECIFIED'::text"
        current = " WHERE superseded_at IS NULL" if "superseded_at" in columns else ""
        dates = (f", min({identifier(date)}::date) AS first_date, "
                 f"max({identifier(date)}::date) AS last_date, "
                 f"count(DISTINCT {identifier(date)}::date) AS observed_days") if date else ""
        statements.append(section(table, f"SELECT {group} AS source, count(*) AS rows"
                                  + dates + f" FROM {name}{current} GROUP BY 1 ORDER BY 1"))
        if date:
            statements.append(section(table + "_monthly",
                f"SELECT {group} AS source, to_char({identifier(date)}::date, 'YYYY-MM') AS period, "
                f"count(*) AS rows, count(DISTINCT {identifier(date)}::date) AS observed_days "
                f"FROM {name}{current} GROUP BY 1,2 ORDER BY 1,2"))
    statements.append(section("sale_projection_identity", """
      SELECT count(*) FILTER (WHERE c.id IS NULL) AS orphan_projection_rows,
        count(*) FILTER (WHERE r.canonical_version_id IS NULL) AS missing_projection_rows,
        count(*) FILTER (WHERE c.id IS NOT NULL AND r.canonical_version_id IS NOT NULL
          AND (c.quantity_sold IS DISTINCT FROM r.quantity_sold
            OR c.amount IS DISTINCT FROM r.amount
            OR c.trading_date IS DISTINCT FROM r.trading_date)) AS differing_rows
      FROM (SELECT id,quantity_sold,amount,trading_date FROM canonical_sale_item
        WHERE superseded_at IS NULL) c
      FULL JOIN resolved_sale_line r ON r.canonical_version_id=c.id
    """))
    return "\n".join(statements)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", required=True)
    args = parser.parse_args()
    metadata = read(args.container, section("schema", """
      SELECT table_name, array_agg(column_name ORDER BY ordinal_position) AS columns
      FROM information_schema.columns WHERE table_schema='public'
        AND (table_name LIKE 'canonical_%' OR table_name LIKE 'resolved_%'
          OR table_name IN ('source_product', 'source_product_sale_fact'))
      GROUP BY table_name ORDER BY table_name
    """))[0]["data"]
    schema = {row["table_name"]: row["columns"] for row in metadata}
    result = {row["section"]: row["data"] for row in read(args.container, queries(schema))}
    result["limitations"] = [
        "Observed dates and counts do not prove complete source populations.",
        "Current bitemporal versions only; intentionally raw-only datasets are not failed imports.",
        "Unversioned child tables include rows belonging to superseded parent versions.",
        "Run-level stages can describe multiple raw deliveries; counts are not rejected source-row counts.",
        "Historical failure records do not establish a currently unresolved failure.",
        "Schema metadata was read immediately before the repeatable-read aggregate snapshot.",
    ]
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
