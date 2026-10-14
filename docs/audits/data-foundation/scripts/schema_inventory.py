#!/usr/bin/env python3
"""Derive an authoritative table/column inventory from the Flyway migrations.

This is the schema source of truth for the Phase 1 audit. It is reproducible:
re-run it against any checkout and it reflects that checkout's migrations, so
findings can always be tied to a git commit.

It parses, in version order:
  - CREATE TABLE <name> ( <columns/constraints> )
  - ALTER TABLE <name> ADD COLUMN <col> <type> ...
  - ALTER TABLE <name> RENAME COLUMN <a> TO <b>
  - ALTER TABLE <name> DROP COLUMN <col>
  - DROP TABLE <name>

Output (stdout CSV):
  table, column, native_type, nullable, first_seen_migration, table_kind

Also writes a machine-readable JSON used by the lineage and probe validators.
"""
import csv
import json
import pathlib
import re
import sys

MIGRATIONS_DIR = pathlib.Path(
    __file__).resolve().parents[4] / "backend/src/main/resources/db/migration"
OUT_DIR = pathlib.Path(__file__).resolve().parent.parent

KEYWORDS = {
    "NOT", "NULL", "DEFAULT", "PRIMARY", "KEY", "REFERENCES", "UNIQUE",
    "CHECK", "CONSTRAINT", "COLLATE", "GENERATED", "IDENTITY", "DEFERRABLE",
}
MULTIWORD_TYPES = {
    "character varying",
    "double precision",
    "timestamp",
    "time",
    "bit",
    "interval",
}


def migration_files():
    files = []
    for p in MIGRATIONS_DIR.glob("*.sql"):
        m = re.match(r"V(\d+)__", p.name)
        if m:
            files.append((int(m.group(1)), p))
    return [p for _, p in sorted(files)]


def table_kind(name):
    if name in ("raw_record",):
        return "raw"
    if name.startswith("ingestion_"):
        return "ingestion"
    if name.startswith("canonical_"):
        return "canonical"
    if name.startswith("resolved_"):
        return "resolved"
    if name.endswith("_override") or name in ("resolution_rule",):
        return "reconciliation"
    if name in ("permission", "user_account"):
        return "auth"
    if name.startswith(("classification", "review_classification", "category",
                        "product_category")):
        return "classification"
    if name in ("saved_dashboard", "saved_dashboard_revision",
                "saved_dashboard_share", "conversation_thread",
                "conversation_message", "forecast_run"):
        return "application"
    if name in ("product_alias", "invoice_note", "invoice_pdf_link",
                "invoice_ingest_flag", "llm_extraction_cache",
                "reconciliation_exception"):
        return "evidence"
    return "other"


def split_top_level(body):
    """Split a CREATE TABLE body on commas at paren depth zero."""
    parts, depth, cur = [], 0, []
    for ch in body:
        if ch == "(":
            depth += 1
            cur.append(ch)
        elif ch == ")":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return parts


def parse_column(fragment):
    """Return (name, type, nullable) from a column definition fragment."""
    frag = fragment.strip()
    name = frag.split()[0] if frag else ""
    rest = frag[len(name):].strip()
    nullable = "NOT NULL" not in re.split(r"\s+", rest.upper())
    # Extract the type: leading tokens up to a keyword boundary.
    tokens = rest.split()
    typ_tokens = []
    for t in tokens:
        u = t.upper().rstrip(",")
        if u in KEYWORDS:
            break
        typ_tokens.append(t)
    ntype = " ".join(typ_tokens).strip()
    return name, ntype, nullable


def add_column(cols, table, name, ntype, nullable, migration):
    cols[table][name] = {"native_type": ntype, "nullable": nullable,
                         "migration": migration}


