# CTB detail backfill (stocktake / wastage / recipe composition) — design

- **Date:** 2026-10-12
- **Status:** design — awaiting plan. Slice A of a larger food-cost goal.
- **Related:** `docs/connectors/source-access.md`, `docs/connectors/matching-and-identity.md`
  (stock count / wastage identity still deferred), the research repo
  `stirlingwdonaldson/goldys-data-sources` (`cooking-the-books/data/endpoints_catalog.md`),
  and the CTB non-JSON fix (`docs/superpowers/specs/2026-10-12-ctb-recipe-non-json-isolation-design.md`).

## Context

The CTB web pull already ingests, to the raw ledger only, the CTB inventory/purchasing master
data: recipes, stocks, suppliers, stocktakes, wastage, stock orders, statements and reference data
(connector `ctb-revenue`, `CtbConnector.fetch`). Several of those are **header-only** in CTB:

- `Stocktake/SearchStocktakes` → stocktake headers, **no counted lines**.
- `WastageRecord/SearchWastageRecords` → wastage headers, **no line items**.
- `RecipeBook/GetAllRecipes` → recipe `id` + `name` only, **no ingredients**.

The line detail behind those headers is what "actual vs theoretical food cost" needs. That goal is
several independent sub-projects:

| Slice | What | Status |
|---|---|---|
| **A** | **Ingest the line detail (raw ledger only)** | **this spec** |
| B | Canonical stock count (`canonical_stock_count`) | model exists, source deferred |
| C | Canonical wastage (`canonical_wastage`) | model exists, source deferred |
| D | Canonical recipe composition (new domain) | not started |
| E | Usage / food-cost analysis (theoretical vs actual) | not started |

This spec covers **A only**: capture the detail responses in the raw ledger so B–E have their
source. No canonical, no projection, no scheduling, no UI.

## Evidence (live-verified 2026-10-12, read-only, production account)

| Endpoint | Param(s) | Verified result |
|---|---|---|
| `Stocktake/GetStocktakeDetailByStocktakeId` | `stocktakeId`, `start`, `limit` | HTTP 200 JSON; stocktake 38 → `totalCount=413`, `limit=1000` returned all 413. Lines carry `qtyCount`/`qtyCount1‑4`, custom UOM conversions, `stocktakeDetailCost`, `subTotal`, `stocktakeAreaName`, `stockCode`/`stockDesc`, `recipeId`, `supplierName` |
| `Stocktake/GetStocktakeCountDetail` | `stocktakeId` | HTTP 200 JSON; 208 rows (`qtyCount`, `stockDesc`, category, supplier) |
| `WastageRecord/GetWastageRecordDetailByWastageRecordId` | `wastageRecordId` | HTTP 200 JSON; per-item line (`quantity`, `cost`, `reason`, `recipeId`, `unitSize`, `unitOfMeasurementName`, `peopleInvolved`) |
| `RecipeBook/GetIngredientsIncludingSubRecipeIngredients` | `recipeId` | HTTP 200 JSON; ingredient line (`costPerQty`, `costPerUnit`, `portionWeight`, `isSubRecipeIngredient`, `isBrokenLink`, stock linkage) — **one recipe per call** |
| `RecipeBook/GetSelectedRecipeFullRecords` | `recipeIds` (JSON array) **and** `recipeFolderIds` (e.g. `[]`) | HTTP 200 JSON; **663 ids in one call returned 663 records** (124 KB). Fields: `costPerPortion`, `costPerPortionIncludingLabour`, `totalCost`, `portionSize` — CTB's own derived cost |

Not viable / skipped: `Statement/GetStatementDetailByStatementId` returns a 378-byte XHTML page
(not JSON) on both the `Statement/` and `ProformaInvoice/` paths — not ingested (the underlying
invoices are already covered by the CSV/PDF pipeline).

## Goal

Populate the raw ledger with the CTB line-detail responses behind the header-only datasets, once,
byte-faithfully and with a tracked run — so the later canonical slices (B–E) have their source.

## Non-goals

- **No canonicalization** — no `canonical_stock_count` / `canonical_wastage` / recipe-composition
  entity, no projector, no semantic query, no API/tool, no dashboard. Slices B–D.
- **No scheduling.** (The future daily-incremental intent is recorded below.)
- **No stock-order lines.** Purchases come from the authoritative invoice CSV, not from POs;
  order↔invoice matching is a separate feature. Stock-order headers are already ingested.
- **No new web UI**, no operator run button (the flag + startup runner is the trigger).
- No recipe sub-recipe **recursion** beyond what `GetIngredientsIncludingSubRecipeIngredients`
  returns (nested sub-recipes are a slice-D concern).

## Design

### 1. A dedicated backfill connector

Add `CtbDetailBackfillConnector implements SourceConnector` (`connectors/ctb`) with
`sourceSystem() = "CTB"`, `connectorName() = "ctb-detail-backfill"`. `fetch(watermark, sink)`:

1. `client.login(email, password)` (reuses `CtbClient`).
2. Re-fetch the three header lists (existing `CtbClient` methods — `searchStocktakes`,
   `searchWastageRecords`, `getAllRecipes`) and read the ids from their JSON.
3. Fan out, one request per id, writing each response via `ingestSink.accept(...)`:
   - stocktake detail — paged `start`/`limit` (use a high limit, e.g. 1000) until `totalCount`;
   - wastage detail — single request per `wastageRecordId`;
   - recipe ingredients — single request per `recipeId`;
   - recipe cost — **one batched** `GetSelectedRecipeFullRecords` call (`recipeIds` = all ids as a
     JSON array, `recipeFolderIds=[]`).
