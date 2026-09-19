# Goldy's Phase 1 MVP Design

**Status:** Approved
**Date:** 2026-09-19
**Scope:** `docs/prd.md` Requirements 1–7 only

## 1. Objective

Build the first usable release of Goldy's unified data platform: a read-only,
single-venue system that ingests Lightspeed, Cooking the Books (CTB), OpenTable,
and Deputy data; preserves source evidence; maps it into bitemporal canonical
entities; surfaces field-level disagreement; and lets authorized staff create
auditable manual overrides.

Phase 1 succeeds when all P0 acceptance criteria in `docs/prd.md` Requirements
1–7 pass against one real trading week of data. Calendar duration is not an exit
criterion.

## 2. Sources of Truth and Current Baseline

Architecture decisions come from `docs/system-context.md`; scope and acceptance
criteria come from `docs/prd.md`; `docs/design-system.md` governs Phase 1 UI
behavior and visual treatment.

The implementation was intentionally cleared on branch
`chore/clear-project-scaffold`. The retained baseline consists of frontend and
backend build configuration, local PostgreSQL configuration, application
configuration, and `V1__baseline_schema.sql`. Statements in the root README,
`CLAUDE.md`, backend architecture rule, and design-system document that claim
Java or frontend scaffolding is currently implemented are stale and must be
corrected during repository-foundation work.

## 3. Scope

### In scope

- Byte-faithful raw ingestion with provenance and append-only guarantees.
- Observable ingestion runs, including partial and complete failures.
- Bitemporal canonical sale-item and shift entities with raw provenance.
- Extensible department × seniority authorization.
- Existing SSO/OIDC authentication and local staff-profile mapping.
- Four isolated source connectors:
  - Lightspeed authenticated back-office ingestion;
  - CTB standard CSV/XLSX export ingestion;
  - Deputy OAuth REST API ingestion;
  - OpenTable automated GuestCenter report retrieval.
- Documented and tested entity matching for every Phase 1 entity type.
- Permission-gated, field-level conflict inspection and manual override.
- Connector status, explicit missing-source states, and operational dashboard.
- Validation against one real trading week.

### Out of scope

- Rule authoring, priority-by-source rules, and custom resolution logic.
- Conversational BI, Smart Exporter, and Automation Hub.
- Freeform SQL or generic AI query tools.
- Write-back to any connected source.
- Tenzo ingestion.
- Per-user permission exceptions.
- Message-bus infrastructure.
- Multi-venue tenancy.
- Local password authentication.

Phase 2 boundaries may be documented as interfaces, but no unused Phase 2
runtime implementation will be created in this phase.

`docs/system-context.md` also requires the AI tool boundary and JSON widget
shape to be locked before parallel connector work. Phase 1 satisfies that
prerequisite with versioned contract artifacts: a JSON Schema for widgets and a
documented fixed-tool interface showing bounded enum parameters and permission
enforcement. It does not add Spring AI, invoke a model, or implement Phase 2
business tools.

## 4. Capability Map

| Module ID | Responsibility | Depends on |
|---|---|---|
| `platform-foundation` | Minimal Spring Boot and Next.js applications, health checks, shared API errors, test harnesses | — |
| `ingestion-ledger` | Exact source bytes, provenance, ingestion runs, failures, watermarks, append-only enforcement | `platform-foundation` |
| `access-control` | OIDC identity, staff profiles, department × seniority permissions, explicit denial | `platform-foundation` |
| `canonical-model` | Bitemporal entities, raw provenance, supersession, as-of queries | `ingestion-ledger` |
| `entity-matching` | Entity-specific keys and tolerances, match outcomes, documented decisions | `canonical-model` |
| `connector-lightspeed` | Authenticated back-office ingestion behind the connector port | `ingestion-ledger` |
| `connector-ctb` | Standard CSV/XLSX export ingestion behind the connector port | `ingestion-ledger` |
| `sales-reconciliation` | Sales canonicalization, matching, conflicts, overrides, and resolved views | `entity-matching`, `access-control`, `connector-lightspeed`, `connector-ctb` |
| `reconciliation-ui` | Exception-first, permission-gated reconciliation workflow | `sales-reconciliation` |
| `connector-deputy` | OAuth API ingestion and shift canonicalization | `ingestion-ledger`, `canonical-model` |
| `connector-opentable` | Automated GuestCenter report ingestion | `ingestion-ledger`, `canonical-model` |
| `operations-observability` | Last success, failures, no-new-data state, and ingestion indicators | all connectors |
| `mvp-validation` | Requirement 1–7 evidence against one trading week | all Phase 1 modules |

