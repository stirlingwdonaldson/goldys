# Lightspeed menu source (product catalog) — design

- **Date:** 2026-10-13
- **Status:** design — approved 2026-10-13; awaiting implementation plan.
- **Related:** `docs/superpowers/specs/2026-10-12-recipe-composition-design.md` (the domain this
  corrects), `docs/superpowers/specs/2026-10-13-recipe-costing-gap-design.md` (price provenance on
  the same read model), `docs/connectors/pos-menu-reconciliation-2026-10-13.md` (the reconciliation
  evidence and alias-table seed), `docs/connectors/matching-and-identity.md`,
  `docs/connectors/source-access.md`.

## Context

The recipe "on menu" signal (`resolved_recipe.menu_state`, `ON_MENU`/`OFF_MENU`/`UNKNOWN`) is
derived **only** from CTB's `isRecipeActive` flag on `canonical_sale_recipe_link`. That flag is
stale in both directions: it marks items that are still sold as `OFF_MENU` (the original bug), and
it marks dropped items as active (e.g. `Coke Zero - Mixer`, last sold 2026-09-19 but CTB still
`is_recipe_active = true`).

The venue's authoritative answer to "what is currently sellable" is the **Lightspeed product
export** (a CSV of all products on the POS). It is pulled *by hand, when the menu changes* — not on
a schedule, and not daily. This spec makes that CSV the source of truth for menu membership and
derives `menu_state` from it, instead of trusting CTB.

## Evidence — the ambiguities that shaped this

A hand reconciliation of the 2026-10-13 product export against the production DB
(`docs/connectors/pos-menu-reconciliation-2026-10-13.md`) surfaced the real cost of the current
name-only join. The same product is spelled differently across CTB, Lightspeed receipts, and the
POS export in at least eight ways:

| Class | Example |
|---|---|
| Word spacing | `Deepwoods` vs `Deep Woods` |
| Serve-format prefix | `G -` (by-glass) vs `B -` (bottle) |
| Suffix | `Mustard Gravy` vs `Mustard Gravy - Side` |
| Qualifier | `Turkish bread` vs `Half Turkish bread` |
| Synonym | `Kiddies`↔`Kids`, `Eggplant Dip`↔`Baba Ganoush`, `Corn Estiques`↔`Charred Corn Salad` |
| Size-token reorder | `Keeper Pilsner - Jug` vs `Jug - Keeper Pilsner` |
| Vintage / label rebrand | `(NV Continental)`↔`(NV Three Pillars)`, `45ml`↔`30ml` |
| Name shortening | `Katsaros Brothers Ouzo` vs `Ouzo` |
| Genuinely different | `Love Shack Red Ale` vs `Love Shack Pale Ale` |

`ProductNameKey.normalize` (case, punctuation, whitespace, leading `"New "`) does **not** collapse
any of these. The reconciliation recorded the actual mapping for each, which becomes the seed of a
maintained alias table (below).

Key facts that constrain the design:

- **No stable ID exists in the automated sources.** `canonical_sale_item.product_number` is a
  category code (`WINEBTG`/`WINEBTB`/`TKWY`), `sku` is empty, and CTB carries only its own
  `stock_code` + `sale_item_stock_description`. The Lightspeed CSV is the one source with a stable
  `ProductID` (integer).
- **The CSV has no active/archived flag** — presence is the signal. It does distinguish modifiers
  (`IsModifier`) and carries `SellPriceIncTax` (zero-price = donations/modifiers/functions), so
  "sellable menu item" is filterable from "present".
- **Out-of-scope:** stock. There is no daily stock signal; "temporarily out of stock" is not
  observable and is deliberately not modelled (see Non-goals).

## Goal

Ingest the Lightspeed product CSV as a **snapshot-versioned** source of truth for menu membership,
canonicalize it, match recipe links to products by name (normalized + alias table, with a
`ProductID` fast-path), and derive `resolved_recipe.menu_state` from product presence instead of
CTB's `isRecipeActive`.

## Non-goals

