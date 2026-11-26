#!/usr/bin/env python3
"""Inspect original retained sales exports; emit candidates, never an apply manifest.

Financial conflict counts compare quantity/amount/tax only, not the full Java
model. Unsafe joined reports are never classified as safe to replay.
"""

import argparse
import collections
import csv
from datetime import date as calendar_date
from decimal import Decimal, InvalidOperation
import json
import subprocess
import sys
from uuid import UUID

from audit import OPTIONS, read, section


def financial(row):
    values = []
    for field in ("Sales Data Product Quantity", "Sales Data Total Inc Tax", "Sales Data Total Tax"):
        value = row.get(field, "").strip().replace("$", "").replace(",", "")
        values.append(Decimal(value) if value else None)
    return tuple(values)


def inspect(container, raw, current):
    raw_id = str(UUID(raw["id"]))
    command = ["docker", "exec", "-i", "-e", "PGOPTIONS=" + OPTIONS, container,
               "psql", "-X", "-q", "-At", "-U", "goldys", "-d", "goldys",
               "-v", "ON_ERROR_STOP=1"]
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, text=True)
    query = ("BEGIN READ ONLY; SELECT convert_from(payload_bytes,'UTF8')::jsonb "
             "#>> '{attachment,data}' FROM raw_record WHERE id='" + raw_id + "'; ROLLBACK;")
    process.stdin.write(query)
    process.stdin.close()
    unique = {}
    conflicts = set()
    years = collections.Counter()
    missing = collections.Counter()
    parma_missing = collections.Counter()
    dates = set()
    malformed = 0
    rows = 0
    reader = csv.DictReader(process.stdout)
    headers = reader.fieldnames or []
    unsafe = [h for h in headers if h.startswith(("Saleline Payments ", "Payment Data "))
              or h == "Product Salelines Product ID"]
    try:
        for row in reader:
            key = row.get("Sales Data Receipt Line ID", "").strip()
            if not key:
                continue
            date = row.get("Sales Data Sale Closed Date", "")
            try:
                calendar_date.fromisoformat(date)
            except ValueError:
                malformed += 1
                continue
            try:
                values = financial(row)
            except (InvalidOperation, AttributeError):
                malformed += 1
                continue
            rows += 1
            dates.add(date)
            if key in unique:
                if unique[key] != values:
                    conflicts.add(key)
                continue
            unique[key] = values
            years[date[:4]] += 1
            if key not in current:
                missing[date[:4]] += 1
                name = row.get("Products Product Name", "")
                if "parma" in name.lower():
                    parma_missing[name] += 1
    finally:
        process.stdout.close()
        code = process.wait()
    supported = all(h in headers for h in ("Sales Data Receipt Line ID", "Sales Data Sale Closed Date",
                                           "Sales Data Product Quantity", "Sales Data Total Inc Tax"))
    succeeded = code == 0 and supported and malformed == 0
    return dict(raw, inspection_succeeded=succeeded,
                rows=rows if succeeded else None,
                unique_keys=len(unique) if succeeded else None,
                observed_days=len(dates) if succeeded else None,
                first_date=min(dates, default=None) if succeeded else None,
                last_date=max(dates, default=None) if succeeded else None, unsafe_join_headers=unsafe,
                financial_conflict_keys=len(conflicts) if succeeded else None, malformed_rows=malformed,
                keys_by_year=dict(years) if succeeded else None,
                missing_keys_by_year=dict(missing) if succeeded else None,
                missing_parma_line_keys=dict(parma_missing) if succeeded else None,
                safe_to_replay=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", required=True)
    args = parser.parse_args()
    data = {r["section"]: r["data"] for r in read(args.container, "\n".join([
        section("raw", """
          SELECT DISTINCT ON (payload_sha256) id,payload_sha256,payload_byte_length,fetched_at
          FROM raw_record WHERE fetcher_identity='lightspeed-sale-items'
            AND fetched_at<'2026-10-11T00:00:00Z'
          ORDER BY payload_sha256,fetched_at
        """),
        section("current", """
          SELECT source_record_ref FROM canonical_sale_item
          WHERE source_system='LIGHTSPEED' AND superseded_at IS NULL
        """),
    ]))}
    current = {row["source_record_ref"] for row in data.pop("current")}
    results = []
    for raw in data["raw"]:
        results.append(inspect(args.container, raw, current))
        print("Inspected retained report " + str(len(results)) + "/" + str(len(data["raw"])),
              file=sys.stderr, flush=True)
    print(json.dumps({"reports": results, "limitations": [
        "Missing-key counts overlap between reports; never sum across reports.",
        "Keys compared against one read-only snapshot; raw bytes are immutable.",
        "Line counts are not quantities sold, and known source joins can inflate measures.",
        "Financial conflicts are diagnostic, not the full production parser/identity validation.",
        "Only original Lightspeed sale-item payloads captured before 11 October were inspected.",
        "Failed inspection is unknown, never an empty or complete report.",
        "No report in this audit is approved for replay.",
    ]}, indent=2))


if __name__ == "__main__":
    main()