Build order is risk-first: establish the platform and ingestion contracts, then
complete a real Lightspeed + CTB sales reconciliation path before adding Deputy
and OpenTable. This exercises every architectural layer before repeating the
connector pattern.

## 5. Architecture

### 5.1 Backend modules

Use one modular Spring Boot application. Package boundaries are deliberate, but
Phase 1 must not add framework-heavy hexagonal boilerplate.

- `ingestion`: connector port, ingestion runs, exact payload persistence,
  watermarks, scheduling, and failures.
- `connectors.<source>`: source client, source DTOs, parsers, and adapter. Vendor
  types never cross this package boundary.
- `canonical`: bitemporal persistence, source-to-canonical mapping, provenance,
  and as-of queries.
- `matching`: entity-specific match policies and explicit match outcomes.
- `auth`: OIDC session integration, staff profiles, and the sole permission
  enforcement service.
- `reconciliation`: conflict detection, manual overrides, recomputation, and
  resolved-view reads.
- `api`: REST controllers, DTOs, validation, and stable error mapping.

Modules communicate through small Java interfaces and immutable records. JPA
entities, vendor DTOs, and HTTP models are not shared as cross-module contracts.

### 5.2 Frontend modules

Use Next.js App Router, TypeScript, Tailwind, and shadcn/ui with Bun. Rebuild only
the Phase 1 surfaces:

- shared application shell;
- dashboard;
- connector health;
- reconciliation exception list;
- reconciliation record drill-in;
- explicit permission-denied state.

The frontend renders backend contracts and manages interaction state. Matching,
permission, canonicalization, and resolution decisions remain backend concerns.

### 5.3 Authentication

Use the venue's existing OIDC provider through Spring Security OAuth2 Login.
The backend owns the authenticated session and exposes the current user through
an API endpoint. Browser JavaScript never stores OIDC access or refresh tokens.

Production routes the frontend and `/api` through one origin. Session cookies
are `HttpOnly`, `Secure`, and `SameSite=Lax`. Local development uses a Next.js
rewrite to the backend with an explicitly configured development redirect URI.

An OIDC identity is keyed by issuer plus subject and maps to a local staff
profile containing department and seniority. The OIDC provider name, client
credentials, issuer metadata, and claim mapping are deployment configuration,
not source-controlled values. No local password database is introduced.

## 6. Data Flow

```text
source
  -> connector fetch
  -> ingestion run opened
  -> exact payload bytes stored
  -> payload parsed inside connector adapter
  -> canonical candidate produced
  -> entity-specific matching applied
  -> prior canonical version closed and successor inserted
  -> field conflicts calculated
  -> optional manual override appended
  -> resolved view recomputed
  -> permission service authorizes resource and fields
  -> REST DTO returned
  -> Next.js renders user state
```

The exact payload is persisted before parsing. Parse, schema, mapping, or match
failure never removes source evidence. Every derived record remains traceable to
the raw record and ingestion run that produced it.

## 7. Persistence Design

`V1__baseline_schema.sql` remains unchanged as migration history. New behavior
is introduced through later Flyway migrations.

### 7.1 Ingestion ledger

The existing JSONB payload cannot satisfy byte-faithful storage because
PostgreSQL normalizes JSON. Add an authoritative `BYTEA` payload, SHA-256 digest,
byte length, content type, optional character encoding, source, fetch method,
fetcher identity, and fetch timestamp. Existing JSONB may remain as a derived,
query-friendly representation but is never the audit source.