- **No stock / out-of-stock state.** Only `ON_MENU` (present) vs `OFF_MENU` (absent) is modelled.
- **No "recently sold" proxy.** Sales recency is not used to infer menu membership; the CSV is the
  authority.
- **No daily automation.** Ingestion is on-demand, when the menu changes.
- **No change to price/cost enrichment.** `menu_price`/`actual_cost` continue to join
  `canonical_sale_item` by name (unchanged); this spec only changes `menu_state` and its authority.
- **No OpenTable cross-check in this slice** (recorded as future work).

## Design

### 1. Migration (`V59__lightspeed_product_catalog.sql`)

**`canonical_product`** — the Lightspeed product catalog, one row per product per snapshot version
(bitemporal supersession; "current" = latest snapshot):

- `id uuid PK`, `logical_entity_id uuid NOT NULL`, `source_system varchar(255) NOT NULL`,
  `source_record_ref varchar(512) NOT NULL`, `raw_record_id uuid NOT NULL REFERENCES raw_record(id)`,
  `product_id integer NOT NULL`, `product_name varchar(512)`, `category varchar(255)`,
  `sell_price_inc_tax numeric(14,4)`, `is_modifier boolean`, `reporting_group varchar(255)`,
  the standard `valid_from` / `valid_to` / `recorded_at` / `superseded_at` columns.
- `source_record_ref = 'lightspeed-product:' || product_id`.
- `logical_entity_id = UUID.nameUUIDFromBytes("lightspeed-product:" + productId)` — the ID is the
  stable identity, so a rename (same `ProductID`) supersedes in place rather than forking.
- Unique index `(product_id) WHERE superseded_at IS NULL`; a secondary index on `product_name`.

**`product_alias`** — the maintained name-matching table:

- `id uuid PK`, `canonical_name varchar(512) NOT NULL` (the normalized `ProductNameKey` of the
  Lightspeed product), `alias varchar(512) NOT NULL` (a normalized variant that should resolve to
  it), `source varchar(32)` (`CTB`/`POS`/`MANUAL`), `created_at`.
- Unique `(canonical_name, alias)`. Seeded from
  `docs/connectors/pos-menu-reconciliation-2026-10-13.md`.

**`canonical_sale_recipe_link`** gains `lightspeed_product_id integer` (nullable) — the `ProductID`
fast-path, populated after a confident name match. Null until matched; never the *only* key.

### 2. Parser + ingest facade + drop endpoint

- `LightspeedProductCatalogParser` — parses the CSV header-driven (like
  `OpenTableMenuItemsCsvParser`): requires `ProductID`, `ProductName`; reads `CategoryNames`,
  `SellPriceIncTax`, `IsModifier`, `ReportingGroup`. Fails loudly on a changed shape
  (`CONNECTOR_SCHEMA_MISMATCH`).
- `LightspeedProductCatalogIngestService` — persists the CSV byte-faithfully as a raw record
  (`source_system=LIGHTSPEED`, `fetcher_identity=lightspeed-product-catalog`), then records each
  row into `canonical_product` via the standard close-and-reinsert supersession. **Snapshot
  versioning:** the whole CSV is one snapshot. After recording every row *present* in the new CSV,
  the ingest service runs a second, explicit step that closes (supersedes) any current
  `canonical_product` row whose `product_id` was **absent** from the new CSV — turning "absent" into
  a closed validity period rather than a dangling current row. No delete — history is retained, and
  "current" always equals the latest snapshot.
- Drop endpoint `POST /api/connectors/lightspeed-products/upload` (multipart, OIDC +
  `connectors:write`, 204) — mirrors `POST /api/connectors/opentable-menu-items/upload`. Not
  scheduled; invoked when the venue exports a new menu.

### 3. Recipe-link → product matching

On ingest (and on demand), match each current `canonical_sale_recipe_link` to a `canonical_product`:

1. **ID fast-path:** if the link already has `lightspeed_product_id` and that `product_id` exists in
   the current snapshot, reuse it (no name match).
2. **Normalized name match:** `ProductNameKey.normalize(sale_item_stock_description)` vs
   `ProductNameKey.normalize(product_name)`.
