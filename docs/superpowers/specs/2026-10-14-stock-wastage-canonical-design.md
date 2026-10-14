# Stock & wastage canonicalisation — design

- **Date:** 2026-10-14
- **Status:** design — not yet implemented.
- **Related:** `docs/connectors/matching-and-identity.md` (stock-count / wastage identity),
  `docs/connectors/source-access.md` (ingestion + scope limitations),
  `docs/superpowers/specs/2026-10-12-recipe-ingredients-design.md` (the closest prior art),
  `docs/architecture/adding-a-domain.md` (domain recipe — this slice implements only its canonical leg).

## Context

The raw ledger already holds the CTB stocktake and wastage **detail** backfilled by the one-off
`ctb-detail-backfill` run (2026-10-12), but they have never been canonicalised: `canonical_stock_count`
and `canonical_wastage` were created in V22 as "modelled, no ingestion source" placeholders and remain
**0 rows**. This slice formalises those two entities and adds the missing stocktake **header** entity,
then wires raw → canonical so the data is no longer "unprocessed".

Scope is deliberately **canonical layer + ingest only** — no projection, no resolved read model, no
semantic query, no REST endpoint, no Ask Goldy's tool, no UI. Those are a later slice (the recipe
ingredients slice did all of these at once; we are splitting them out because the resolved inventory
day already exists and will be wired separately).

CTB is the single authority for stocktakes and wastage; there is no second source, so this is a
**single-source** domain (no reconciliation engine, no override table this slice).

## Verified source facts (production ledger, read-only, 2026-10-14)

All fetchers are `source_system = CTB`. Envelope is `{"data":[…],"totalCount":n,"message":{…}}`.

| Fetcher | Raw records | Shape | source_record_ref |
|---|---|---|---|
| `ctb-stocktakes` | 3 (re-pulled daily) | 27 stocktake headers | `null` (written by `CtbConnector.pullRaw`) |
| `ctb-stocktake-detail` | 29 (paged, 1000/page) | 11,066 counted lines | `"stocktake:<stocktakeId>"` |
| `ctb-wastage` | 3 (re-pulled daily) | wastage headers | `null` |
| `ctb-wastage-detail` | 119 (one per record) | 359 waste lines | `"wastage:<wastageRecordId>"` |

### Stocktake header (`ctb-stocktakes`) fields

`stocktakeId`, `stocktakeDate`, `stocktakeDesc`, `outletName`, `totalStockValue`, `isPartial`,
`isLockStocktake`, `createdDate`, `createdBy`, `hasActiveTemplate`, `isOwnedByCurrentOutlet`,
`stocktakeTemplates` (array). The **date lives only here** — the detail payload has none.

### Stocktake detail (`ctb-stocktake-detail`) fields

`id` (per-stocktake line id), `stocktakeDetailId` (**not** a line id — see identity below), `stockId`,
`stockCode`, `stockDesc`, `qtyCount` + `qtyCount1..4` (custom-UOM counts), `stockUnit`,
`stockUnitOfMeasurementName`, `stocktakeDetailCost`, `subTotal`, `supplierId`/`supplierName`,
`stocktakeAreaId`/`stocktakeAreaName`, `recipeId`, `stockCategoryId`/`stockCategoryName`,
`stockParLevel`, plus template/weight/barcode fields we do not store.

### Wastage detail (`ctb-wastage-detail`) fields

`wastageRecordId`, `wastageRecordDetailId`, `wastageRecordName`, `recordDate`, `stockDesc`,
`quantity`, `cost`, `reason`, `recipeId`, `supplierName`, `unitOfMeasurementName`, `unitSize`,
`stockUnit`, `stockSupplierId`, `peopleInvolved`, `isActive`. The date lives **on the line**
(`recordDate`), so wastage is self-contained — no header entity is needed.

### Identity findings (measured, not assumed)

- `stocktakeDetailId` has only **4 distinct values** across 11,066 lines — it is a template/detail
  reference, not a line id. The per-line id is the `id` field: **972 distinct, unique only within a
  stocktake** (`(stocktakeId, id)` is 11,066/11,066 unique). This is the same "owner-scoped line id"
  problem the recipe-ingredient slice solved — the parent must anchor the identity.
- `wastageRecordDetailId` is **globally unique** (359/359); identity still anchors on the parent for
  consistency and safety.