Add an ingestion-run record containing source, connector identity, start/end
times, status, watermark inputs/outputs, fetched-record count, persisted-record
count, and failure summary. Partial success is distinct from success, failure,
and no-new-data.

The operational ingestion ledger comprises run, payload, and failure records.
Failures remain separate from successful payload rows so they cannot be mistaken
for source data, while remaining first-class and independently queryable.

Database permissions or triggers enforce that raw payloads cannot be updated or
deleted by the application role. Retention deletion, if ever required, needs a
separate approved design.

### 7.2 Canonical state

Each canonical version has:

- a version-row ID;
- a stable logical-entity ID shared across versions;
- valid-time start/end;
- recorded-at/superseded-at system time;
- source system and raw-record provenance;
- normalized canonical fields.

Fact fields are never changed in place. Supersession only closes the previous
row's lifecycle and inserts a successor. Repositories expose current and
system-time-as-of queries through one shared bitemporal pattern.

### 7.3 Matching

Matching and resolution are separate concerns. Matching decides whether source
records describe one real event; resolution decides which conflicting field
value is authoritative.

Every policy returns one of `MATCHED`, `UNMATCHED`, or `AMBIGUOUS`, with the
policy version and evidence used. No matching keys or tolerances are guessed in
code. They are designed from sanitized real Lightspeed/CTB and Deputy/OpenTable
samples, documented per entity type, then captured in fixture-driven tests.

### 7.4 Conflicts, overrides, and resolved views

A conflict records the logical entity, field, candidate source values, and
state. A Phase 1 manual override records the selected source candidate, actor,
reason when provided, and timestamp. If staff must enter a corrected value that
no source supplied, that correction first enters through the manual-ingestion
path and becomes a traceable candidate. Overrides are append-only and may be
superseded, not erased.

Resolved records are derived exclusively from canonical state and active manual
overrides. Recomputing produces a consistent version without mutating canonical
history. Dashboards and reporting APIs read only resolved data.

## 8. Authorization Model

Permissions are table-driven by `(department, seniority, resource, action)`.
Owner access is represented by permission rows, not ordinal comparisons or a
hardcoded bypass. Adding a department or seniority adds data; it does not change
permission code.

One permission service is the sole enforcement point for API reads and override
actions. Controllers request authorization before accessing protected data.
Denied requests return an explicit `NOT_PERMITTED` response; the system never
returns silently filtered or deceptively partial results.

The concrete field-to-role matrix must be supplied by stakeholders before
permission seed data or reconciliation-field rendering is implemented. The
implementation plan treats that matrix as a gated input, not an engineering
guess.

## 9. API and Error Contracts

REST DTOs are versionable contracts independent of JPA and vendor models.
Backend and frontend development may proceed in parallel only after request,
response, and error examples are approved for that vertical slice.

All errors use one envelope with a stable code, human-readable message,
correlation ID, and field details where applicable. Phase 1 codes include:

- `NOT_PERMITTED`
- `VALIDATION_FAILED`
- `CONNECTOR_AUTH_FAILED`
- `CONNECTOR_FETCH_FAILED`
- `CONNECTOR_SCHEMA_MISMATCH`
- `MATCH_AMBIGUOUS`
- `OVERRIDE_CONFLICT`
- `RECOMPUTATION_PENDING`

Connector failures are persisted before an error is returned or logged.
Credentials, tokens, payload contents, and sensitive fields are never included
in logs or error responses.

## 10. Frontend Interaction Design

Follow `docs/design-system.md`: shadcn defaults, light mode, desktop-first,
calm presentation, and semantic status colors.

The reconciliation landing view is exception-first. It shows conflicting or
missing fields in cards and collapses agreements. A drill-in displays all
fields by source. Missing values say `No data from <source>` rather than showing
blank cells. A successful override visibly identifies the value as overridden
and removes it from the default open-exceptions list while retaining audit
history in the drill-in.

Connector UI distinguishes failure, no new records, partial ingestion, and
success. Permission denial is a complete explicit state, never a disabled or
partially rendered version of protected content.

