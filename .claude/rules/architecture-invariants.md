---
paths:
  - "backend/**"
---

# Backend architecture invariants

These are pinned in `docs/system-context.md` and enforced in the existing
scaffold code (see the javadoc on each class named below). Violating one of
these is a design regression, not a style nit — treat it that way even under
time pressure.

## Raw log (`raw/RawRecord.java`)

- Every ingested payload — API JSON, CSV/XLSX, scraped HTML, manual entry —
  lands in `raw_record` byte-faithful, before any parsing/transformation.
- Rows are never updated or deleted by application code. A correction is a
  new row, not an edit.
- All shapes flow through the same JSONB envelope — no source-specific
  staging tables. Non-JSON payloads get wrapped (`{"raw": "..."}`), not given
  their own column.
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
- New canonical entity types extend `BitemporalEntity` — don't invent a
  parallel versioning scheme for a new entity.
- `CanonicalShift`/`CanonicalSaleItem` fields are placeholders pending the
  entity-matching design session (see CLAUDE.md's open questions) — don't
  treat their current shape as final without checking `docs/prd.md`.

## Permission model (`auth/`)

- `PermissionService` is the *only* enforcement point. Don't add a second,
  parallel permission check anywhere else — reconciliation UI and (Phase 2)
  AI tool calls both route through it.
- Permissions are table-driven `(Department, Seniority, resource)` rows, not
  named roles. Adding a new `Department` or `Seniority` value must never
  require editing an existing `Permission` row — only new rows.
- Do not encode `OWNER > MANAGER > STAFF` as an ordinal/numeric shortcut in a
  permission check. Owner's broader access comes from explicit `Permission`
  rows (often via `Department.ALL`), not a hardcoded bypass.
- A denied check must throw/return an explicit `AccessDeniedException` —
  never a silently filtered or partial result.
- `Permission` rows ship empty from the scaffold. Don't populate them with
  guessed field-to-role mappings — that's an open question requiring
  stakeholder input (see `docs/prd.md`'s Open Questions).

## Connectors (`connector/SourceConnector.java`)

- Every vendor integration implements the one `SourceConnector` port. If
  adding a connector forces a change to that interface, the interface is
  leaking a vendor-specific concern — reconsider the change instead of the
  interface.
- A `fetch()` implementation must report failures via `IngestionFailure`
  (see above), never throw silently or just return zero rows.
- Check `docs/system-context.md`'s "Per-Source Ingestion Reality" table
  before assuming an API exists — Lightspeed, CTB, and OpenTable all route
  through scrapes/exports, not clean REST APIs, in Phase 1.

## AI tools / widgets (Phase 2 stubs: `tools/`, `widget/WidgetSpec.java`)

- Not used by anything in Phase 1 — these exist only so later modules build
  against a fixed shape.
- Every `AiTool` takes a bounded, enum-validated parameter set
  (`paramSchema()`). Never add a tool whose parameters accept an arbitrary
  field name or free-text filter expression — that's freeform query
  generation with extra steps, which the tool boundary exists to prevent.
- `ToolRegistry.invoke` enforces `PermissionService` before dispatch — any
  new tool-calling path must do the same, not bypass it.
- Reporting/dashboards (once they exist) read only from resolved views,
  never canonical or raw tables directly, even before the full Reconciliation
  Engine UI is built.