- Wastage carries **no stock code** — product identity is `product_name_key` from `stockDesc` only.

### Data quirks handled

1. **Literal `\u0000` escapes** in CTB strings — stripped before any `text`/`varchar` write.
2. **Header re-pull accumulation** — `ctb-stocktakes`/`ctb-wastage` are re-pulled daily and the ledger
   is append-only, so the canonicalization runner must **dedupe headers to the latest per id**
   (requires `fetched_at` ordering, see Design).
3. **No date on stocktake detail** — `counted_date` is resolved from the canonical header via
   `stocktake_id`; a line whose header is absent is a hard failure, never a silent skip.
4. **Custom-UOM counts (`qtyCount1..4`)** — not stored v1; `quantity_on_hand = qtyCount` (base unit).

## Scope (v1)

**In:** `canonical_stocktake` (header), enriched `canonical_stock_count`, enriched `canonical_wastage`,
plus parser + flag-gated canonicalization runner replaying the existing raw ledger.

**Out (explicit, not silently dropped):**

1. **Projection / resolved read model** — `resolved_inventory_day.stock_on_hand`/`wastage` stay null;
   wiring them is a separate slice.
2. **Semantic / REST / Ask Goldy's / UI** — deferred.
3. **Ongoing/incremental freshness** — v1 backfills from the *existing* raw ledger only; the one-off
   `ctb-detail-backfill` is not scheduled (Slice-A future work, shared with recipe ingredients).
4. **Custom-UOM counts (`qtyCount1..4`)** and weight/barcode/template fields — not modelled yet.
5. **Wastage header entity** — the detail is self-contained; the thin header (no date) adds no value yet.

## Design

Mirrors the recipe-ingredient slice's canonical leg and the invoice header+line pattern.

### Migration `V68__stock_wastage_canonical.sql`

- **Create `canonical_stocktake`** (bitemporal: id, logical_entity_id, source_system,
  source_record_ref, raw_record_id, valid_from/to, recorded_at, superseded_at) plus:
  - `stocktake_id integer NOT NULL`
  - `stocktake_date date NOT NULL`
  - `description varchar(512)`
  - `outlet_name varchar(255)`
  - `total_stock_value numeric(14,4)`
  - `is_partial boolean`
  - `is_locked boolean`
  - unique-current `uq_stocktake_current_source_fact (source_system, source_record_ref)`; index on
    `stocktake_id`.
- **Alter `canonical_stock_count`** (keep `counted_date`, `product_name_key`, `quantity_on_hand`), add:
  - `stocktake_id integer NOT NULL`
  - `line_id integer NOT NULL`
  - `stock_id integer`
  - `stock_code varchar(255)`
  - `unit_of_measurement_name varchar(64)`
  - `unit_cost numeric(14,4)` (`stocktakeDetailCost`)
  - `line_value numeric(14,4)` (`subTotal`)
  - `supplier_name varchar(512)`
  - `area_name varchar(255)` (`stocktakeAreaName`)
  - `recipe_id integer`
  - unique-current `uq_stock_count_current_source_fact (source_system, source_record_ref)`; index on
    `stocktake_id`.
- **Alter `canonical_wastage`** (keep `wastage_date`, `product_name_key`, `quantity`, `reason`), add:
  - `wastage_record_id integer NOT NULL`
  - `line_id integer NOT NULL`
  - `cost numeric(14,4)`
  - `supplier_name varchar(512)`
  - `unit_of_measurement_name varchar(64)`
  - `unit_size varchar(64)`
  - `recipe_id integer`
  - unique-current `uq_wastage_current_source_fact (source_system, source_record_ref)`; index on
    `wastage_record_id`.

Both altered tables are empty (0 rows), so adding `NOT NULL` columns is safe. CTB ids are `integer`
(matching `canonical_recipe_ingredient`).

### Canonical entities (`canonical/`)

- `CanonicalStocktake` (new) + `StocktakeInput` / `StocktakeRecorded` / `StocktakeView` +
  `CanonicalStocktakeRepository` / `CanonicalStocktakeService` / `CanonicalStocktakeIngest`, mirroring
  `CanonicalInvoice`.
- Extend `CanonicalStockCount` / `CanonicalWastage` with the new fields, and add the missing
  `StockCountInput` / `WastageInput` + `Ingest` / `Service` / `Repository` facades (they have none
  today — only the entity exists).

