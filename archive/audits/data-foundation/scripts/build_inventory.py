#!/usr/bin/env python3
"""Build the dataset inventory and self-validate it against the schema + code.

Reads the curated mapping in ``datasets.py`` and cross-checks:
  - every canonical / resolved table name exists in the schema inventory,
  - every parser class and semantic-query interface exists in the backend source,
  - every fetcher_identity literal appears in the backend source.

Any failure is printed as a WARNING (and written to the CSV) rather than silently
dropped, so drift between the curated mapping and the code is always visible.

Output: dataset-inventory.csv
"""
import csv
import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import datasets  # noqa: E402

REPO = pathlib.Path(__file__).resolve().parents[4]
SRC = REPO / "backend/src/main/java/com/goldys/platform"
OUT = pathlib.Path(__file__).resolve().parent.parent

SCHEMA_JSON = OUT / "schema-inventory.json"
INVENTORY_CSV = OUT / "dataset-inventory.csv"

FIELDS = ["source_system", "dataset", "fetcher_identity", "parser",
          "canonical", "resolved", "semantic_query", "read_mode",
          "excluded_reason", "record_grain", "source_key", "validation"]


def load_schema():
    return json.loads(SCHEMA_JSON.read_text())


def find_class(name):
    if not name:
        return True  # no class to find
    hits = list(SRC.rglob(f"{name}.java"))
    return bool(hits)


def find_literal(name):
    if not name:
        return True
    hits = list(SRC.rglob("*.java"))
    for p in hits:
        if name in p.read_text(errors="replace"):
            return True
    return False


def main():
    if not SCHEMA_JSON.exists():
        sys.exit("schema-inventory.json missing — run schema_inventory.py first")
    schema = load_schema()

    rows = []
    warnings = []
    for d in datasets.as_dicts():
        problems = []

        canon = [t for t in d["canonical"].split(",") if t]
        resolv = [t for t in d["resolved"].split(",") if t]
        for t in canon + resolv:
            if t not in schema:
                problems.append(f"table:{t}:missing")
        for p in d["parser"].split(","):
            p = p.strip()
            if p and not find_class(p):
                problems.append(f"parser:{p}:missing")
        for q in d["semantic_query"].split(","):
            q = q.strip()
            if q and not find_class(q):
                problems.append(f"semantic:{q}:missing")
        if not find_literal(d["fetcher_identity"]):
            problems.append(f"fetcher:{d['fetcher_identity']}:not-in-code")

        if problems:
            warnings.append((d["dataset"], "; ".join(problems)))

        row = dict(d)
        row["validation"] = "ok" if not problems else "; ".join(problems)
        rows.append(row)

    with INVENTORY_CSV.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)

    n = len(rows)
    nbad = len(warnings)
    print(f"Wrote {n} dataset rows -> dataset-inventory.csv "
          f"({nbad} with validation warnings)")
    for ds, prob in warnings:
        print(f"  WARN {ds}: {prob}")

    # Domain roll-up
    domains = {}
    for r in rows:
        domains.setdefault(r["source_system"], set()).add(r["dataset"])
    print("\nDomains:", {k: len(v) for k, v in sorted(domains.items())})


if __name__ == "__main__":
    main()
