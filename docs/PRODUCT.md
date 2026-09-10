# Goldy's Unified Data Platform — Product Summary

**Status:** Reference doc, non-normative.
**Audience:** AI coding sessions and human collaborators, as a fast orientation layer before reading the full specs.
**Precedence:** This doc summarizes `system-context.md` and `prd.md`. It never overrides either. Where anything here appears to conflict with those two docs, `system-context.md` wins on architecture and `prd.md` wins on requirements/scope — treat this doc as stale and re-derive the summary from source, don't reconcile by editing this file's logic.
**Use this doc to:** get product identity, scope boundary, and roadmap position in one pass before opening `prd.md` for requirement detail or `system-context.md` for architecture detail. Do not cite this doc as the basis for an acceptance criterion, a non-goal, or an architectural decision — cite the source doc and section instead.

---

## Product Identity

- **Product:** a read-only data-integration and reconciliation platform for one pub (Goldy's).
- **Function:** ingest data from N operational source systems, store it immutably, map it to canonical entities, detect field-level disagreements between sources describing the same entity, and produce a resolved, staff-configurable view of the truth for reporting.
- **Not:** a POS, payroll, accounting, or reservations system. Not a replacement for any connected source system. Not a write-of-record for any connected system — see Non-Goals.
- **Relationship to Tenzo:** Tenzo is the incumbent reporting tool this product supersedes for reporting/dashboards. Tenzo is explicitly excluded as a data source in Phase 1 and Phase 2 (see Non-Goals). It is not ingested, not queried, and not a dependency of any requirement in this build.

## In-Scope Source Systems (MVP, Phase 1)

| Source | Role |
|---|---|
| Lightspeed (O-Series/Kounta) | POS |
| Cooking the Books (CTB) | Accounting |
| OpenTable | Reservations |
| Deputy | Payroll/rostering |

No other source is ingested in Phase 1. Inventory, social, and website/comms tooling are named in the problem statement as disconnected systems but are not scoped to a Phase 1 or Phase 2 connector in `prd.md` — do not build a connector for them without a new requirement.

## User/Access Model (canonical definition: `prd.md` Requirement 3)

Access is governed by two independent axes, not named roles:
- **Department:** `BOH`, `FOH`, `All` (extensible).
- **Seniority:** `Staff`, `Manager`, `Owner` (extensible).
- Effective permission = the (department, seniority) pair, evaluated per resource/field.
- `Owner` seniority sees all departments plus wage/labor-cost data no other combination can see.
- A denied request returns an explicit "not permitted" — never a silently filtered or partial result.
- Do not implement per-user permission exceptions — see Non-Goals.

This same model gates the reconciliation UI (Phase 1) and all AI tool calls (Phase 2). It is one model with two enforcement points, not two models.

## Architecture Summary (canonical definition: `system-context.md`)

Three layers, fixed order, every module reads/writes at the layer specified — do not skip a layer or read from a layer above what a module is scoped to:

1. **Raw event log** — immutable, append-only. Every ingested record (API JSON, CSV/XLSX export, scraped HTML, manual entry) is stored byte-faithful with source, fetch method, content type, fetched-at, fetcher identity, before any transformation.
2. **Bitemporal canonical entities** — vendor-agnostic entities (e.g. `CanonicalShift`, `CanonicalSaleItem`) with explicit valid-time (`valid_from`/`valid_to`) and system-time (`recorded_at`/`superseded_at`). Corrections close and supersede rows; they never overwrite.
3. **Resolved views** — derived from canonical state by applying active resolution rules. Never mutates canonical rows. Recomputable: changing a rule and recomputing must reproduce a consistent result without loss of history.

Hard rule: reporting, dashboards, Conversational BI, Smart Exporter, and Automation Hub read only from resolved views. Never from canonical or raw tables directly.

## Non-Goals (canonical definition: `prd.md` Non-Goals — do not re-derive, cite that section)

These are permanent architectural constraints unless stated otherwise, not phasing deferrals:

- No write-back to any connected source system (Lightspeed, CTB, OpenTable, Deputy), ever, at any phase. Every connector is one-way: source → platform.
- No AI/heuristic auto-resolution of conflicts. Resolution rules are authored by staff through a UI, always.
- No freeform or ad-hoc query access for the AI assistant (Conversational BI, Smart Exporter), ever, at any phase. Fixed, enum-validated tool set only.
- No per-user permission exceptions in v1 of the role model. Department × seniority only.
- No Kafka/SQS or other message-bus infrastructure at any phase unless a specific throughput/fan-out problem is identified that the append-only-log-plus-poller pattern cannot handle.

Deferred, not permanent:
- Tenzo as a connector — excluded from Phase 1 and Phase 2; revisit only as a cross-check source and only if a concrete reason emerges.
- Automation Hub — excluded from MVP; scoped in Phase 2 as P2 (Future Considerations), never authorized to write back to a connected source system per the write-back non-goal above.

## Roadmap / Phase Gate

| Phase | Scope | Duration | Exit condition |
|---|---|---|---|
| Phase 1 — MVP | `prd.md` Requirements 1–7: raw log, bitemporal canonical layer, role/permission model, one connector per in-scope source, manual-override-only reconciliation UI, ingestion failure observability, documented entity-matching strategy | 3–4 weeks | All Phase 1 P0 acceptance criteria met and validated against one real trading week of data. Not calendar-gated. |
| Phase 2 — Full build | `prd.md` Requirements 8–10: field-level resolution rule engine, Conversational BI (role-gated, fixed tool set), Smart Exporter | 7–12 weeks, starts after Phase 1 | Depends on Phase 1's entity-matching strategy and canonical layer being validated against real data — do not start Phase 2 rule-engine work before that validation. |
| Unscheduled (P2, Future Considerations) | Automation Hub, Lightspeed REST API migration if unlocked | — | No requirement authorized to build against yet. |

Sequencing constraints (`system-context.md` Build Order Prerequisites) are binding, not advisory: shared scaffolding (raw-log envelope, bitemporal base pattern, role/permission model, tool-calling framework, JSON widget schema) must be locked before parallel connector work starts, and the entity-matching strategy must be designed before the reconciliation UI is implemented.

## Metrics Being Tracked (no numeric targets set — see `prd.md` Success Metrics)

- Leading (Phase 1): ingestion completeness per source, conflict-surfacing rate, time-to-detect ingestion failure, manual-override usage by department/seniority.
- Lagging (post Phase 2): reporting turnaround time vs. manual process, whether management stops cross-checking against source exports, whether Tenzo is displaced.

## Open Questions Blocking Specific Work (canonical: `prd.md` Open Questions)

| Question | Blocks |
|---|---|
| Entity-matching keys/tolerances per canonical entity type | Requirement 5 (reconciliation UI) implementation |
| Full list of department/seniority values beyond BOH/FOH, Staff/Manager/Owner | Requirement 3 permission table population |
| Field-to-role mapping (which fields each department × seniority sees) | Requirement 3 acceptance criteria, Requirement 5 implementation |

Do not implement Requirement 5 or populate Requirement 3's permission table until the corresponding open question is resolved — resolving it is design work, not a formality, per `prd.md`.

## Doc Map

| Need | Read |
|---|---|
| Is X in scope / what phase is X in / exact acceptance criteria | `prd.md` |
| Exact schema/versioning pattern, tech stack, per-source ingestion mechanics, architectural invariants | `system-context.md` |
| What's actually implemented vs. scaffolded right now | root `README.md` |
| Product identity, scope boundary, roadmap position at a glance | this doc |
