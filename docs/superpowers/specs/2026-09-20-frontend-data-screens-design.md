# Goldy's Frontend Data Screens (Demo-Mode Build-Out)

**Status:** Draft — for review
**Date:** 2026-09-20
**Scope:** `frontend/` only. Builds the three remaining Phase 1 screens against a typed contract, driven by a demo-mode flag. No backend, schema, or connector changes.

## 1. Objective

Build out the Dashboard, Reconciliation, and Connectors screens so the full Phase 1
frontend is usable end-to-end now, while the backend data contracts are still being
defined. The UI is driven by a single `demo` boolean: when on, a fixtures module
provides realistic example data behind a visible "Demo data" banner; when off, the
same screens call the real (as-yet-unimplemented) endpoints and fall back to honest
empty/error states.

This honors the "never fabricate operational data" invariant: fixtures are explicitly
demo mode, clearly labeled, and never presented as production data.

## 2. Sources of Truth and Current Baseline

- Architecture/scope: `docs/system-context.md`, `docs/prd.md`.
- Screen behavior and visual treatment: `docs/design-system.md` (§3 screen inventory,
  §5 status tokens, §6 reconciliation exception-first + permission-denied patterns).
- Reconciliation concepts: the Phase 1 spec's Requirements 5–6 (field-level conflicts,
  manual override, "no data from source X", connector failure states).
- Baseline: the application shell, typed `fetchApi` client, and state/feedback
  components (Empty/Error/Loading/PermissionDenied/toast) from the merged frontend
  shell; the four screens currently render honest empty states.

## 3. Scope

### In scope

- A typed frontend API contract for dashboard summary, reconciliation exceptions and
  records, connector status, and overrides.
- A demo-mode flag (env default + runtime toggle) with an app-wide "Demo data" banner.
- A fixtures data source (`demoApi`) that is mutable in memory, so the override flow
  genuinely resolves an exception and drops it from the list.
- A live data source (`liveApi`) that calls the real endpoints via `fetchApi`.
- The three screens: Dashboard, Reconciliation (list + drill-in + override), Connectors.
- A demo-mode switch in Settings.

### Out of scope

- Any backend endpoint, DTO, schema, connector, matching logic, or canonical/resolved
  computation (the draft contract is a proposal for the backend to adopt later).
- The field-to-role permission matrix values (screens stay permission-aware via
  `/api/me`, but do not hardcode the matrix).
- Conversational BI, Smart Exporter, Automation Hub (Phase 2).

## 4. Architecture

A data-source layer with two interchangeable implementations behind one flag:

```text
screens ──▶ useApi() ──▶ demo ? demoApi (fixtures) : liveApi (fetchApi)
                 ▲
                 └─ useDemoMode() { demo, setDemo }  (env default + runtime toggle)
```

- `lib/api/types.ts` — the contract types.
- `lib/api/demo.ts` — `demoApi` (in-memory, mutable fixtures + small latency).
- `lib/api/live.ts` — `liveApi` (real `fetchApi` calls).
- `lib/demo-mode.tsx` — `DemoModeProvider`, `useDemoMode`, `useApi`.
- `components/demo-banner.tsx` — the "Demo data" strip.

Screens never import `demo.ts` or `live.ts` directly; they call the `Api` interface via
`useApi()`. Toggling demo mode re-renders all screens through the same code path.

## 5. Contract

```ts
interface DashboardSummary {
  ingestionCompleteness: number | null;      // 0–100, null = no data
  openConflicts: number;
  timeToDetectFailure: string | null;        // e.g. "42m avg"
  overrideUsage: { count: number; period: string } | null;
}

interface SourceValue {
  source: string;             // "Lightspeed", "Cooking the Books", "OpenTable", "Deputy"
  value: string | null;       // null = no data from this source
}

type ExceptionStatus = "conflict" | "missing";

interface ReconciliationException {
  id: string;
  entity: string;             // e.g. "Sale #4821"
  field: string;              // e.g. "quantity_sold"
  sources: SourceValue[];
  status: ExceptionStatus;    // conflict = sources disagree; missing = a source has none
}

interface ReconciliationField {
  name: string;
  label: string;
  sources: SourceValue[];
  overridden: boolean;
  authoritativeSource?: string;
}

interface ReconciliationRecord {
  id: string;
  entity: string;
  entityType: string;         // "sale" | "shift"
  fields: ReconciliationField[];
}

type ConnectorRunStatus = "success" | "partial" | "failed" | "no_new_data" | "never_run";

interface ConnectorStatus {
  source: string;
  connectorName: string;
  lastRunAt: string | null;
  status: ConnectorRunStatus;
  failureCount: number;
}

interface SaveOverrideInput {
  recordId: string;
  field: string;
  source: string;             // which source's value is authoritative
  reason?: string;
}

interface OverrideResult {
  ok: true;
  recordId: string;
  field: string;
}

interface Api {
  getDashboardSummary(): Promise<DashboardSummary>;
  listReconciliationExceptions(): Promise<ReconciliationException[]>;
  getReconciliationRecord(id: string): Promise<ReconciliationRecord>;
  listConnectorStatuses(): Promise<ConnectorStatus[]>;
  saveOverride(input: SaveOverrideInput): Promise<OverrideResult>;
}
```

## 6. Demo Mode

- `useDemoMode()` returns `{ demo, setDemo }`.
- Default: `localStorage.getItem("goldys-demo-mode")`, else
  `process.env.NEXT_PUBLIC_DEMO_MODE !== "false"` (i.e. **demo on by default** for now).
- The Settings switch flips it at runtime and persists to `localStorage`.
- When `demo` is true, `DemoBanner` renders "Demo data — not production" app-wide.

## 7. Screens

- **Dashboard** — four stat cards (ingestion completeness, open conflicts,
  time-to-detect, override usage) + a short "open exceptions" list, from
  `getDashboardSummary()`.
- **Reconciliation** — exception-first list of `ReconciliationException` cards; clicking
  one opens the drill-in (`getReconciliationRecord`): a field × source table with
  "No data from source X" badges and conflict highlighting, plus the override form
  (select authoritative source + optional reason + save). On success the exception
  resolves and drops from the list, with a success toast and "overridden" indicator.
- **Connectors** — the four Phase 1 sources with run-state badges (success / partial /
  failed / no-new-data / never-run) and last-run time.
- **Settings** — adds the demo-mode switch.

All data fetches use the existing `LoadingState`/`ErrorState`/`EmptyState`/
`PermissionDenied` components and the `ApiError` envelope.

## 8. Verification

- `bun run typecheck`, `bun run lint`, `bun run build` pass.
- Manual/browser pass: demo banner + toggle visible; all three screens render fixtures;
  override resolves an exception; toggling demo off shows honest empty/error states.
- No backend change; `./gradlew` unaffected.

## 9. Boundaries

### Always

- Screens call the `Api` interface, never `demo.ts`/`live.ts` directly.
- Demo data is always behind the visible banner; never presented as production.
- Reuse the existing state/feedback components and the `ApiError` envelope.
- Keep permission-awareness via `/api/me`, never hardcode the matrix.

### Ask first

- Add production dependencies or change pinned framework versions.
- Touch the backend, schema, or migrations.
- Change the contract in a way that diverges from the Phase 1 spec's concepts.

### Never

- Fabricate data and present it as real (the demo banner is non-optional).
- Hardcode demo fixtures into screen components.
- Introduce a data-fetching library without a stated need.
