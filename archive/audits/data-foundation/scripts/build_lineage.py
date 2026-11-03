#!/usr/bin/env python3
"""Build the field-lineage matrix.

For every canonical / resolved table:
  - enumerate its columns (authoritative, from the schema inventory),
  - classify each canonical column as ``system`` (bitemporal/provenance),
    ``parsed`` (a source field maps onto it), or ``derived`` (computed /
    synthetic / not directly sourced from one raw field),
  - classify resolved columns as ``resolved``.

Source-field "dropped" detection is computed at the *dataset* level (a parser can
feed more than one entity): a source field referenced by the dataset's parser code
that does not map onto any column of any entity that dataset feeds is a candidate
dropped / lossy field.

Matching is normalised, word-suffix based (camelCase / snake_case / multi-word
header insensitive) and is a best-effort first pass: reproducible, but every
``parsed`` / ``dropped`` conclusion carries the evidence file for manual review.

Output: field-lineage.csv
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

SYSTEM_COLUMNS = {
    "id", "logical_entity_id", "source_system", "source_record_ref",
    "raw_record_id", "valid_from", "valid_to", "recorded_at", "superseded_at",
    "resolved_at", "resolution_type", "authoritative_source", "has_conflict",
}

ENTITY_SOURCES = {
    "canonical_daily_sales": ["CtbRevenueParser", "CtbConnector",
                              "LightspeedInsightsCsvParser",
                              "LightspeedAllSalesCsvParser",
                              "LightspeedIngestService"],
    "canonical_product_sales": ["CtbSaleItemParser", "CtbConnector",
                                "LightspeedProductCsvParser",
                                "LightspeedProductIngestService"],
    "canonical_payment": ["LightspeedPaymentCsvParser",
                          "LightspeedPaymentIngestService", "LightspeedPayment"],
    "canonical_deleted_sale": ["LightspeedDeletedSaleCsvParser",
                               "LightspeedDeletedSaleIngestService",
                               "LightspeedDeletedSale"],
    "canonical_sale_item": ["LightspeedSaleItemCsvParser",
                            "LightspeedSaleItemIngestService",
                            "LightspeedSaleItem"],
    "canonical_product": ["LightspeedProductCatalogParser",
                          "LightspeedProductCatalogIngestService",
                          "LightspeedProduct"],
    "canonical_invoice": ["CtInvoiceCsvParser", "CtInvoiceCsvIngestService",
                          "CtInvoice"],
    "canonical_invoice_line": ["CtInvoiceCsvParser", "CtInvoiceCsvIngestService",
                               "CtInvoiceLine", "InvoicePdfEnrichmentService"],
    "canonical_reservation": ["OpenTableReservationsCsvParser",
                              "OpenTableReservationJsonParser",
                              "OpenTableReservationDetail", "OpenTableApiClient"],
    "canonical_review": ["OpenTableReviewJsonParser", "OpenTableReviewsCsvParser",
                         "OpenTableReviewDetail"],
    "canonical_guest": ["OpenTableReviewJsonParser", "OpenTableReviewsCsvParser"],
    "canonical_recipe_cost": ["CtbRecipeCostParser"],
    "canonical_recipe_ingredient": ["CtbRecipeIngredientsParser"],
    "canonical_sale_recipe_link": ["CtbSaleRecipeLinkParser"],
    "canonical_stocktake": ["CtbStocktakeParser", "CtbConnector"],
    "canonical_stock_count": ["CtbStocktakeParser"],
    "canonical_wastage": ["CtbWastageParser"],
    "canonical_labour_entry": [],
}

NON_FIELD = {
    "ctb", "lightspeed", "opentable", "deputy", "success", "partial", "failed",
    "running", "no_new_data", "get", "post", "pdf", "csv", "json", "true",
    "false", "null", "data", "attachment", "meta", "message", "totalcount",
    "id", "source", "status", "value", "tab", "home", "alt", "control",
    "delete", "end", "escape", "enter", "accept", "backspace", "arrowup",
    "arrowdown", "arrowleft", "arrowright", "interrupted", "isset", "issuccess",
    "total", "items", "price", "quantity", "size", "service", "shift", "food",
    "aud", "booked", "seated", "completed", "cancelled", "no_show", "full",
    "empty", "tax_invoice_no",
}


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def split_words(s):
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", s)  # camelCase boundary
    s = s.replace("_", " ").replace("-", " ")
    return [w for w in s.split() if w]


def suffix_candidates(orig):
    """Normalised suffix candidates from a multi-word / camel field name."""
    words = split_words(orig)
    out = []
    for i in range(len(words)):
        c = norm("".join(words[i:]))
        if c:
            out.append(c)
    return out


def find_class(name):
    hits = list(SRC.rglob(f"{name}.java"))
    return str(hits[0].relative_to(REPO)) if hits else None


def extract_source_fields(files):
    found = {}
    header_re = re.compile(r'"([A-Z][A-Za-z0-9]+(?: [A-Z][A-Za-z0-9]+)*)"')
    json_re = re.compile(
        r"(?:->>'([A-Za-z0-9_]+)'|\.(?:get|path|optString|has)\("
        r'"([A-Za-z0-9_]+)"\))')
    for f in files:
        try:
            text = (REPO / f).read_text(errors="replace")
        except OSError:
            continue
        for m in header_re.finditer(text):
            orig = m.group(1)
            n = norm(orig)
            if n not in NON_FIELD and len(n) >= 2:
                found.setdefault(n, (orig, f))
        for m in json_re.finditer(text):
            orig = m.group(1) or m.group(2)
            if not orig:
                continue
            n = norm(orig)
            if n not in NON_FIELD and len(n) >= 2:
                found.setdefault(n, (orig, f))
    return found


def maps_to(source_orig, columns):
    """Match a source field (full name or a word-suffix) to a stored column."""
    col_norms = [(c, norm(c)) for c in columns]
    cands = suffix_candidates(source_orig)
    for cand in cands:
        for c, cn in col_norms:
            if not cn:
                continue
            if cn == cand:
                return c
        # fuzzy prefix/suffix, only for longer tokens to avoid noise
        for c, cn in col_norms:
            if len(cn) >= 4 and len(cand) >= 4 and (
                    cn.startswith(cand) or cand.startswith(cn)):
                return c
    return None


def main():
    schema = json.loads((OUT / "schema-inventory.json").read_text())
    fieldnames = ["source_system", "dataset", "entity_table", "row_type",
                  "name", "classification", "matched_source_field", "evidence"]

    ds_meta = {}          # dataset -> source system
    ds_tables = {}        # dataset -> list of (table, kind)
    for d in datasets.as_dicts():
        key = (d["source_system"], d["dataset"])
        ds_meta[key] = d
        ds_tables.setdefault(key, [])
        for t in [t for t in d["canonical"].split(",") if t]:
            ds_tables[key].append((t, "canonical"))
        for t in [t for t in d["resolved"].split(",") if t]:
            ds_tables[key].append((t, "resolved"))

    rows = []
    for key, tables in ds_tables.items():
        src_system, dataset = key
        d = ds_meta[key]

        # union of source files across all entities this dataset feeds
        files = []
        for t, _kind in tables:
            for n in ENTITY_SOURCES.get(t, []):
                f = find_class(n)
                if f:
                    files.append(f)
        files = sorted(set(files))
        src_fields = extract_source_fields(files)

        # union of all stored columns across the dataset's entities
        all_columns = set()
        for t, _kind in tables:
            all_columns.update(c["column"] for c in schema.get(t, []))

        # map every source field onto a stored column (or None)
        mapped_columns = set()
        for n, (orig, f) in src_fields.items():
            target = maps_to(orig, all_columns)
            if target is not None:
                mapped_columns.add(target)

        # per-column classification
        for t, kind in tables:
            for c in [c["column"] for c in schema.get(t, [])]:
                if kind == "resolved":
                    cls = "resolved"
                elif c in SYSTEM_COLUMNS:
                    cls = "system"
                elif c in mapped_columns:
                    cls = "parsed"
                else:
                    cls = "derived"
                rows.append({
                    "source_system": src_system, "dataset": dataset,
                    "entity_table": t, "row_type": f"{kind}_column",
                    "name": c, "classification": cls,
                    "matched_source_field": "", "evidence": "",
                })

        # dataset-level dropped fields
        for n, (orig, f) in sorted(src_fields.items()):
            if maps_to(orig, all_columns) is None:
                rows.append({
                    "source_system": src_system, "dataset": dataset,
                    "entity_table": "/".join(t for t, _ in tables),
                    "row_type": "source_field_dropped", "name": orig,
                    "classification": "dropped", "matched_source_field": "",
                    "evidence": f,
                })

    with (OUT / "field-lineage.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)

    canon = sum(1 for r in rows if r["row_type"] == "canonical_column")
    resolv = sum(1 for r in rows if r["row_type"] == "resolved_column")
    dropped = sum(1 for r in rows if r["row_type"] == "source_field_dropped")
    print(f"Wrote {len(rows)} lineage rows -> field-lineage.csv "
          f"(canonical={canon}, resolved={resolv}, dropped-candidates={dropped})")


if __name__ == "__main__":
    main()
