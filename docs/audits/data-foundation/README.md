# Phase 1 — Data Completeness & Query-Readiness Audit

**Audit date:** 2026-10-14
**Git commit:** `9d625c7f23c88112302acc0a89d7826a3443f462` (`feat/data-foundation`)
**Schema:** this checkout ships migrations V1–V72 (61 tables, 815 columns).
**Production schema:** V1–V73 — production has one migration this checkout does
not (`V73__drop_raw_record_parsed_payload`), so `raw_record.parsed_payload` is
modelled here but dropped in production. Findings below that depend on live data
were measured read-only against production on 2026-10-14; everything else is
derived from the code in this checkout.

> Rule applied throughout: **current implementation takes precedence over the
> 2026-10-09 production audit.** That audit described a schema of ~34 tables and
> many "not received" domains; the platform has since advanced through V72 and
> most of those claims are now stale (see "Discrepancies vs the 2026-10-09
> audit").

---

## 1. What this audit produces (reproducible)

Everything under `docs/audits/data-foundation/` is generated from the code or
measured read-only; nothing is hand-invented:

| Artifact | Produced by | Source of truth |
|---|---|---|
| `schema-inventory.csv/.json` | `scripts/schema_inventory.py` | Flyway migrations V1–V72 |
| `dataset-inventory.csv` | `scripts/build_inventory.py` + `scripts/datasets.py` | schema inventory + backend source (includes **record grain + source key** per dataset) |
| `field-lineage.csv` | `scripts/build_lineage.py` | schema inventory + parser source-field extraction |
| `findings.csv` | this report | read-only production probes + code |
| `readiness.md` | this report | same |
| `evidence/fixture/*.csv` | `scripts/run_probes.py --container goldys-audit-fixture` | synthetic data |
| `evidence/production/*.csv` | `scripts/run_probes.py --container goldys-prod-postgres-1` | production (read-only) |

Regenerate the code-derived artifacts:

```sh
cd backend && python3 ../docs/audits/data-foundation/scripts/schema_inventory.py
python3 docs/audits/data-foundation/scripts/build_inventory.py
python3 docs/audits/data-foundation/scripts/build_lineage.py
python3 -m unittest discover -s docs/audits/data-foundation/tests
```

The three derived CSVs are committed, but re-running regenerates them from the
current code so findings can always be tied to a commit.

---

## 2. Verification procedure (safe, repeatable)

The probe runner is read-only by construction: it wraps every query in
`BEGIN READ ONLY; … ROLLBACK;` with `default_transaction_read_only=on`,
`statement_timeout=8s`, `lock_timeout=1s`, `idle_in_transaction_session_timeout=15s`
and `psql -X -q --csv -v ON_ERROR_STOP=1` (the same mechanism as
`docs/audits/production-data/checks/probe.py`). It never reads or prints
credentials (they stay in the container environment) and emits only **aggregate
counts, min/max dates and structural metadata** — never scalar supplier, product,
guest, employee, customer or invoice values.

**Fixture first (validates the harness on synthetic data):**

```sh
python3 docs/audits/data-foundation/fixtures/setup_fixture.py     # throwaway postgres:16, all 72 migrations + synthetic rows
python3 docs/audits/data-foundation/scripts/run_probes.py --container goldys-audit-fixture --out docs/audits/data-foundation/evidence/fixture
python3 docs/audits/data-foundation/fixtures/setup_fixture.py --teardown
```

**Production (operator-approved, read-only, aggregate-only):**

```sh
python3 docs/audits/data-foundation/scripts/run_probes.py --container goldys-prod-postgres-1 --out docs/audits/data-foundation/evidence/production
```

The target container is a required argument — the runner never defaults to
production. Probes were validated against the fixture before being run against
production. Live production measurements are recorded under
`evidence/production/`; they are not synthetic.

---

## 3. Dataset coverage

Production retains **30 source datasets** across four sources (32
`(source, fetcher_identity)` pairs; every one of the 26 fetchers present in the
production raw ledger is in the inventory). Live raw-ledger and canonical
populations:

| Domain | Raw records | Canonical (current) | Resolved |
|---|---|---|---|
| Sales (daily) | LS 28 + CTB 1,555 | 1,955 | 1,944 |
| Product sales | (ctb-revenue shared) | 13,849 | 13,849 |
| Payments | 7 | 976,199 | 7,181 |
| Sale items | 46 | 1,462,332 | 19,053 |
| Deleted sales | 7 | 3,207 | 1,009 |
| Reservations | 238 | 24,194 | 1,424 |
| Invoices (CSV) | 17 | 1,200 hdr / 6,291 line | 169 |
| Invoice PDFs | 2,687 | (enrichment) | — |
| Recipes / ingredients | 1 + 663 | 663 / 2,630 | 663 / 2,630 |
| Sale↔recipe links | 44 | 645 | — |
| Reviews / guests | 49 | 1,218 / 172 | (classification) |
| Stocktakes / counts / wastage | 3 / 29 / 119 | **0 / 0 / 0** | — |
| Stocks / suppliers / orders / statements / reference | 21/3/9/3/18 | 0 | — |
| Deputy | 0 | 0 | 0 |

**Received, not yet structured:** stocktakes/counts/wastage, stocks/suppliers/
reference masters, invoice AJAX, menu items, recipes master — these hold real
source data in the raw ledger that has not reached a canonical table.

**Never received:** Deputy (no OAuth token/parser), Lightspeed products,
product catalog, and Z-report (no deliveries).

**Methodological notes (how the audit keeps facts distinct from assumptions):**

- **Keyed on `fetcher_identity`, not connector name.** Connectors and fetchers
  diverge in this codebase (`ctb-invoice-pdf` connector writes `ctb-sftp`;
  `opentable-api` writes `opentable-reservations/reviews/menu-items/menus`;
  `deputy-api` writes `deputy-webhook` — DF-18). The inventory and coverage
  probes join on `raw_record.fetcher_identity`; all 26 production fetchers are
  covered.
- **Grain and keys are first-class.** Each dataset row in
  `dataset-inventory.csv` carries `record_grain` (the source record grain) and
  `source_key` (the source's unique key), so the field-lineage matrix is
  self-contained about identity, not just column shape.
- **Missing is not zero.** Presence (`0 raw` vs `0 canonical`), zero-valued rows
  (`zero_gross`, `unit_cost_zero`) and absent calendar dates (`absent_calendar_dates`)
  are reported as *distinct* measures. Where a zero could be an empty report
  rather than a zero-trading day (14 Lightspeed rows, DF-17), it is flagged as
  ambiguous rather than asserted.
- **Measured vs derived.** Anything from `evidence/production/` is a live
  read-only measurement (aggregate-only). Anything from the schema/lineage
  inventories is derived from this checkout's code and migrations. `evidence/fixture/`
  is synthetic. The three are never conflated in the findings' evidence column.

---

## 4. Critical information loss

Verified against current code and live data (full register in `findings.csv`):

1. **CTB product-sales cost/tax/discount/dimension loss (DF-04).** `canonical_product_sales`
   retains only `product_name_key + quantity_sold + amount`. The source's
   `stockCode`, `totalFoodCost`, `costPerUnit`, `discountedAmount`, `taxAmount`,
   department/outlet and revenue identity are dropped at parse time. The raw
   bytes still carry them (recoverable), but they cannot be queried today.
2. **Invoice header account/tax code and line category are 100% null (DF-05).**
   `account_number` and `tax_code` are null on all 1,200 headers; `category` null
   on all 6,291 lines. Either the parser does not read them or the CSV omits
   them — the raw CSV does contain `SupplierGLCode`/`StockGLCode` candidates.
3. **Sale-item SKU 99.97% null (DF-09).** The Lightspeed sale-items report is
   scheduled *without* the `Product Salelines → Product ID` join (which would
   silently drop ~92% of rows), so SKU is effectively not delivered rather than
   dropped. Product identity relies on `item_name`.
4. **PDF enrichment is nearly unapplied (DF-10).** 2,687 PDFs are retained but
   `invoice_pdf_link` holds 24 rows, leaving UOM/unit-quantity/pack-size/WET
   null on 5,400–6,090 of 6,291 lines. The extraction path exists; it has not
   run broadly in production.

---

## 5. Identity and relationship gaps

1. **Business valid-time is not implemented (DF-07).** Every one of 1.97M
   canonical versions across 8 domains has `valid_from = recorded_at` and
   `valid_to = NULL` — system time only, no real-world effective time. As-of
   business queries are not possible yet (audit F12, still open).
2. **Stocktake/stock-count/wastage received but not canonicalized (DF-03).**
   0 canonical rows despite 3 + 29 + 119 retained raw records — the
   `StockWastageCanonicalizationRunner` has not populated production.
3. **Guest ↔ reservation linkage unwired (DF-08).** 0 of 24,194 reservations
   carry a `guest_id` (deferred name-matching per `matching-and-identity.md`).
4. **product_name_key ↔ stock_code ambiguity (DF-11).** 9 stock codes map to
   multiple product names and 18 names map to multiple codes — no stock master
   to disambiguate, so purchase↔sale joins are name-based only.
5. **Invoice header/line ex-tax disagreement on 97 invoices (DF-06)** persists
   (audit F04) — needs identity/tax/discount/freight reconciliation before
   purchasing totals are trustworthy.
6. **Inconsistent `source_system` value (DF-13).** `canonical_daily_sales`
   carries `LIGHTSPEED_PAYMENTS` as a third source alongside `CTB`/`LIGHTSPEED`,
   which will surprise any source-based resolution logic.

---

## 6. Data that needs only query exposure (no new source inputs)

These are fully canonicalized (and often projected) but lack a consumer-facing
read path — Phase 3/4 work, not Phase 2:

- **sale→recipe links** (645 rows) — `canonical_sale_recipe_link` has no resolved
  projection and no semantic query.
- **guests** (172 rows) — `canonical_guest` has no query interface.
- **stock/count/wastage** once canonicalized (DF-03 fixed) — single-source, so
  they would be read canonical-direct (per the owner's single-source decision),
  but there is no query interface yet.
- **payments / sale-items / deleted-sales** are already projected with semantic
  interfaces; they need a Data Explorer / dashboard / Ask-Goldy's surface to be
  *used*, not new modelling.
- **stocks/suppliers/reference masters** — raw-only; after canonicalization they
  become the supplier/stock master that fixes DF-11 and underpins Phase 2.

## 7. Data requiring new source inputs (cannot be recovered)

- **Deputy timesheets** — no raw payload, no parser, no token. Needs OAuth
  registration + a first real delivery.
- **Lightspeed products / product catalog / Z-report** — zero deliveries; the
  parser contracts exist but no source feed has arrived.
- **Hour/service-level sales** — Lightspeed arrives as daily-reconciled reports;
  atomic transaction timestamps are not in the retained reports, so hour/service
  grain requires a new report/feed configuration at the source.
- **Expected population / trading calendar** — completeness cannot be asserted
  without the venue's trading calendar (the 475 absent Lightspeed dates need
  interpretation: pre-adoption vs closed vs gap).

---

## 8. Discrepancies vs the 2026-10-09 audit

Most "not received / blocked" claims are now stale. Examples:

| 2026-10-09 audit claim | Current reality (2026-10-14) |
|---|---|
| "No ingredient/recipe dataset retained" (Q5) | 2,630 recipe-ingredient + 663 recipe-cost rows, resolved |
| "Reservation tables empty" (Q8) | 24,194 reservations, 1,424 resolved days |
| "No transaction/line/payment facts" (Q1) | 1.46M sale items + 976k payments + 3,207 deleted sales, all projected |
| "Recipes/stock/wastage not received" (F17) | received raw (recipes 3, stocktakes 3, counts 29, wastage 119); recipes canonicalized, stock/wastage still raw-only |
| "10 fixed AI tools / 26 metrics" | 13 `ToolId`s / ~32 `MetricId`s |
| `parsed_payload` dropped in V68 (memory) | dropped in **V73** (production flyway history); this checkout is at V72 |

**Still-open audit findings confirmed by this audit:** F04 (97 header/line
mismatches), F06/F09 (ingest stage accounting — now observable via
`ingestion_stage`, but revenue still failing), F12 (valid-time unimplemented),
F13 (category null).

---

## 9. Smallest defect set to fix before Phase 2

Phase 2 (identity & relationship repair) cannot build on unstable keys. The
minimum pre-Phase-2 corrections, in order:

1. **DF-03 — canonicalize stocktake/stock-count/wastage** (received, 0 rows):
   the parser and schema exist; the canonicalization runner has not produced
   rows in production. Without it, inventory/consumption questions stay blocked.
2. **DF-06 — reconcile the 97 header/line invoice mismatches** and pin the
   invoice identity: supplier-scoped key + line-ordinal policy (audit F03/F04/
   F05). This is the load-bearing identity for every purchasing join.
3. **DF-04 — retain CTB product `stockCode`/cost/tax/discount** at the received
   grain (new source-product-day fact, replayable from retained raw). Required
   before any margin/discount/tax analysis.
4. **DF-07 — establish a valid-time contract** (even `valid_from = business date`
   for date-grain facts) so as-of semantics exist before relationship repair.
5. **DF-02 — stop treating raw `SUCCESS` as transform success** in freshness;
   surface the `ingestion_stage` PARSED/CANONICALIZED failures (already
   recorded) in connector health, and stop the CTB revenue failures (DF-01).

Items deferred to Phase 3/4 (query exposure only): sale↔recipe links, guests,
stock/count/wastage query interfaces, and the payments/sale-items dashboard
surface.

---

## 10. Files changed and tests executed

**Files added (all under `docs/audits/data-foundation/`):**

- `README.md` (this report)
- `findings.csv`, `readiness.md`
- `scripts/schema_inventory.py`, `scripts/datasets.py`, `scripts/build_inventory.py`, `scripts/build_lineage.py`, `scripts/run_probes.py`
- `probes/s1_schema.sql`, `probes/c1_coverage.sql`, `probes/c2_stage.sql`, `probes/i1_integrity.sql`, `probes/i2_identity.sql`, `probes/t1_temporal.sql`
- `fixtures/setup_fixture.py`, `fixtures/fixture.sql` (synthetic data only)
- `tests/test_audit_harness.py`
- Generated: `schema-inventory.csv/.json`, `dataset-inventory.csv`, `field-lineage.csv`
- Evidence: `evidence/fixture/*.csv` (synthetic), `evidence/production/*.csv` (read-only live)

**Tests executed:**

- `python3 -m unittest discover -s docs/audits/data-foundation/tests` → **8 passed**
  (schema inventory shape, probe-table grounding, dataset-inventory consistency,
  field-lineage coverage).
- Fixture end-to-end: all 72 migrations applied to a throwaway Postgres, synthetic
  fixtures loaded, and all six probe sets executed cleanly (no SQL errors).
- Production: all six probe sets executed read-only (aggregate-only) against
  `goldys-prod-postgres-1`.

**Safety:** no code or schema was modified, no migration/backfill/ingestion was
run, no credentials or private values were read or exported, no source systems
were contacted. Only reviewed `SELECT`-only probes through the established
read-only wrapper were executed against production.
