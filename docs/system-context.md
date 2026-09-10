---
tags:
  - goldys
---
## Project Context & Objectives

Goldy's pub requires a unified data platform to consolidate disconnected operational systems (POS, payroll, inventory, accounting, reservations, order-at-table, social, comms, website) into a reliable single source of truth. The platform must automatically detect overlapping data mismatches between sources and let staff configure how they're resolved, rather than hardcoding resolution logic per source.

It aims to improve on Tenzo's reporting — but note Tenzo plays two roles here, not one: it's the incumbent tool being superseded for reporting/dashboards, **and** a candidate data source in its own right (it already aggregates CTB, Deputy, and likely Lightspeed via its own connectors and exposes a developer API). Don't build a connector against Tenzo without first confirming whether its API returns raw or pre-aggregated data — pre-aggregated is only useful as a cross-check, not as canonical input, since the bitemporal layer needs raw events.

**Build phasing:** this doc describes the target-state architecture. The 3–4 week MVP should implement the raw → canonical layers, a working ingestion connector for each in-scope source, and a minimal reconciliation UI (manual override is enough — the full field-level rule engine can follow in the 7–12 week full build). Don't reach for hexagonal boilerplate, event-bus infrastructure, or the automation hub in the MVP unless a module explicitly says otherwise below.

## Technology Stack