3. **Alias match:** consult `product_alias` (both directions) for the residual synonym/rebrand
   cases.
4. **Confidence gate:** auto-link only exact/alias matches; everything else is flagged for manual
   review (the same eyeball pass that produced the reconciliation doc), and until reviewed the
   recipe keeps `UNKNOWN` rather than guessing `OFF_MENU`.

### 4. Projector change (`RecipeProjector`)

`menu_state` is derived from product presence, not `isRecipeActive`:

- linked product present (and sellable: `is_modifier = false`, non-zero price) in the current
  snapshot → `ON_MENU`.
- linked product absent from the current snapshot → `OFF_MENU`.
- no product match (unmatched link or no link) → `UNKNOWN`.

`price_source`/`menu_price`/`actual_cost` enrichment is unchanged (still name-matched against
`canonical_sale_item`). CTB's `isRecipeActive` is no longer read for `menu_state`; the flag remains
stored on the link but is demoted to a non-authoritative field.

### 5. Alias table as the durable matching fix

`product_alias` is the mechanism that prevents this session's manual reconciliation from needing to
be repeated. It is seeded once from the reconciliation doc and extended whenever a new mismatch is
found during a menu update.

## ProductID — future risks (explicitly noted)

Storing `lightspeed_product_id` is a fast-path, and the following are known risks to monitor on the
first few snapshots:

1. **Rename vs delete-and-recreate is unknown.** If Lightspeed renames in place, the `ProductID`
   survives and the ID-link is stable across renames. If it *retires and recreates* (new ID), the
   stored ID goes dead and the link would falsely read `OFF_MENU` — which is why the ID is a
   fast-path with name fallback, not the sole key.
2. **The initial name match bakes in the risk.** A wrong auto-match fixes a wrong ID. The
   confidence gate (step 4) exists precisely to keep low-confidence matches out of the fast-path.
3. **The ID only bridges recipe ↔ CSV.** Price/cost still joins `canonical_sale_item` by name, so
   two join keys coexist in the projector and must be kept distinct.
4. **Uniqueness/stability to verify.** Confirm `ProductID` is unique and persists through a real
   menu change (diff snapshot 1 vs 2) before relying on it broadly.

## Testing

- Parser: required columns, `IsModifier`/price parsing, changed-shape failure.
- Ingest: snapshot versioning (second snapshot supersedes), absent-product retirement, byte-faithful
  raw record, idempotent re-ingest of the same CSV.
- Matching: exact normalize, alias resolution, ID fast-path, low-confidence → `UNKNOWN` (no false
  `OFF_MENU`).
- Projector: `ON_MENU`/`OFF_MENU`/`UNKNOWN` from presence; price/cost enrichment unchanged.
- Integration via Testcontainers; never against production.

## Rollout

1. Deploy `V59` (Flyway owns the schema; `hibernate.ddl-auto=validate`).
2. Seed `product_alias` from the reconciliation doc.
3. Ingest the current product CSV (one drop), which records the snapshot and runs matching.
4. Rebuild `resolved_recipe` (projector/startup seeder); verify the still-sold items that were
   wrongly `OFF_MENU` now read `ON_MENU`, and the confirmed retirements read `OFF_MENU`.
5. Going forward: the venue drops a fresh CSV on each menu change.

## Decisions & dependencies

- **The Lightspeed product CSV is the source of truth** for menu membership; CTB's `isRecipeActive`
  is demoted to non-authoritative.
- **Snapshot-versioned**, not incremental: each CSV is a full snapshot; history is retained,
  "current" is the latest.
- **`ProductID` is a fast-path with name fallback**, never the sole key (see the future-risks note).
- **No sales-proxy, no stock** — presence (filtered to sellable) is the whole model.

## Future work (recorded intent)

- Corroborate `menu_state` with OpenTable's `Enabled`/`Archived` (already ingested raw) as a second
  signal.
- Add a stock/out-of-stock state if a daily stock source appears.
- If Lightspeed proves to keep `ProductID` stable across renames, promote the ID to the primary key
  and retire the name join for `menu_state`.
