# Goldy's Resolution Rule Engine Design

**Status:** In review
**Date:** 2026-09-30
**Scope:** Backend rule engine (PRD Requirement 8) — rule persistence, evaluation,
and endpoints over the two reconciliation units that exist today. No field-level
reconciliation, no frontend live-wiring, no materialized resolved views.

## 1. Objective

Add a staff-configurable **resolution rule engine** on top of the existing
manual-override mechanism, so routine field conflicts resolve themselves via
standing rules ("Cooking the Books wins on daily sales") instead of a human
re-deciding every time.

The engine is scoped to the **two reconciliation units the backend actually
produces today** (see §2), not to the PRD's aspirational per-field granularity —
field-level reconciliation is a separate, larger prerequisite and out of scope
here.

## 2. Sources of Truth and Baseline

- Requirements/scope: `docs/prd.md` Requirement 8 (rule engine) and its
  Non-Goals (no AI auto-resolution; rules are always staff-authored).
- Architecture invariants: `docs/system-context.md` — resolved views are
  **derived** from canonical state + rules, never mutate canonical rows, and are
  reproducible on recompute.
- UI contract: `docs/superpowers/specs/2026-09-30-phase-two-ui-design.md` §5 and
  the already-merged resolution-rules frontend (`liveApi` stubs).

Current baseline (verified against the tree):

- **Daily sales reconciliation** (`DailySalesReconciliationService`): reconciles
  one metric — `total_sales` — per `trading_date` across sources. Field name is
  `"daily_sales"`. Status: `missing` (<2 sources), `agreed`, or `conflict`.
- **Product sales reconciliation** (`ProductSalesReconciliationService`):
  reconciles a combined `quantity × amount` per `product_name_key` + date.
- **Resolution today**: manual override only — append-only `daily_sales_override`
  and `product_sales_override` tables, superseded rather than mutated. `resolved()`
  is computed at read time (override → agreed → null).
- **There is no rule entity, no rule evaluation, no recompute**, and no
  `/api/reconciliation/rules*` endpoints.

## 3. Scope

### In scope

- `resolution_rule` persistence (append-only, supersede pattern).
- Rule evaluation: priority-by-source, manual, and declarative custom logic
  (`flag`, `highest`, `lowest`, `newest`), applied inside the two existing
  reconciliation services.
- REST endpoints: rules CRUD, audit, recompute status.
- Permission enforcement via the existing `PermissionService`.
- Audit as the append-only rule history.

### Out of scope

- Field-level reconciliation (reconciling `quantity_sold`, `amount`, `gst_total`
  independently) — a prerequisite the PRD names but that does not exist yet.
- Materialized resolved views / batch recompute jobs (resolution stays derived).
- Frontend live-wiring and aligning the demo fixtures/`ResolutionRule` type to
  the real `entity_type`/`field_key` vocabulary (follow-up).
- Deputy connector, Conversational BI, Smart Exporter, Automation Hub.

## 4. Data Model

One new Flyway migration (`V12__resolution_rule.sql`), modeled on the existing
override tables:

```sql
CREATE TABLE resolution_rule (
    id uuid PRIMARY KEY,
    entity_type varchar(64) NOT NULL,          -- 'daily_sales' | 'product_sales'
    field_key varchar(512) NOT NULL,           -- 'daily_sales', or a product name key, or '*' (all products)
    strategy varchar(32) NOT NULL,             -- 'priority' | 'manual' | 'custom'
    custom_logic varchar(32),                  -- 'flag' | 'highest' | 'lowest' | 'newest' (when strategy='custom')
    source_priority jsonb,                     -- ordered source list (when strategy='priority')
    actor_email varchar(255) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    superseded_at timestamp(6) with time zone
);

CREATE UNIQUE INDEX ux_resolution_rule_current
    ON resolution_rule (entity_type, field_key)
    WHERE superseded_at IS NULL;
```

- **`entity_type`** is one of `daily_sales` or `product_sales`.
- **`field_key`** is the unit within the entity type: the constant `"daily_sales"`
  for daily sales; a specific product name key for a product rule; or `"*"` for
  a catch-all "all products" rule.
- A rule is **current** iff `superseded_at IS NULL`; editing or deleting supersedes
  the current row and appends a successor (or nothing, for delete). Rows are never
  mutated.
- `source_priority` is stored as JSONB (`["Cooking the Books","Lightspeed"]`);
  the JPA mapping is `@JdbcTypeCode(SqlTypes.JSON)` over a `List<String>`, or a
  plain `String` holding the JSON — fixed in the implementation plan.

## 5. Evaluation Semantics

For a reconciliation unit `(entity_type, field_key)` with its per-source values,
resolution precedence is:

1. **Manual override** wins (unchanged — record-specific overrides rule).
2. **All sources agree** → resolved (unchanged — nothing to resolve).
3. **Conflict or missing** → apply the most-specific current rule for that unit:
   - **`priority`** — pick the first source in `source_priority` that has data
     for the unit; if none has data, unresolved.
   - **`manual`** — unresolved (always ask a human).
   - **`custom` `flag`** — unresolved (flag for a human).
   - **`custom` `highest` / `lowest`** — pick the source with the max/min reconciled
     metric: `total_sales` for `daily_sales`, `quantity` for `product_sales`.
   - **`custom` `newest`** — pick the source whose canonical row for the unit has
     the most recent `recorded_at`.
4. **No rule** → unresolved (today's behavior — the conflict stays in the
   open-exceptions list).

Rule lookup is **most-specific-first**: for `product_sales`, a rule on the exact
product key wins over the `"*"` catch-all. `daily_sales` has a single field key
(`"daily_sales"`), so it is effectively an exact-match lookup.

Applying a rule never mutates canonical rows or override rows; it only changes the
derived answer, so the bitemporal and append-only guarantees hold unchanged.

## 6. API Contract

Fills the `liveApi` stubs the frontend already calls. All endpoints are
permission-gated on `reconciliation.sales` (READ for queries, WRITE for mutations).

- `GET /api/reconciliation/rules` → current rules.
- `POST /api/reconciliation/rules` → create or update (supersede the current rule
  for the same `entity_type`+`field_key`).
- `DELETE /api/reconciliation/rules/{id}` → supersede (soft delete).
- `GET /api/reconciliation/rules/audit` → change history (created/updated/deleted,
  actor, timestamp), newest first.
- `GET /api/reconciliation/recompute/status` → `{ state: "complete", lastChangedAt }`
  (derived views are always current; `lastChangedAt` is the most recent rule change).

DTOs (backend-owned, versionable):

```java
record RuleDto(
    UUID id,
    String entityType,      // "daily_sales" | "product_sales"
    String fieldKey,        // "daily_sales", a product key, or "*"
    String strategy,        // "priority" | "manual" | "custom"
    List<String> sourcePriority,
    String customLogic,     // nullable
    Instant updatedAt,
    String updatedBy) {}

record RuleAuditDto(
    UUID id, UUID ruleId,   // null for a delete
    String entityType, String fieldKey,
    String change,          // "created" | "updated" | "deleted"
    Instant at, String by) {}

record RecomputeStatusDto(String state, Instant lastChangedAt) {}
```

Note: the frontend `ResolutionRule` type uses `entity`/`field` with demo values
(`Sales`/`quantity_sold`). Aligning that type and the demo fixtures to the real
`entity_type`/`field_key` vocabulary is a separate follow-up, not part of this
backend slice.

## 7. Permission and Audit

- **Permission:** reuse `PermissionService` with the existing
  `ResourceKey("reconciliation.sales")` and `PermissionAction.READ`/`WRITE`,
  identical to the override endpoints. Field-to-role (BOH vs FOH field) gating is
  deferred until the PRD's field-to-role mapping open question is resolved.
- **Audit:** the rule's append-only history is the audit — every superseded row
  and every new row is a change. No separate audit table.

## 8. Recompute

Resolution remains **derived** (a pure function of canonical rows + rules +
overrides). There is no recompute job, no materialized resolved-view table, and no
staleness window; changing a rule changes derived answers immediately. The
`recompute/status` endpoint reports `"complete"` with the last rule-change time so
the UI banner can show "rules apply immediately / last changed at X".

## 9. Decisions and Open Questions

Confirmed during review:

- **`newest`** = `recorded_at` (system time).
- **`highest`/`lowest` on product sales** compares `quantity`.

Open:

- **Field-to-role mapping** — deferred (PRD open question); the mechanism is
  built now, the matrix gates rule authoring later.

## 10. Testing Strategy

- Unit tests for rule evaluation: each strategy × (conflict/missing) × (data
  present/absent), precedence (override > rule > agreement), and
  most-specific-first product matching.
- Integration tests (PostgreSQL/Testcontainers): rule CRUD supersession, the
  unique-current index, audit ordering, and permission denial (`NOT_PERMITTED`).
- Reproducibility: changing a rule and re-deriving must be deterministic and must
  not mutate canonical/override rows.
- API tests: response shapes, validation, and error envelopes for the five
  endpoints.

## 11. Boundaries

- Never auto-resolve via heuristics/ML — only staff-authored rules.
- Never mutate canonical or override rows; rules are append-only.
- Read only resolved/canonical data — never write back to a source system.
- Keep the `reconciliation.sales` resource key; no new permission axes here.