## 11. Testing Strategy

### Backend

- Unit tests for parsers, match policies, permission decisions, and resolution.
- PostgreSQL integration tests for byte preservation, append-only enforcement,
  bitemporal supersession/as-of queries, recomputation, and permission behavior.
- A shared connector contract test suite applied to every adapter.
- Fixture-driven tests using sanitized real or representative source samples.
- API tests for response, validation, authentication, authorization, and error
  contracts.

Use Testcontainers if the repository's Docker baseline proves reliable in local
development and CI. Otherwise use an explicitly provisioned PostgreSQL 16
service with the same migration and reset behavior. Do not substitute an
in-memory database for PostgreSQL-specific behavior.

### Frontend

- Component tests for permission denial, missing source, conflict, override,
  loading, empty, partial-ingestion, and failure states.
- Contract fixtures matching approved backend DTO examples.
- Browser tests for the primary reconciliation journey: open exception, compare
  values, select authority, save override, and observe resolved state.
- Accessibility tests for keyboard flow, focus, labels, contrast, and status
  announcements.

### Acceptance dataset

The trading-week validation dataset must include successful and failed runs,
corrected records, matched/unmatched/ambiguous cases, missing-source cases,
field conflicts, manual overrides, and BOH/FOH/Owner/denied permission cases.
Sensitive production values must be sanitized before becoming committed test
fixtures.

## 12. Commands

The implementation must keep these commands valid and document any additions:

### Backend (`backend/`)

- Build: `./gradlew build`
- Test: `./gradlew test`
- Format: `./gradlew spotlessApply`
- Format check: `./gradlew spotlessCheck`

### Frontend (`frontend/`)

- Install: `bun install`
- Development: `bun run dev`
- Build: `bun run build`
- Lint: `bun run lint`
- Type check: `bunx tsc --noEmit`

Focused unit, integration, and browser-test commands will be added with their
test harnesses and recorded in project guidance.

## 13. Code Style

Favor conventional Java and TypeScript over abstraction-heavy code. Names use
domain terms from the PRD. Public boundaries use immutable records or readonly
types, and non-obvious bitemporal or matching decisions include rationale.

```java
public record MatchDecision(
    MatchOutcome outcome,
    UUID logicalEntityId,
    String policyVersion,
    List<MatchEvidence> evidence) {}
```

Avoid generic framework names such as `Processor`, `Manager`, or `Helper` when a
domain name exists. Do not create common base classes for connectors beyond the
small shared port and contract-test behavior they demonstrably share.

## 14. Project Structure

```text
backend/
  src/main/java/com/goldys/platform/
    api/
    auth/
    canonical/
    connectors/
    ingestion/
    matching/
    reconciliation/
  src/main/resources/db/migration/
  src/test/
frontend/
  app/
  components/
  features/
  lib/
  tests/
docs/
  matching/
  superpowers/specs/
e2e/
tasks/
```

Tests mirror the production package or feature they verify. Source-specific
fixtures live with their connector tests, not in shared domain packages.

## 15. Delivery Checkpoints

1. **Repository baseline:** applications start, migrations apply to an empty
   PostgreSQL 16 database, and verification commands are reproducible.
2. **Ingestion foundation:** exact bytes survive round trip, append-only rules
   are enforced, and failures/partial runs are queryable.
3. **First vertical slice:** Lightspeed + CTB sales reach a permission-gated
   reconciliation screen and manual override updates only resolved state.
4. **Remaining connectors:** Deputy and OpenTable pass the shared connector
   contract; operational UI distinguishes all run states.
5. **MVP validation:** Requirements 1–7 pass against one sanitized trading week
   and indicators are recorded before Phase 2 planning.

Each checkpoint requires build, test, lint/format, focused security review, and
human review before the next high-risk phase.

## 16. Commit Discipline

Use atomic Conventional Commits as verified save points. Every commit does one
logical thing and passes its focused tests plus applicable build and lint checks.
Examples:

```text
docs: define phase one implementation specification
chore: restore backend application baseline
test: define immutable ingestion contract
feat: persist byte-faithful ingestion payloads
feat: ingest cooking the books exports
feat: reconcile overlapping sales records
feat: add permission-gated reconciliation view
```

Do not mix formatting, unrelated cleanup, dependency changes, schema changes,
or separate capabilities in one commit. A migration, implementation, and tests
may share a commit only when they form one inseparable vertical behavior.

## 17. Boundaries

### Always

- Persist source bytes before parsing.
- Use Flyway for schema changes and keep Hibernate on `validate`.
- Preserve canonical history and raw provenance.
- Route protected operations through the sole permission service.
- Return explicit denial and missing-source states.
- Keep connector vendor types inside their adapters.
- Verify each increment before a Conventional Commit.

### Ask first

- Add production dependencies or change pinned framework versions.
- Change the database schema beyond an approved task.
- Change authentication/session architecture.
- Change matching tolerances after validation begins.
- Add new sources, canonical entity types, permission axes, or scope.
- Modify CI, deployment, or infrastructure beyond local PostgreSQL.

### Never

- Commit secrets, source credentials, OIDC tokens, or unsanitized source data.
- Write back to connected source systems.
- Generate freeform SQL or expose a generic query tool.
- Mutate source facts or canonical fact fields in place.
- Guess matching rules or field-to-role permissions.
- Read reports or dashboards from raw or canonical tables.
- Introduce Kafka/SQS without a measured need and approved design.

## 18. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Source formats differ from assumptions | High | Obtain sanitized samples first; fixture-test parsers; keep vendor logic isolated |
| Matching keys are unreliable | High | Design from paired real samples; expose ambiguous outcomes; version policies |
| Permission matrix arrives late | High | Build enforcement mechanism first; gate seed data and reconciliation fields on stakeholder matrix |
| OIDC provider details arrive late | Medium | Keep standard OIDC configuration external; use provider-neutral integration tests |
| Scrapes break on vendor UI changes | High | Detect schema mismatch explicitly; store source evidence; add fixture and smoke tests |
| Raw bytes and derived JSON diverge | Medium | Treat bytes plus digest as authoritative; regenerate derived representation deterministically |
| Recalculation exposes mixed state | High | Version recomputation runs and switch resolved reads only after completion |
| Docker unavailable in some environments | Medium | Verify early; support PostgreSQL service fallback without weakening integration tests |

## 19. Required Inputs and Decision Gates

These are planned inputs, not unspecified implementation details:

1. **OIDC deployment details:** issuer, client registration, redirect URI, and
   claim availability are required before deployable authentication is complete.
2. **Deputy registration:** OAuth client registration must exist before live
   Deputy integration begins.
3. **Sanitized source samples:** paired Lightspeed/CTB sales samples and relevant
   Deputy/OpenTable samples are required before matching policies are approved.
4. **Permission matrix:** stakeholders must enumerate fields/entities permitted
   for each department × seniority combination before seed data and protected UI
   fields are implemented.

The implementation plan must schedule acquisition and validation of these inputs
before their dependent tasks. Work may continue on independent modules while a
gate is waiting, but dependent behavior must not be mocked and mistaken for
complete production functionality.

## 20. Success Criteria

- Every Phase 1 source writes exact payload bytes and complete provenance before
  transformation.
- Every run exposes success, partial success, failure, or no-new-data explicitly.
- Corrected source facts create bitemporal successors and preserve as-of reads.
- Every Phase 1 entity type has an approved, documented, fixture-tested matching
  strategy.
- Lightspeed and CTB conflicts are visible field by field and missing sources are
  explicit.
- Authorized staff can append a manual override without changing canonical
  history; unauthorized users receive `NOT_PERMITTED`.
- Deputy and OpenTable pass the same connector contract as other adapters.
- Dashboard and reconciliation APIs read resolved data only.
- All Requirements 1–7 acceptance criteria pass against one sanitized real
  trading week.
- Builds, tests, formatting, lint, type checks, browser tests, and accessibility
  checks pass using documented commands.