def main():
    cols = {}          # table -> {column -> {native_type, nullable, migration}}
    migration_for = {}
    order = []         # (table, column) insertion order

    for path in migration_files():
        text = path.read_text()
        mig = path.stem

        # CREATE TABLE
        for m in re.finditer(r"CREATE TABLE\s+(?:IF NOT EXISTS\s+)?(\w+)\s*\(",
                             text, re.IGNORECASE):
            table = m.group(1)
            cols.setdefault(table, {})
            migration_for.setdefault(table, mig)
            start = m.end()
            depth = 1
            i = start
            while i < len(text) and depth > 0:
                if text[i] == "(":
                    depth += 1
                elif text[i] == ")":
                    depth -= 1
                i += 1
            body = text[start:i - 1]
            for frag in split_top_level(body):
                # skip constraints / table-level declarations
                first = frag.split()[0].upper().rstrip(",") if frag else ""
                if first in KEYWORDS or first in ("UNIQUE", "EXCLUDE", "FOREIGN"):
                    continue
                if frag.upper().startswith(("PRIMARY KEY", "CONSTRAINT",
                                            "UNIQUE", "CHECK", "FOREIGN")):
                    continue
                name, ntype, nullable = parse_column(frag)
                if name and name not in cols[table]:
                    add_column(cols, table, name, ntype, nullable, mig)
                    order.append((table, name))

        # ALTER TABLE ADD COLUMN — scan each ALTER TABLE statement for every
        # ADD COLUMN it contains (multi-column statements are common here).
        for am in re.finditer(r"ALTER TABLE\s+(\w+)\b", text, re.IGNORECASE):
            table = am.group(1)
            depth = 0
            i = am.end()
            while i < len(text):
                ch = text[i]
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth = max(0, depth - 1)
                elif ch == ";" and depth == 0:
                    break
                i += 1
            stmt = text[am.start():i]
            for cm in re.finditer(r"ADD COLUMN\s+(\w+)\s+", stmt,
                                  re.IGNORECASE):
                name = cm.group(1)
                start = cm.end()
                depth = 0
                j = start
                while j < len(stmt):
                    ch = stmt[j]
                    if ch == "(":
                        depth += 1
                    elif ch == ")":
                        if depth == 0:
                            break
                        depth -= 1
                    elif ch in ",;" and depth == 0:
                        break
                    j += 1
                frag = stmt[start:j].strip()
                nullable = "NOT NULL" not in re.split(r"\s+", frag.upper())
                tokens = frag.split()
                typ_tokens = []
                for t in tokens:
                    if t.upper().rstrip(",") in KEYWORDS:
                        break
                    typ_tokens.append(t)
                ntype = " ".join(typ_tokens).strip()
                cols.setdefault(table, {})
                if name not in cols[table]:
                    add_column(cols, table, name, ntype, nullable, mig)
                    order.append((table, name))

        # ALTER TABLE RENAME COLUMN
        for m in re.finditer(
                r"ALTER TABLE\s+(\w+)\s+RENAME COLUMN\s+(\w+)\s+TO\s+(\w+)",
                text, re.IGNORECASE):
            table, old, new = m.group(1), m.group(2), m.group(3)
            if table in cols and old in cols[table]:
                cols[table][new] = cols[table].pop(old)
                cols[table][new]["migration"] = mig
                for idx, (t, c) in enumerate(order):
                    if t == table and c == old:
                        order[idx] = (table, new)

        # ALTER TABLE DROP COLUMN
        for m in re.finditer(
                r"ALTER TABLE\s+(\w+)\s+DROP COLUMN\s+(\w+)", text,
                re.IGNORECASE):
            table, name = m.group(1), m.group(2)
            if table in cols:
                cols[table].pop(name, None)
                order = [(t, c) for (t, c) in order
                         if not (t == table and c == name)]

        # DROP TABLE
        for m in re.finditer(r"DROP TABLE\s+(\w+)", text, re.IGNORECASE):
            table = m.group(1)
            cols.pop(table, None)
            order = [(t, c) for (t, c) in order if t != table]

    # Emit
    rows = []
    for table, column in order:
        if table not in cols or column not in cols[table]:
            continue
        info = cols[table][column]
        rows.append({
            "table": table,
            "column": column,
            "native_type": info["native_type"],
            "nullable": info["nullable"],
            "first_seen_migration": info["migration"],
            "table_kind": table_kind(table),
        })

    csv_path = OUT_DIR / "schema-inventory.csv"
    with csv_path.open("w", newline="") as f:
        w = csv.DictWriter(
            f, fieldnames=["table", "column", "native_type", "nullable",
                           "first_seen_migration", "table_kind"],
            lineterminator="\n")
        w.writeheader()
        w.writerows(rows)

    # JSON for downstream scripts
    tables = {}
    for r in rows:
        tables.setdefault(r["table"], []).append({
            "column": r["column"],
            "native_type": r["native_type"],
            "nullable": r["nullable"],
        })
    json_path = OUT_DIR / "schema-inventory.json"
    json_path.write_text(json.dumps(tables, indent=2, sort_keys=True) + "\n")

    print(f"Inventoried {len(tables)} tables, {len(rows)} columns "
          f"across {len(migration_files())} migrations -> schema-inventory.csv")


if __name__ == "__main__":
    main()
