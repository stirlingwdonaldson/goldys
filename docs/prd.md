# Goldy's Unified Data Platform — Product Requirements Document

**Status:** Draft for review
**Audience:** AI coding sessions building this system, sequenced by an eng collaborator. Written for precision over narrative — treat every requirement below as literal, not illustrative.
**Source of truth:** `system-context.md` (architecture invariants, tech stack, per-source ingestion reality). This PRD does not override that doc's architectural decisions; it scopes and sequences them into shippable phases.

---

## Problem Statement

Goldy's pub runs on seven-plus disconnected operational systems — POS (Lightspeed), payroll/rostering (Deputy), accounting (Cooking the Books), reservations (OpenTable), plus inventory, social, and website/comms tooling — each with its own view of the same underlying events (a shift, a sale, a booking). No system agrees with any other when their data overlaps, and there is no reliable way to answer a simple operational question ("what did labor cost look like against sales last Friday") without manually reconciling exports by hand. The current reporting layer, Tenzo, aggregates a subset of these sources but cannot resolve conflicts between them and offers no way to configure how conflicts should be resolved — it presents whichever number its own pipeline happened to produce.

The cost of not solving this: staff and management make roster, purchasing, and pricing decisions on numbers nobody has verified against source systems, and every new reporting question requires a new manual export-and-reconcile exercise rather than a query against trustworthy data.

## Goals

1. **Single source of truth exists for at least one full operational cycle** (a trading week) — every canonical entity type in scope has raw data flowing in from its source connector without silent gaps.
2. **Field-level conflicts are visible, not hidden** — where two sources disagree on the same fact (e.g. `quantity_sold` from Lightspeed vs. CTB), the discrepancy is surfaced to staff rather than one source silently winning.
3. **Reconciliation is staff-configurable, not developer-configurable** — a non-engineer can set or change a resolution rule (e.g. "CTB wins on `quantity_sold`") without a code change or deploy.
4. **Historical resolved data survives rule changes** — changing a resolution rule and recomputing reproduces a consistent result without mutating or losing prior canonical history (bitemporal guarantee, verified by test).
5. **Ingestion failures are diagnosable within the platform** — when a connector fails to fetch, that failure is visible as a first-class state in the raw log, not inferred later from a reconciliation mismatch.