### Parsers (`connectors/ctb/`)

- `CtbStocktakeParser` — parses both header (`ctb-stocktakes`) and detail (`ctb-stocktake-detail`)
  envelopes into `StocktakeInput` / `StockCountInput`. The header parser reads `stocktakeId` from the
  payload (the raw `source_record_ref` is null); the detail parser derives `stocktakeId` from
  `source_record_ref = "stocktake:<id>"`.
- `CtbWastageParser` — parses `ctb-wastage-detail` into `WastageInput`.
- Both use the recipe parser's defensive `integer`/`decimal`/`bool`/`sanitize` helpers and collapse
  exact-duplicate lines (LinkedHashSet). Non-JSON throws `CONNECTOR_SCHEMA_MISMATCH`.

### Canonicalization runner (`connectors/ctb/`)

`StockWastageCanonicalizationRunner` (`@Order(HIGHEST_PRECEDENCE)`,
`@ConditionalOnProperty(app.stock-wastage-canonicalize=true)`, idempotent via `source_record_ref`),
mirroring `RecipeIngredientCanonicalizationRunner`:

1. Parse `ctb-stocktakes` records, **dedupe to the latest per `stocktakeId`**, record
   `canonical_stocktake` rows.
2. Build a `stocktakeId → date` map from the recorded headers.
3. Parse `ctb-stocktake-detail`, derive `stocktakeId` from `source_record_ref`, resolve `counted_date`
   from the map, record `canonical_stock_count`. A line whose `stocktakeId` has no header is collected
   and rethrown (run closes `PARTIAL`), never silently skipped.
4. Parse `ctb-wastage-detail`, record `canonical_wastage`.

**`RawLedgerQuery` addition** — `RawPayloadWithRef` drops `fetched_at`, so add an
`fetched_at`-ordered finder (the repository already has `OrderByFetchedAtDesc` in `latestDigest`)
returning `(id, bytes, sourceRecordRef, fetchedAt)` ordered ascending; the runner keeps the last
(most recent) header per id. This is required for correct dedup across the daily re-pulls.

### Matching & identity (update `matching-and-identity.md`)

| entity | source_record_ref | logical identity (`UUID.nameUUIDFromBytes`) |
|---|---|---|
| stocktake header | `stocktake:<id>` | `"stocktake:" + id` |
| stock count line | `stocktake:<id>:<lineId>` | `"stock-count:" + id + ":" + lineId` |
| wastage line | `wastage:<recordId>:<detailId>` | `"wastage:" + recordId + ":" + detailId` |

All single-source `CTB`, deterministic, no cross-source matching. Re-record semantics: unchanged →
no-op; changed → close + reinsert (bitemporal supersede on `(source_system, source_record_ref)`).

## Non-goals

- Projection, resolved read model, semantic, REST, tool, UI (deferred slice).
- Ongoing/incremental freshness (the detail backfill is one-off).
- Custom-UOM counts, weight/barcode/template fields, wastage header entity.
- Cross-source reconciliation or manual overrides (single-source domain).

## Testing

Per `adding-a-domain.md` §4, canonical-leg only:

- **Parsers** — envelope handling, field mapping, `\u0000` strip, defensive numeric parse (garbage →
  null), exact-duplicate collapse, missing header/date, non-JSON → `CONNECTOR_SCHEMA_MISMATCH`.
- **Canonical ingest** — new fact, supersession on change, same-fact no-op, deterministic logical id,
  batch single event.
- **Runner** — replay, header dedup-to-latest (ordering), date resolution, hard-fail on a line with no
  header, unparseable `source_record_ref` skip, idempotency.
- **`RawLedgerQuery`** — `fetched_at` ordering.
- **Migration** — `canonical_stocktake` created; `canonical_stock_count` / `canonical_wastage` gain the
  new columns and unique-current indexes; `hibernate.ddl-auto=validate` passes.
- `ArchitectureBoundariesTest` — unchanged (no new package); extend only if a new package is introduced.

Run the fast lane (`./gradlew fastTest`) while iterating, and the full `./gradlew test spotlessCheck`
gate before finishing. Integration tests use Testcontainers (Docker required) — mirror
`CanonicalReservationIntegrationTest`.
