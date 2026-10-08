# Goldy's Data Explorer — Design

**Status:** In review
**Date:** 2026-10-08
**Scope:** A read-only "data explorer" that lets the owner look through every layer of the data
pipeline — raw ingested records, bitemporal canonical entities, and resolved views — and to drill
from a resolved metric value (the existing provenance panel) into the raw evidence behind it.

## 1. Objective

Today the platform ingests a lot (raw payloads byte-faithful, then canonical entities, then resolved
views), but only the *resolved* layer is browsable — and only through the per-domain screens. There is
no way to inspect the raw records or the canonical rows. The goal is a single owner-facing surface
that can display **all the data that exists now, and data that will exist later**, with the canonical
and resolved layers enumerated from a registry rather than hand-wired per entity.

This is an inspection/audit tool, not a reporting surface: it reads the pipeline's own bookkeeping
(provenance drill-in), never derives business truth for a consumer.

## 2. Sources of Truth and Baseline

- `docs/system-context.md` — the three-layer model (raw → bitemporal canonical → recomputable
  resolved), the "immutable raw ingestion" and "recomputable resolution" invariants.
- `docs/architecture/current-state.md` + `ArchitectureBoundariesTest` — the dependency direction and
  the hard rule that `api` must not read `canonical` or `ingestion.RawLedgerQuery`.
- `docs/superpowers/specs/2026-10-08-provenance-trust-design.md` — the provenance/trust pass; the
  `TrustQuery` pattern (leaf `semantic` interface implemented in `reconciliation`), the `connectors`
  READ permission gate for raw-record references, and the `ProvenancePanel` frontend component.

**Baseline facts:**

- `raw_record` stores every ingested payload byte-faithful, with `source_system`, `fetch_method`,
  `content_type`, `character_encoding`, `fetcher_identity`, `fetched_at`, `payload_sha256`,
  `payload_byte_length`. Read access today is `ingestion.RawLedgerQuery.rawPayloadsForSource` (full
  bytes, backfill-only — no pagination, no filtering, no metadata listing).
- Canonical entities exist for: daily sales, product sales, reservation, invoice, invoice line,
  labour entry, stock count, wastage, sale item, shift — each a bitemporal table (`valid_from`/
  `valid_to`, `recorded_at`/`superseded_at`, plus `source_system`, `source_record_ref`,
  `raw_record_id`, `logical_entity_id`). The last two (`canonical_shift`, `canonical_sale_item`) are
  placeholders with no ingestion source.
- Resolved views exist for: daily sales, product sales, reservation day, labour day, inventory day —
  disposable projections recomputed by projectors.
- `auth.PermissionService.require(role, ResourceKey("connectors"), READ)` already gates raw-record
  references in `ProvenanceController`.

## 3. Scope

### In scope

- A `connectors`-gated read-only data explorer over all three layers.
- Raw: paged, filterable list of raw records (metadata only) + a payload detail view.
- Canonical: a registry of canonical entity types, each listing rows (paged, recent-first) as generic
  rows.
- Resolved: a registry of resolved domains, each listing rows (paged, recent-first) as generic rows,
  with a link to the existing domain screen.
- Frontend `/data` page with Raw / Canonical / Resolved sections; raw-record links from the existing
  provenance panel.
- The registry is the single registration point for future entities/domains.

### Out of scope

- Full-table export / bulk download of raw or canonical data.
- Any write, edit, override, or re-ingestion action from the explorer (strictly read-only).
- Search/full-text over payloads (filtering by source/fetcher/method/date only in v1).
- A staff-facing surface — this is `connectors`-gated (owner/admin).
- Populating or redesigning `canonical_shift` / `canonical_sale_item` (they stay placeholders; see
  §10).

## 4. Placement and read-path compliance

The ArchUnit rule `apiDoesNotReadCanonical` forbids the `api` package from depending on `canonical`
or `ingestion.RawLedgerQuery`. The explorer therefore follows the provenance/trust pattern exactly:

```
api.DataExplorerController            (thin delivery, no canonical/ingestion deps)
   ↓
application.DataExplorerService       (authorization: connectors READ)
   ↓
semantic.DataExplorerQuery            (LEAF interface + value records)
   ↑ implemented by
reconciliation.DataExplorerQueryImpl  (reads canonical + resolved + ingestion.RawRecordBrowseQuery)
```

- `semantic` stays a leaf: `DataExplorerQuery` and its value records have no platform dependencies.
- `reconciliation` is the permitted reader of canonical; it also reads the raw ledger through a new
  `ingestion.RawRecordBrowseQuery` facade (see §5) — `reconciliation → ingestion` is not forbidden by
  any ArchUnit rule.
- No new ArchUnit rule is needed; the existing `apiDoesNotReadCanonical` remains green, which is the
  proof that placement is correct.

## 5. Raw-layer query facade

Add a public read facade in `ingestion` (sibling to `RawLedgerQuery`):

- `list(filter, page, size)` → paged `RawRecordSummary` rows ordered `fetched_at DESC`. `filter` is
  optional `sourceSystem`, `fetcherIdentity`, `fetchMethod`, and a `fetched_at` range.
- `byId(id)` → `RawRecordDetail` = the summary plus the payload bytes and digest.

`RawRecordSummary` carries metadata only (id, source, fetcher, method, content type, character
encoding, fetched-at, byte length, sha-256) — **not** the bytes, so a list page never loads payloads.
The payload is fetched on demand by id.

The repository gains a paged, filtered query (Spring Data `Pageable`); the facade exposes public
records so `reconciliation` never touches the package-private `RawRecord` entity.

## 6. The registry (canonical + resolved)

A registry enumerates entity descriptors at runtime. Each descriptor is:

```
EntityDescriptor { id, label, list(page, size) -> Page<GenericRow> }
```