*(No numeric thresholds are set, deliberately — there's no existing baseline to set a meaningful target against. Success for Phase 1 is completion-based: see the acceptance criteria on each P0 requirement below, and the Leading/Lagging Indicators section, which the team should start measuring from week one even without a target attached.)*

## Non-Goals

- **Automated/AI-driven conflict resolution.** The MVP and full build both require staff to configure resolution rules explicitly (priority-by-source, manual override, custom logic authored via UI). The system does not guess or auto-resolve conflicts using heuristics or ML. *Rationale: correctness and auditability matter more than convenience here, and an opaque auto-resolver undermines the "staff-configurable" invariant.*
- **Write-back to any connected source system (Lightspeed, CTB, OpenTable, Deputy), at any phase.** This is a permanent architectural constraint, not an MVP-only deferral: every connector is one-way, source → platform. The platform never writes corrected data, updates, or any other payload back into a source system. *Rationale: decided explicitly — the value here is reconciliation and reporting, not becoming a write-of-record for systems that already have their own owners and workflows; two-way sync multiplies failure modes for no proven benefit.*
- **Automation Hub triggers that write to source systems.** Automation Hub (Phase 2, P2) is scoped to outbound notifications (e.g. Slack alerts) and exports only — it can never write back into a connected source system, per the constraint above. *Rationale: keeps the one write-capable module's blast radius limited to systems the platform doesn't depend on for ingestion.*
- **Automation Hub (triggers/workflows) in the MVP.** Deferred to the full build per `system-context.md` phasing. *Rationale: depends on resolved views being trustworthy first — building triggers on unreconciled data would just automate bad decisions faster.*
- **Freeform/ad-hoc query access for the AI assistant**, in both MVP and full build, permanently. The Conversational BI and Smart Exporter modules only ever call a fixed, enum-validated tool set over the semantic layer. *Rationale: this is a stated architectural invariant, not a phasing decision — freeform SQL generation is explicitly the failure mode this system is designed to avoid.*
- **Tenzo as an MVP connector.** Tenzo is not built or ingested in Phase 1 or Phase 2 of this spec. It remains the incumbent reporting tool being superseded, and stays a candidate future cross-check source only — revisit if/when there's a concrete reason to. *Rationale: decided explicitly to keep MVP source count at four and avoid spending time confirming Tenzo's API rawness for a source that's cross-check-only at best.*
- **Custom, per-user permission overrides in v1 of the role model.** Access is governed by the department × seniority axes (see Requirement 3 below), not by exceptions granted to individual staff members. *Rationale: keeps the model's extensibility promise intact — ad hoc per-user grants are exactly the kind of special-casing that makes a permission model hard to reason about later.*
- **Kafka/SQS or other message-bus infrastructure**, at any phase, unless a specific throughput or fan-out problem is identified that the append-only-log-plus-poller pattern can't handle. *Rationale: explicit anti-goal in `system-context.md` — avoid infrastructure the system doesn't yet need.*

## User Stories

Grouped by persona. "AI coding session" stories describe what the *build* must produce to be usable by the next module; "pub staff/management" stories describe end-user value once built.

### Pub Manager / Owner (primary reporting user)
- As a pub manager, I want to see when two sources disagree on a sales figure for a given shift, so that I don't report a number I can't defend.
- As a pub manager, I want to set which source wins by default for a given field (e.g. "CTB wins on `quantity_sold`"), so that routine conflicts resolve themselves without me re-deciding every time.
- As a pub manager, I want to manually override a specific resolved value for a specific record, so that I can correct a one-off error without changing the general rule.
- As a pub manager, I want to ask a reporting question in plain language ("what was labor cost as a % of sales last week") and get an answer backed by resolved data, so that I don't have to build the query myself. *(Full build — Conversational BI)*
- As a pub manager, I want to export a presentation-ready table to Excel for a board or accountant meeting, so that I don't hand-format data pulled from multiple exports. *(Full build — Smart Exporter)*

### Department-Scoped Managers (role model)
- As a back-of-house (BOH) manager, I want to see inventory, cost-of-goods, and kitchen labor data, so that I can manage food cost without wading through front-of-house reporting that isn't mine to act on.
- As a front-of-house (FOH) manager, I want to see covers, reservations, and service labor data, so that I can staff and run service without seeing kitchen-specific figures.
- As an owner, I want to see everything both BOH and FOH managers see plus wage/labor-cost data neither of them can see, so that I have the full picture without having to log in as someone else.
- As the person configuring the role model, I want to add a new department (e.g. "Bar") or seniority level later without redefining every existing role, so that the model doesn't need a rebuild every time the org changes.
- As any role-scoped user, when I ask the Conversational BI assistant a question outside my permitted data, I want a clear "you don't have access to that" response rather than a partial or silently-filtered answer, so that I'm not misled into thinking the answer is complete. *(Full build — Conversational BI)*

### Ops/Admin Staff (data entry and connector operators)
- As an admin staff member, I want the system to tell me clearly when a connector (e.g. Lightspeed scrape) has failed to pull new data, so that I know to investigate before I trust a report.
- As an admin staff member entering data manually (e.g. a correction not captured by any source system), I want that manual entry stored with the same audit trail as any other ingested record, so that its provenance is never ambiguous later.

### Engineering Collaborator (reads this system's own architecture)
- As the eng collaborator joining later, I want every canonical table to carry the same bitemporal versioning pattern regardless of which LLM session built it, so that I can reason about history consistently across modules.
- As the eng collaborator, I want non-obvious logic (bitemporal queries, reconciliation rule evaluation) commented in place, so that I'm not reverse-engineering intent from code alone.

### Edge cases
- As a pub manager, when a source connector has never successfully ingested a given entity type, I want the reconciliation UI to say "no data from source X" rather than silently omitting that source from the comparison, so that I don't mistake absence for agreement.
- As a pub manager, when I change a resolution rule, I want to know whether recomputation is complete before I read the resolved view, so that I don't act on a partially-recomputed result.

---

## Requirements

### Phase 1 — MVP (3–4 weeks)

**P0 — Must-Have**

1. **Raw event log (append-only staging layer).**
   - Description: A `JSONB`/blob staging table storing every ingested record byte-faithful, tagged with source system, fetch method, content type, fetched-at timestamp, and fetcher identity. Covers API JSON, CSV/XLSX exports, scraped HTML/DOM extracts, and manual entries uniformly.
   - Acceptance criteria:
     - [ ] Given any successful fetch from any connector, when the raw payload is received, then it is persisted unmodified before any parsing/transformation occurs.
     - [ ] Given a raw record, when queried, then source system, fetch method, content type, fetched-at, and fetcher identity are all present and non-null.
     - [ ] Given a manual data entry by staff, when saved, then it is stored in the same raw log table with `fetch_method = manual` and the entering staff member's identity.
   - Dependencies: None — this is the foundational piece per Build Order Prerequisite #1.

2. **Bitemporal canonical entity layer.**
   - Description: Vendor-agnostic canonical entities (at minimum `CanonicalShift`, `CanonicalSaleItem`, plus whichever entities the in-scope MVP connectors produce) with explicit `valid_from`/`valid_to` (valid time) and `recorded_at`/`superseded_at` (system time) columns, per the pattern pinned in `system-context.md`.
   - Acceptance criteria:
     - [ ] Given a raw record mapped to canonical form, when a later raw record supersedes it (e.g. a corrected export), then the prior canonical row is closed (`superseded_at` set) rather than overwritten.
     - [ ] Given any canonical entity, when queried "as of" a past system time, then the result reflects what was known at that time, not the current state.
     - [ ] Given the base entity/repository pattern, when a new canonical entity type is added by a future module, then it reuses the same base pattern rather than defining its own versioning scheme.
   - Dependencies: Raw event log (#1) must exist first.

3. **Role & permission model (department × seniority), built as shared scaffolding.**
   - Description: An extensible access-control model gating what any request — UI view, reconciliation action, or (in Phase 2) AI tool call — is allowed to see or do. Modeled as two independent axes rather than flat named roles:
     - **Department:** `BOH`, `FOH`, `All` (extensible — a new department, e.g. `Bar`, can be added without touching existing department definitions).
     - **Seniority:** `Staff`, `Manager`, `Owner` (extensible the same way).
     - A user's effective role is the combination of their department and seniority (e.g. `BOH × Manager`). Permissions are defined per (department, seniority, resource/field) rather than per named role, so adding an axis value doesn't require redefining every existing permission.
     - This model gates **resolved-view data** in the MVP (Requirement 4) and will additionally gate **AI tool calls** in Phase 2 (Requirement 8) — same permission model, two enforcement points.
   - Acceptance criteria:
     - [ ] Given a user's department and seniority, when they request resolved-view data, then only fields/entities permitted for that (department, seniority) combination are returned.
     - [ ] Given `Owner` seniority, when any department-scoped data is requested, then it is visible regardless of department (Owner sees both BOH and FOH data, plus wage/labor-cost data neither BOH nor FOH managers see).
     - [ ] Given a new department or seniority level is added, when existing permissions are reviewed, then no existing permission definition needs to change to accommodate it (only new permission rows are added).
     - [ ] Given a permission check fails, when the request is denied, then the response is an explicit "not permitted" — never a silently filtered or partial result.
   - Dependencies: None — this is foundational scaffolding per Build Order Prerequisite #1 (extended to include the role/permission model alongside the raw-log envelope, bitemporal pattern, tool-calling framework, and widget schema).

4. **One working ingestion connector per in-scope MVP source**, each behind a common port interface (connector isolation invariant).
   - Sources and paths, per `system-context.md`'s "Per-Source Ingestion Reality" table:
     - **Lightspeed (O-Series/Kounta):** authenticated back-office scrape of server-rendered pages (confirmed working path today). If the paid REST API tier is unlocked before/during build, prefer it, but do not block the MVP on unlocking it.
     - **Cooking the Books (CTB):** self-serve Custom Invoice Export (CSV/XLSX) via scheduled SFTP or email ingestion, using the standard export only — no request to CTB/Quantaco for additional fields beyond what the standard export provides. Do not build against CTB's internal-endpoint scrape for production ingestion — schema-discovery use only.
     - **OpenTable:** scripted browser pull from GuestCenter reporting (not a manual export step — must be automatable).
     - **Deputy:** standard OAuth REST API connector. OAuth client registration is being handled directly by the project owner — engineering should confirm the client is registered before starting this connector, but does not need to chase ownership of that task.
   - **Tenzo is explicitly out of scope for MVP** (see Non-Goals) — do not build a Tenzo connector in this phase.
   - Acceptance criteria (per connector):
     - [ ] Given the connector runs on schedule, when it succeeds, then new/changed records appear in the raw log with correct source metadata.
     - [ ] Given the connector fails partway or fully, when the failure occurs, then a failure state is written to the raw log (see Requirement 6) — not merely an absence of new rows.
     - [ ] Given the connector's source format (JSON, CSV, scraped HTML), when ingested, then it passes through the same raw-log envelope as every other source (no source-specific staging table).
   - Dependencies: Raw event log (#1). Deputy connector additionally depends on OAuth client registration being complete (external, tracked by project owner, not engineering).

5. **Minimal reconciliation UI (manual override only — no rule engine yet), permission-gated.**
   - Description: A UI surface where staff can see, for a given canonical entity, the field-level values reported by each source, and manually set which value is authoritative for that specific record. This is explicitly narrower than the full field-level rule engine (Phase 2). Every view and action in this UI is subject to the role/permission model (Requirement 3) — a BOH manager sees and can override BOH-scoped fields only, and so on.
   - Acceptance criteria:
     - [ ] Given a canonical entity with conflicting field values from two or more sources, when a staff member views it, then each source's value is shown individually (not merged/averaged).
     - [ ] Given a staff member selects an authoritative value for a field on a specific record, when saved, then a resolved view row is created/updated for that record without mutating the underlying canonical rows.
     - [ ] Given a source has no data for a given entity, when displayed, then the UI shows "no data from source X" rather than omitting that source silently (per edge-case user story above).
     - [ ] Given a user without permission for a given field/entity under the role model, when they view or attempt to override it, then the request is denied per Requirement 3's acceptance criteria — not shown in a read-only or partial form.
   - Dependencies: Bitemporal canonical layer (#2), role/permission model (#3), at least two connectors producing overlapping canonical entities (#4).

6. **Ingestion failure observability.**
   - Description: Connector failures (auth failure, timeout, partial fetch, schema mismatch) are written to the raw log as a first-class state, distinct from "no new data."
   - Acceptance criteria:
     - [ ] Given any connector failure mode, when it occurs, then a record is written indicating failure type, source, and timestamp — queryable independently of successful ingestion records.
     - [ ] Given a connector has failed, when a staff member checks connector status, then they can see the failure without having to infer it from a downstream reconciliation gap.
   - Dependencies: Raw event log (#1).

7. **Entity-matching strategy defined for each in-scope MVP entity type** (design artifact, not just code).
   - Description: Before the reconciliation UI can compare records from different sources, there must be an explicit, documented rule for identifying that two records from different sources describe the same real-world event (shared ID, time+amount tolerance matching, fuzzy name matching, etc.) — per Build Order Prerequisite #2.
   - Acceptance criteria:
     - [ ] Given each MVP canonical entity type (at minimum shifts and sale items), when the matching strategy is documented, then it specifies the exact keys/tolerances used, before the reconciliation UI's comparison logic is implemented.
     - [ ] Given two source records that should match under the documented strategy, when ingested, then they are linked to the same canonical entity.
   - Dependencies: None technically, but must complete before Requirement #5 is implemented (sequencing constraint, not just a data dependency).

**P1 — Nice-to-Have (MVP fast-follow)**

- Connector status dashboard summarizing last-successful-fetch time and failure count per source, beyond raw per-record failure states.
- CSV/manual-upload fallback path for any connector that fails, so staff aren't blocked on a broken scrape/API mid-week.

**P2 — Future Considerations (explicitly deferred, design-compatible)**

- Full field-level resolution rule engine (priority-by-source, custom logic, UI-authored rules) — Phase 2 below.
- Lightspeed REST API migration if/when the paid tier is unlocked, replacing the scrape without changing the canonical layer (this is exactly what connector isolation is for).

---

### Phase 2 — Full Build (7–12 weeks, follows MVP)

**P0 — Must-Have**

8. **Field-level reconciliation rule engine.**
   - Description: Replaces Phase 1's manual-override-only UI with a full rule engine: staff author resolution rules per field (priority-by-source, manual override, custom logic) through a UI, not hardcoded in Java or shipped config. Rules apply going forward and can be recomputed against history. Rule authoring itself is permission-gated the same way reconciliation UI access is (Requirement 3) — a BOH manager can set rules for BOH-scoped fields, not FOH- or Owner-only fields.
   - Acceptance criteria:
     - [ ] Given a staff member with no engineering access, when they create or change a resolution rule via the UI (within their permitted fields), then the rule takes effect without a code deploy.
     - [ ] Given a resolution rule changes, when resolved views are recomputed, then the result is reproducible and does not mutate canonical history (bitemporal guarantee holds).
     - [ ] Given a field has no explicit rule set, when resolution runs, then the system has a documented default behavior (e.g. flag as unresolved) rather than an undefined/arbitrary pick.
   - Dependencies: MVP Requirements #2, #3, #5, #7.

9. **Conversational BI module, role-gated.**
   - Description: AI assistant that clarifies the user's reporting goal via conversation, then calls a fixed, enum-validated set of purpose-built tools (e.g. `get_sales_by_period`, `get_labor_cost_variance`) against the semantic layer over resolved views, and emits JSON widget specs for frontend rendering. No generic query tool; no freeform SQL generation, ever. Every tool call is additionally scoped by the requesting user's department/seniority (Requirement 3) — the tool layer enforces the same permission model as the reconciliation UI, not a separate one.
   - Acceptance criteria:
     - [ ] Given any user question, when the assistant responds with data, then it did so via one or more of the fixed tool set — never by constructing a query string outside that tool set.
     - [ ] Given a tool call, when parameters are supplied, then every dimension/metric/filter is validated against a whitelist before execution; out-of-whitelist parameters are rejected, not passed through.
     - [ ] Given a user's role does not permit a requested dimension/metric, when the tool is called, then it returns an explicit permission-denied result the assistant surfaces to the user — never a filtered or approximated answer presented as complete.
     - [ ] Given the assistant needs to render a dashboard, when it responds, then it outputs a JSON widget specification conforming to the shared schema (Build Order Prerequisite #1), not frontend code.
   - Dependencies: Resolved views (#8) exist and are populated; role/permission model (MVP #3); shared tool-calling framework and JSON widget schema (scaffolding, see Requirement 11).

10. **Smart Exporter module.**
   - Description: Uses the same tool-calling infrastructure as Conversational BI to extract data and format presentation-ready tables for Excel export — including the same role-gating, so an export never contains data the requesting user couldn't see in the UI.
   - Acceptance criteria:
     - [ ] Given an export request, when fulfilled, then it reuses Conversational BI's tool-calling and semantic-layer infrastructure rather than a separate query path.
     - [ ] Given exported data, when opened in Excel, then it is presentation-ready (formatted headers, no raw JSON/internal IDs) rather than a raw data dump.
     - [ ] Given a user's role restricts a field, when they export data, then that field is excluded from the export, not merely hidden in the UI.
   - Dependencies: Conversational BI's tool-calling framework (#9) — build second, or in parallel once the shared framework lands.

**P1 — Nice-to-Have**

11. **Shared scaffolding hardening**: formalize the raw-log staging envelope, bitemporal base entity pattern, role/permission model, tool-calling framework, and JSON widget schema as reusable libraries/modules (they exist informally from MVP work but should be extracted and documented before further modules build on them in parallel) — per Build Order Prerequisite #1.

**P2 — Future Considerations (out of scope this build, design-compatible)**

12. **Automation Hub.** Triggers evaluated against resolved-view data (never raw/canonical) that fire outbound notification actions (e.g. Slack alerts) or exports — never write-backs into a connected source system, per the permanent write-back non-goal above. Any notification action still needs its own auth/permission design (e.g. a Slack bot token), distinct from every other module's read-only ingestion.

---

## Success Metrics

*Deliberately no numeric targets (see Goals). The metrics below are what to start measuring from week one regardless — they're the evidence a target would eventually be set against, and they're useful as trend lines even without one.*

### Leading Indicators (MVP, days–weeks post-connector-live)
- **Ingestion completeness:** % of scheduled connector runs that succeed without silent gaps, per source, measured daily.
- **Conflict surfacing rate:** count of field-level conflicts detected and shown to staff per trading week (a proxy for whether reconciliation is actually catching real discrepancies, not a target to minimize).
- **Time-to-detect ingestion failure:** time between a connector failure and its appearance as a visible failure state.
- **Manual override usage:** how often staff use manual override in the MVP UI, broken down by department/seniority — informs whether Phase 2's rule engine needs richer default rules for the fields staff override most, and whether the role model's field groupings match how staff actually work.

### Lagging Indicators (Full build, weeks–months post-launch)
- **Reporting turnaround time:** time for a manager to answer a reporting question via Conversational BI vs. the prior manual-export process.
- **Trust in resolved numbers:** qualitative — whether management stops cross-checking Conversational BI output against source-system exports before acting on it.
- **Tenzo displacement:** whether Tenzo is still used for reporting/dashboards post-launch, or fully superseded (revisit Tenzo as a data source only if a concrete reason emerges — see Non-Goals).

## Open Questions

- **[Engineering] Entity-matching tolerances.** The requirement to define an entity-matching strategy (Requirement #7) is scoped as "must happen before the reconciliation UI is built," but the specific matching keys/tolerances per entity type are not yet drafted — this is real design work, not a formality, and should be time-boxed to a single focused session against real sample data (e.g. a day of Lightspeed and CTB records side by side) rather than left open-ended.
- **[Stakeholder] Exact department and seniority values beyond the ones named so far.** BOH, FOH, and Owner/Manager/Staff are established; whether there are other departments (e.g. Bar as distinct from FOH) or seniority nuances (e.g. a shift-lead level between Staff and Manager) should be confirmed before Requirement #3's permission table is populated, even though the model itself doesn't need to change to accommodate them later.
- **[Stakeholder] Exact field/entity-to-role mapping.** The role model's shape (department × seniority) is decided, but which specific fields and entities each combination can see hasn't been enumerated yet — that mapping is what actually populates Requirement 3's acceptance criteria and should be drafted before the reconciliation UI (Requirement 5) is built against it.

## Timeline Considerations

- **Phasing is sequential by design, not just by convenience:** Build Order Prerequisite #1 (shared scaffolding — now including the role/permission model) must be locked before parallel connector work starts; Prerequisite #2 (entity-matching strategy) must be designed before the Reconciliation Engine/UI is implemented. Starting connector work or reconciliation UI work before these land risks rework, per the source doc's explicit warning.
- **Deputy OAuth client registration is being handled directly by the project owner**, outside the engineering build — kick it off in parallel with Requirement #1–3 scaffolding work so it isn't a late blocker on the Deputy connector.
- **CTB and OpenTable both route through export/scrape mechanisms rather than clean APIs** — budget more buffer for these two connectors than for Deputy, and treat "scripted browser pull" (OpenTable) as a real engineering task, not a manual interim step. CTB is scoped to its standard export only, which removes one source of schedule risk (no negotiation with Quantaco needed).
- **No hard external deadline or numeric completion target is set** — Phase 1 is considered done when its P0 requirements' acceptance criteria are all met and validated against one real trading week of data, not against a calendar date.
- **Suggested phase boundary:** MVP (Requirements 1–7) ships as a complete, usable increment on its own — staff get visible conflicts, manual override, and role-scoped access even without the rule engine. Phase 2 (Requirements 8–12) should not start until MVP's entity-matching strategy and canonical layer have been validated against at least one real trading week of data, since Phase 2's rule engine assumes that foundation is already correct.