- **Backend:** Java 25, Spring Boot 3.5.0 (confirmed to support Java 25 — see root `README.md`'s "Verified state"), Spring AI, Spring Data JPA. Built with the committed Gradle wrapper pinned to Gradle 9.5.0 (not an 8.x line) because Gradle itself, not just the Java toolchain it targets, must run on a JVM that can parse Java 25 class files.
- **Frontend:** Next.js (App Router), TypeScript, Tailwind CSS, Recharts/Tremor, shadcn/ui for the component library. Package manager/runtime: Bun (not npm/yarn) — use `bun install`, `bun run`, `bunx shadcn@latest add <component>`.
- **Database:** PostgreSQL 16+ (`JSONB` for raw staging, normalized tables for canonical/resolved data).
- **Architecture pattern:** Ports-and-Adapters (hexagonal) at the connector boundary, so each vendor integration is a swappable adapter behind a common port. "Event-driven" here means an append-only log that downstream jobs poll and replay, not a commitment to message-bus infrastructure (Kafka/SQS) — don't introduce one unless a specific throughput or fan-out problem requires it.

## Data Model: Three Layers

This is the core invariant of the whole system. Every module description below should be read against it.

1. **Raw event log (append-only, immutable).** Every piece of ingested content is stored byte-faithful, exactly as received, before any transformation — regardless of what shape it arrived in. This is _not_ limited to API JSON payloads: it equally covers a CSV export, a scraped HTML/DOM extract, or a manually entered record. Store the raw bytes/text plus source metadata (source system, fetch method, timestamp, fetcher identity) in a `JSONB`/blob staging table. See "Per-Source Ingestion Reality" below — most MVP sources are _not_ clean API JSON, and this layer's design must not assume otherwise.
2. **Bitemporal canonical entity state.** Raw records are mapped to vendor-agnostic canonical entities (e.g. `CanonicalShift`, `CanonicalSaleItem`) so downstream code never depends on a specific vendor's schema. "Bitemporal" is a concrete schema commitment, not a description: every canonical table carries both _valid time_ (when the fact was true in the real world) and _system/transaction time_ (when we recorded it) — e.g. `valid_from`/`valid_to` plus `recorded_at`/`superseded_at`. Pin this pattern once, here, so every module implements versioning the same way rather than each LLM session inventing its own.
3. **Recomputable resolved views.** Where two or more sources disagree about the same canonical entity, the Reconciliation Engine's output is a _resolved view_, derived from canonical state by applying the currently active resolution rules — it does not mutate canonical rows. Because it's derived, changing a resolution rule and recomputing must reproduce a consistent result without touching history. Reporting, dashboards, and the Automation Hub all read from resolved views, never directly from canonical or raw data.

## Architectural Invariants

- **Immutable raw ingestion:** store ingested content exactly as received (any shape — API JSON, CSV, scraped page extract, manual entry) before transformation, to protect against vendor schema drift and to preserve an audit trail independent of how the data was obtained.
- **Bitemporal canonical decoupling:** all external data maps to vendor-agnostic canonical entities carrying explicit valid-time and system-time, so downstream code is unaffected if a provider is swapped and past states remain queryable as-of any point in time.
- **Recomputable resolution:** the Reconciliation Engine writes resolved views derived from canonical state, not in-place edits — resolution rule changes must be replayable against history.
- **Field-level, UI-configurable reconciliation:** conflicts are surfaced and resolved at the individual field level (e.g. `quantity_sold` from Lightspeed vs. CTB, not "this order disagrees somehow"), and resolution rules are authored through a UI by non-engineers — not hardcoded in Java or config files shipped with a deploy.
- **Restricted, tool-mediated AI access:** the conversational AI assistant answers questions through a fixed set of purpose-built tools (e.g. `get_sales_by_period`, `get_labor_cost_variance`), each backed by its own parameterized query against the semantic layer over resolved views. It does not have a generic "run this query/view" tool and never generates freeform SQL — the tool boundary is what keeps it safe, not query parameterization alone.
- **JSON-driven UI generation:** when building dashboards, the assistant outputs standard JSON widget specifications for the frontend to render, rather than generating executable frontend code directly.
- **Connector isolation:** each vendor integration lives behind a common port interface; swapping or adding a source should never require changes outside its adapter.
- **No write-back to source systems:** every connector is one-way, source → platform, permanently — at no phase does the platform write corrected data, updates, or any other payload back into Lightspeed, CTB, OpenTable, or Deputy. Automation Hub's outbound actions (e.g. Slack alerts, exports) are not write-backs to a connected source system and remain the only write path this platform has. This is a permanent architectural constraint, not an MVP-only deferral (see `prd.md` Non-Goals).

## Build Order & Design Prerequisites

These are sequencing constraints, not suggestions — building modules out of this order creates rework because later pieces assume the earlier ones already exist in a fixed shape.

1. **Shared scaffolding before parallel module work.** Before any connector or module is built in parallel, four pieces must exist and be locked: the raw-log staging envelope (source, fetch method, content type, raw payload, fetched-at), the bitemporal base entity/repository pattern, the tool-calling framework for AI access, and the JSON widget schema. If each of these is instead defined ad hoc by whichever module happens to need it first, later modules will diverge from it rather than reuse it, and the divergence surfaces as integration bugs rather than design discussion.
2. **Entity-matching strategy is a required design step, not an implementation detail.** The Reconciliation Engine assumes two records from different sources can be identified as describing the same underlying event before it can compare their fields — but nothing else in this doc says how that matching happens when sources don't share a common ID. This must be explicitly designed (e.g. what keys, tolerances, or fuzzy-matching rules identify "the same entity" for each entity type) before the Reconciliation Engine is implemented, not discovered inside its implementation.
3. **AI tool-calling uses a fixed, enum-validated parameter surface.** Each tool exposed to the conversational AI takes a bounded set of dimensions, metrics, and filters validated against a whitelist — not arbitrary field names or free-text filter expressions. A tool that accepts open-ended parameters is freeform query generation with extra steps, and defeats the point of the tool boundary.
4. **Ingestion failure must be observable, not just ingestion success.** Any connector must surface when it fails to fetch or fails partway through, as a first-class state in the raw log (not merely an absence of new rows). Silent ingestion gaps will otherwise surface later as apparent reconciliation mismatches, which is the wrong layer to be debugging a connector failure from.
5. **Reporting and dashboards read only from resolved views, never from canonical or raw tables directly**, even before the Reconciliation Engine's UI is built out — this keeps the read path stable regardless of how much of the resolution logic exists yet.

## Per-Source Ingestion Reality (flag before designing a connector)

Design assumptions about API access must be checked against what's actually available — several MVP sources have no or partial APIs:

|Source|API access|Working ingestion path|
|---|---|---|
|**Lightspeed (O-Series / Kounta)**|Public REST API exists but is paid-add-on-gated on the current plan (or ask support to unlock the legacy free tier)|Authenticated back-office scrape of server-rendered pages (working today); richer REST API if unlocked|
|**Cooking the Books (CTB)**|No public API|Self-serve Custom Invoice Export (CSV/XLSX → scheduled SFTP/email) for production; internal-endpoint scrape exists but is fragile/unsupported — schema-discovery only, not production|
|**OpenTable**|Partner-gated, no self-serve API|Manual/scripted CSV pull from GuestCenter reporting — plan a scripted browser pull, not a manual export step|
|**Deputy**|Genuine self-serve OAuth REST API|Standard API connector — cleanest of the five once someone with admin access registers the OAuth client|
|**Tenzo**|Has a developer API, tier/rawness unconfirmed|Confirm with account manager before treating as a source; useful as cross-check even if aggregated-only|

Note CTB is owned by Quantaco, a hospitality analytics competitor — any request for data broader than the standard export should be framed carefully and isn't guaranteed goodwill.

## Key System Modules

|**Module**|**Core Functionality**|
|---|---|
|**Reconciliation Engine**|Detects field-level discrepancies between canonical entities from different sources; staff configure resolution rules through a UI (priority-by-source, manual override, custom logic); writes recomputable resolved views, never edits canonical data in place.|
|**Conversational BI**|AI interface that clarifies the user's reporting goal via conversation, then calls a fixed set of purpose-built tools over the semantic layer (resolved views) to fetch data, and emits JSON widget specs for the frontend to render as dashboards/reports. Shares its tool-calling and semantic-layer infrastructure with Smart Exporter rather than duplicating it.|
|**Automation Hub**|Defines triggers (conditions evaluated against resolved-view data, not raw or canonical state) that fire workflows/actions across connected platforms. Any action that writes back to an external system (e.g. posting to Slack, updating a calendar) needs its own auth/permission design — this is a write path, distinct from every other module's read-only ingestion. Out of scope for the MVP.|
|**Smart Exporter**|AI-assisted utility using the same tool-calling infrastructure as Conversational BI to extract data and format presentation-ready tables for Excel export.|

## Open Questions / Not Yet Decided

- **User/role model shape is decided; field-to-role mapping is not.** `prd.md` Requirement 3 fixes the model as department × seniority. What remains open is which specific fields/entities each (department, seniority) combination can see — tracked in `prd.md`'s Open Questions, needed before Requirement 3's permission table is populated and before the reconciliation UI (Requirement 5) is built against it.

## Working Notes for LLM-Authored Code

This codebase is being built primarily by LLM coding sessions, with a Java-proficient human collaborator likely joining later. Favor conventional, idiomatic Java/TypeScript over clever or terse solutions; comment non-obvious decisions (especially bitemporal query logic and reconciliation rule evaluation) rather than relying on the code being self-explanatory to whoever picks it up next.