---
paths:
  - "backend/**"
---

# Backend architecture invariants

These are pinned in `docs/system-context.md` and govern the Phase 1 rebuild.
The cleared baseline does not contain the classes named below until an approved
implementation task restores them. Violating an invariant is a design
regression, not a style nit — treat it that way even under time pressure.

## Raw log (`ingestion/RawRecord.java`)

- Every ingested payload — API JSON, CSV/XLSX, scraped HTML, manual entry —
  lands in `raw_record` byte-faithful, before any parsing/transformation.
- Rows are never updated or deleted by application code. A correction is a
  new row, not an edit.
- All source shapes use the same raw-record ledger rather than source-specific
  staging tables. Authoritative bytes live in `BYTEA` with a SHA-256 digest and
  byte length; JSONB, when present, is derived and never the audit source.
- Connector failures go to `IngestionFailure`, not silence. `failureType` is
  a plain string on purpose (new connectors can report unanticipated failure
  modes without a schema change) — don't tighten it to an enum without
  checking whether that constraint is still wanted.

## Bitemporal canonical layer (`canonical/BitemporalEntity.java`)

- Two independent time axes: `validFrom`/`validTo` (when the fact was true in
  the real world) and `recordedAt`/`supersededAt` (when we recorded/replaced
  it). Don't collapse these into one timestamp pair.
- Never `UPDATE` a canonical row in place when a later raw record supersedes
  it. Close the old row (`supersede()`) and insert a new one. This is what
  makes "query as of a past system time" and rule recomputation possible.
- New canonical entity types use the shared `BitemporalEntity` pattern once it
  is restored — don't invent a parallel versioning scheme.
- The V1 `canonical_shift` and `canonical_sale_item` columns are placeholders
  pending entity-matching design. Don't treat their shape as a final matching
  contract.

## Permission model (`auth/`)

- `PermissionService` is the *only* enforcement point once restored. Don't add
  a second, parallel permission check anywhere else — reconciliation UI and
  (Phase 2) AI tool calls both route through it.
- Permissions are table-driven `(Department, Seniority, resource)` rows, not
  named roles. Adding a new `Department` or `Seniority` value must never
  require editing an existing `Permission` row — only new rows.
- Do not encode `OWNER > MANAGER > STAFF` as an ordinal/numeric shortcut in a
  permission check. Owner's broader access comes from explicit `Permission`
  rows (often via `Department.ALL`), not a hardcoded bypass.
- A denied check must throw/return an explicit `AccessDeniedException` —
  never a silently filtered or partial result.
- `Permission` rows ship empty in V1. Don't populate them with
  guessed field-to-role mappings — that's an open question requiring
  stakeholder input (see `docs/prd.md`'s Open Questions).

## Connectors (`ingestion/port/SourceConnector.java`)

- Every vendor integration implements the one `SourceConnector` port. If
  adding a connector forces a change to that interface, the interface is
  leaking a vendor-specific concern — reconsider the change instead of the
  interface.
- A `fetch()` implementation must report failures via `IngestionFailure`
  (see above), never throw silently or just return zero rows.
- Check `docs/system-context.md`'s "Per-Source Ingestion Reality" table
  before assuming an API exists — Lightspeed, CTB, and OpenTable all route
  through scrapes/exports, not clean REST APIs, in Phase 1.

## AI tools / widgets (Phase 2 contract artifacts only)

- No Phase 2 runtime is implemented in Phase 1. Versioned documentation and
  schema artifacts lock the boundary for later modules.
- Every future tool takes a bounded, enum-validated parameter set. Never add a
  tool whose parameters accept an arbitrary
  field name or free-text filter expression — that's freeform query
  generation with extra steps, which the tool boundary exists to prevent.
- Every future tool dispatcher enforces `PermissionService` before dispatch;
  no tool-calling path may bypass it.
- Reporting/dashboards (once they exist) read only from resolved views,
  never canonical or raw tables directly, even before the full Reconciliation
  Engine UI is built.