4. Isolate per id: log + continue on a classified failure (the same pattern as the merged
   `CtbConnector.fetch` isolation), and rethrow an aggregated failure at the end so the run closes
   `PARTIAL`.

**Why a connector, not `ingestPush` per payload:** a `SourceConnector` + `ConnectorRunner` gives
**one run with many raw records**, correct run-status derivation and failure recording for free.
`IngestionService.ingestPush` starts a *new run per payload*, which would add ~810 one-off runs to
the ledger and perturb completeness/cleanliness metrics (the F10 concern).

**Registration:** do **not** register this connector as a Spring bean — `IngestionService` keys its
connector map by `sourceSystem` and a second `CTB` bean would collide. Construct it explicitly in
the runner (below).

### 2. One-off trigger

Add `CtbDetailBackfillRunner` (in `com.goldys.platform.ingestion`, so it can use the package-private
`ConnectorRunner`), an `ApplicationRunner` gated by
`@ConditionalOnProperty(name = "app.ctb-detail-backfill", havingValue = "true")`, mirroring
`CtbInvoicePdfReenrichRunner` / `APP_CTB_INVOICE_REENRICH`. It constructs the connector with the
configured base URL + CTB credentials and calls `connectorRunner.run(connector, null)` (async, like
the scheduled pull). Enabled per deploy, then the env var is unset.

### 3. Raw storage

- One ingestion run: `source_system = CTB`, `connector_name = ctb-detail-backfill`.
- One raw record per response, `fetch_method = API`, `content_type = application/json`,
  `character_encoding = UTF-8`.
- New fetcher identities:
  | Response | `fetcher_identity` |
  |---|---|
  | stocktake detail page | `ctb-stocktake-detail` |
  | wastage detail | `ctb-wastage-detail` |
  | recipe ingredients | `ctb-recipe-ingredients` |
  | recipe cost (batched) | `ctb-recipe-cost` |

### 4. Volume, pacing, failure handling

- ~28 stocktake calls (high `limit`) + 118 wastage + 663 recipe-ingredient + 1 batched cost
  ≈ **~810 requests**. Sequential with a small inter-request delay (be polite; reuse the existing
  request cadence). Expect ~30–60 minutes, one-off — the 30-minute scheduled-pull monitor does not
  apply (this is not the scheduled connector).
- Per-id failure is logged and skipped; the run closes `PARTIAL` with the failed ids summarised.
- Re-running is safe but **duplicates** raw records (the ledger is append-only and does not
  deduplicate on hash). Acceptable for a one-off; the later canonical layers dedupe.

### 5. Docs

- Add the four endpoints + params to `docs/connectors/source-access.md`.
- In `docs/connectors/matching-and-identity.md`, update the `Stock count` and `Wastage` sections:
  the source is now **ingested raw** (still not canonicalized / identity still deferred), and add a
  recipe-composition note.

## Testing

- **Unit (`CtbDetailBackfillConnectorTest`):** with a mock `CtbClient` and a capturing sink — one
  raw write per detail id with the correct `fetcher_identity`; stocktake paging until `totalCount`;
  the batched recipe-cost call uses a JSON-array `recipeIds` **and** `recipeFolderIds`; a per-id
  failure is isolated (other ids still written) and the aggregated failure is rethrown.
- **Integration (Testcontainers):** run the runner against a stubbed connector and assert an
  `ingestion_run` row (`connector_name=ctb-detail-backfill`) exists with the expected raw records
  and identities. Never point at production.
- **Manual live verification (operator):** enable the flag, confirm the raw ledger gains the four
  identities with sensible counts, and check `ingestion_run` closes `SUCCESS`.

## Rollout

Off by default (`app.ctb-detail-backfill=false`). Enable on the production deploy, let the startup
run finish, verify read-only (raw counts + run status), then unset. No migration, no canonical
writes, so rollback is just "don't set the flag".

## Future work (recorded intent — not this slice)

**Schedule this daily, incremental:** pull detail only for **new** ids and for items that have been
**edited**. Open problem: CTB exposes no "modified since" timestamp on recipes/stocktakes/wastage,
so edit detection needs a mechanism — candidates: (a) hash each recipe's ingredient response (and
each stocktake/wastage detail response) and re-pull when the hash changes vs the last stored copy;
(b) the `RecipeBook/GetRecipesHaveNotBeenModifiedBasedOnTimeSetting` /
`GetAffectedRecipesBySettingChanges` endpoints; (c) a bounded rolling window plus a periodic full
refresh. Whichever is chosen, it must run on its **own** schedule/monitor (not the 04:00 pull, whose
~20-minute window and 30-minute monitor cannot absorb ~810 extra calls). Also deferred: recipe
sub-recipe recursion, statement line detail (endpoint not returning JSON), and stock-order lines.

## Open questions

- Bounded batch size / delay for the one-off pass to avoid rate-limiting (none observed; a
  conservative fixed delay is fine).
- Whether `GetIngredientsIncludingSubRecipeIngredients` captures every nested sub-recipe
  ingredient, or only direct ones (slice D will need the full tree).
- Do stocktake detail lines need `GetStocktakeCountDetail` as well, or is the line detail a
  superset? (Both verified; line detail is the richer one.)
