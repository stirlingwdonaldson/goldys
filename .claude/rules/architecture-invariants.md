---
paths:
  - "backend/**"
---

# Backend architecture invariants

These are pinned in `docs/system-context.md`; the current package map and the
enforced dependency rules are in `docs/architecture/current-state.md`. Violating
an invariant is a design regression, not a style nit, so treat it that way even
under time pressure. Paths below are relative to
`backend/src/main/java/com/goldys/platform/`.

## Raw ledger (`ingestion/RawRecord.java`)

- Every ingested payload (API JSON, CSV/XLSX, PDF, scraped HTML, webhook body,
  manual entry) lands in `raw_record` byte-faithful, before any parsing or
  transformation, through `IngestionService` / `RawPayloadService`. There are no
  source-specific staging tables.
- Authoritative bytes live in `payload_bytes` (`BYTEA`) with `payload_sha256` and
  `payload_byte_length` (V2). JSONB, where present, is derived and never the audit
  source.
- Rows are never updated or deleted. A database trigger
  (`raw_record_append_only`) rejects mutation; a correction is a new row.
- Connector failures are recorded as `IngestionFailure` rows on an
  `IngestionRun` with status `FAILED` or `PARTIAL`, never silence. `failureType`
  is a plain string on purpose (new connectors can report unanticipated failure
  modes without a schema change), so don't tighten it to an enum without
  checking whether that constraint is still wanted.

## Bitemporal canonical layer (`canonical/BitemporalEntity.java`)

- Two independent time axes: `validFrom`/`validTo` (when the fact was true in
  the real world) and `recordedAt`/`supersededAt` (when we recorded or replaced
  it). Don't collapse them into one timestamp pair.
- Never `UPDATE` a canonical row in place when a later raw record supersedes
  it. Close the old version (`supersede()`) and insert a new one via
  `BitemporalRepository`. This is what makes as-of queries and rule
  recomputation possible.
- Every canonical entity extends `BitemporalEntity` and carries the raw record
  id it came from. Don't invent a parallel versioning scheme.
- Matching and identity rules per entity are pinned in
  `docs/connectors/matching-and-identity.md`. Don't change an entity's logical
  identity without updating that doc.

## Resolved layer and read direction (`reconciliation/`, `semantic/`)

- Resolved projections (`resolved_*` tables) are disposable: projectors can
  truncate and rebuild them from canonical state plus overrides and rules.
  Never store information only in a resolved row.
- Business consumers (`api`, `reporting`, `conversational`, `dashboard`) read
  resolved data through `semantic` query interfaces, never `canonical` or the
  raw ledger. Reconciliation internals may read canonical freely.
- `ArchitectureBoundariesTest` enforces these package rules. Extend it when you
  add a package or domain rather than working around it.

## Permission model (`auth/`)

- `PermissionService.require(role, resource, action)` is the only enforcement
  point. Application services authorize reads, `ToolDispatcher` authorizes AI
  tool calls, and domain write services authorize at the mutation boundary.
  Don't add a parallel check anywhere else.
- Permissions are table-driven `(department, seniority, resource)` rows, not
  named roles. Adding a department or seniority value must never require editing
  an existing `Permission` row, only adding new rows.
- Do not encode `OWNER > MANAGER > STAFF` as an ordinal shortcut. Owner access
  comes from explicit rows (seeded as `ALL × OWNER`), not a hardcoded bypass.
- A denied check throws `AccessDeniedException`, which surfaces as an explicit
  "not permitted", never a silently filtered or partial result.
- Seed new resources for `ALL × OWNER` only. BOH/FOH-specific grants wait on the
  stakeholder field-to-role mapping (open question in `docs/prd.md`). Don't
  guess them.

## Connectors (`ingestion/port/`, `connectors/`)

- Pull connectors implement `SourceConnector` (`sourceSystem()`,
  `connectorName()`, `fetch(watermark, IngestionSink)`) and are run by
  `ConnectorRunner`. Push ingestion (webhooks, CSV/PDF drops, SFTP) goes through
  `IngestionService.ingestPush(...)`. Either way, bytes hit the raw ledger before
  parsing.
- If adding a connector forces a change to the port, the port is leaking a
  vendor concern. Reconsider the change instead of widening the interface.
- Report expected source failures by throwing `ConnectorFetchException` with a
  `failureType`. Its message is operator-facing and must never contain payload
  contents, credentials, or tokens.
- Connectors are one-way. Nothing ever writes back to a source system.
- Check `docs/system-context.md`'s "Per-Source Ingestion Reality" table and
  `docs/connectors/source-access.md` before assuming an API exists.

## AI tools and widgets (`reporting/`, `conversational/`, `widget/`)

- Every tool takes a bounded, enum-validated parameter set. Never add a tool
  whose parameters accept an arbitrary field name, SQL, or a free-text filter
  expression. That is freeform query generation with extra steps, which the
  tool boundary exists to prevent. Contract: `docs/contracts/ai-tool-boundary.md`.
- Tools return typed widget specs (schema version 2), never executable UI code.
- Saved dashboards store query configuration, never rendered data, and are
  re-authorized per metric at render time.