- `Page` carries `items`, `total`, `page`, `size`; the row count for a descriptor is the `total` of a
  first-page fetch (no separate count query).
- The frontend renders the entity/domain list from `canonicalEntities()` / `resolvedDomains()` — no
  entity names are hard-coded in the UI.
- **Adding a future entity/domain = one new descriptor + one `toGenericRow` mapper.** No controller or
  frontend change.

### Canonical descriptors (registered today)

| id | label | source | notes |
|---|---|---|---|
| `daily_sales` | Daily sales | CTB, Lightspeed | |
| `product_sales` | Product sales | CTB, Lightspeed | |
| `reservation` | Reservations | OpenTable | |
| `invoice` | Invoices | CTB (CSV upload) | |
| `invoice_line` | Invoice lines | CTB (PDF) | |
| `labour_entry` | Labour entries | Deputy | |
| `stock_count` | Stock counts | CTB (deferred) | model present, no source ingested yet |
| `wastage` | Wastage | CTB (deferred) | model present, no source ingested yet |
| `sale_item` | Sale items | — | placeholder, no source |
| `shift` | Shifts | — | placeholder, no source |

### Resolved descriptors (registered today)

`resolved_daily_sales`, `resolved_product_sales`, `resolved_reservation_day`,
`resolved_labour_day`, `resolved_inventory_day` — each resolves to a "jump to existing screen" link
in the UI, plus its row list.

## 7. Row shapes

- **`GenericRow`** = `{ id, columns: Map<String,String> }`. Values are stringified scalars: dates in
  ISO-8601, numbers via their canonical representation, nulls omitted. Common bitemporal columns
  (`source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`,
  `superseded_at`) are included first, then entity-specific columns.
- **Raw detail payload** is returned as pretty-printed JSON when it parses as JSON, otherwise as
  base64, with the content type and sha-256 so the operator can verify byte-faithfulness.
- Ordering is **recent-first**: canonical by `recorded_at DESC`, resolved by the domain's natural key
  (date) `DESC`, raw by `fetched_at DESC`.

## 8. API surface (all `connectors` READ-gated)

| Method + path | Returns |
|---|---|
| `GET /api/data/raw` | paged `RawRecordSummary[]` + total (filter query params) |
| `GET /api/data/raw/{id}` | `RawRecordDetail` |
| `GET /api/data/canonical` | `EntityDescriptor[]` |
| `GET /api/data/canonical/{entity}?page=&size=` | `Page<GenericRow>` |
| `GET /api/data/resolved` | `EntityDescriptor[]` |
| `GET /api/data/resolved/{domain}?page=&size=` | `Page<GenericRow>` |

The controller authorizes `connectors` READ (via the application service) before every call, mirroring
`ProvenanceController.mayReadRawRecords`. A non-owner receives the standard `NOT_PERMITTED` error, not
a partial or filtered result.

## 9. Frontend

- New route `/data` under the existing `(app)` shell, gated on the user's role in the nav (owner
  only), with three tabs: **Raw / Canonical / Resolved**.
- **Raw:** filter controls (source, fetcher, method, date range) + a paged table; expanding a row
  fetches the payload and renders it as formatted JSON (or a base64 note) with the digest.
- **Canonical:** a dropdown of entity types from `canonicalEntities()`, then a paged generic table
  (columns derived from the first row's keys). Placeholder entities render an explicit "no data yet —
  model placeholder" empty state.
- **Resolved:** a dropdown of domains from `resolvedDomains()`, a paged generic table, plus a "open in
  screen" link per domain.
- **Provenance panel:** the existing `rawRecordIds` become links that route to
  `/data?layer=raw&id=<id>` (or open the raw detail inline).
- Reuses the existing table/loading/error components and the `lib/api` `live` + `demo` split; new
  types and `Api` methods are added to both implementations.

## 10. Placeholders and future data

- `canonical_shift` and `canonical_sale_item` are registered as descriptors so the explorer lists
  them, but they render an explicit **empty / placeholder** state (not "no data", which would be
  misleading). A note in the descriptor marks them "model placeholder — ingestion source not yet
  defined"; when those domains are built they must be updated here.
- Because canonical and resolved browsing go through the registry, any new domain added via
  `docs/adding-a-domain.md` is surfaced by adding one descriptor + mapper — the single point of change
  the explorer needs to keep covering "all the data".

## 11. Error handling and empty states

- Permission failure → `NOT_PERMITTED` (never a filtered list).
- Unknown entity/domain id → 404.
- Raw payload that fails to parse as JSON → rendered as base64 with content type, not an error.
- A registered entity with no rows → "no data from this source yet", distinct from a placeholder.
- A bad filter/page value → 400 with the standard `ApiErrorResponse`.

## 12. Testing

- **Architecture:** existing `apiDoesNotReadCanonical` stays green with the new controller/service in
  place (proves the seam). No new rule.
- **Backend:**
  - `RawRecordBrowseQuery` filters by source/fetcher/method/date and paginates; returns no bytes on
    the list path.
  - Registry enumerates all ten canonical entities and all five resolved domains.
  - `canonicalRows` / `resolvedRows` return recent-first generic rows with the shared bitemporal
    columns.
  - Non-owner gets `NOT_PERMITTED` on every endpoint; unknown id → 404.
- **Frontend:** page renders, tab switching, raw filter + payload drill-in, placeholder empty state,
  provenance-panel raw links route correctly.
- **Integration:** list + detail round-trip against the raw ledger and one canonical/resolved table
  (Testcontainers, per the repo invariant).

## 13. Non-goals reaffirmed

Read-only, owner-gated, no export, no search, no writes. The explorer exposes what the pipeline
already records; it never mutates raw, canonical, or resolved state.
