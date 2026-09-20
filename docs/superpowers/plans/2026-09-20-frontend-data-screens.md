# Frontend Data Screens (Demo-Mode Build-Out) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Dashboard, Reconciliation, and Connectors screens against a typed contract, driven by a `demo` boolean (fixtures vs live API), with a visible demo banner and a Settings toggle.

**Architecture:** A data-source layer with two interchangeable implementations — `demoApi` (in-memory, mutable fixtures) and `liveApi` (`fetchApi` calls) — selected by `useApi()` from a `DemoModeProvider`. Screens call the `Api` interface and never import the implementations directly, so toggling demo mode re-renders everything through the same code path.

**Tech Stack:** Next.js 15, React 19, TypeScript 5.6, Tailwind, shadcn/ui, Bun.

**Spec:** `docs/superpowers/specs/2026-09-20-frontend-data-screens-design.md`

## Global Constraints

- Frontend-only; no backend, schema, or connector changes.
- Screens call the `Api` interface via `useApi()`; never import `demo.ts`/`live.ts` directly.
- Demo data is always behind the visible `DemoBanner`; never presented as production.
- Reuse the existing `LoadingState`/`ErrorState`/`EmptyState`/`PermissionDenied`/toast components and the `ApiError` envelope.
- Permission-awareness stays via `/api/me`; never hardcode the field-to-role matrix.
- Keep Next.js 15 / React 19 / Tailwind pinned; no new dependencies.
- Never stage the pre-existing unrelated untracked files (`.opencode/`, `data/`, `opencode.jsonc.backup.*`, root `package-lock.json`).

## Review Focus

1. Toggling demo off mid-session must re-fetch through `liveApi` and show honest empty/error states — never stale fixture data. Pinned in Task 3/4.
2. `saveOverride` in demo mode must actually mutate the fixtures so the exception resolves and drops from the list (the demo must *function*, not just render). Pinned in Task 2/5.
3. The `useApiData` hook must not re-fetch in an infinite loop (inline `fetcher` identity). Pinned in Task 3.
4. A "no data from source X" case (a `null` source value) must render the `--status-missing` badge, never a blank cell. Pinned in Task 5.
5. An `ApiError` from the live path (backend down/404) must surface via `ErrorState` with Retry, never a crash. Pinned in Task 3/6.

---

### Task 1: Contract types and the `Api` interface

**Files:**
- Modify: `frontend/lib/api/types.ts`
- Modify: `frontend/lib/api/index.ts`

**Interfaces:**
- Consumes: existing `fetchApi`, `ApiError`, `isApiError` in `lib/api`.
- Produces: the contract types (`DashboardSummary`, `SourceValue`, `ExceptionStatus`, `ReconciliationException`, `ReconciliationField`, `ReconciliationRecord`, `ConnectorRunStatus`, `ConnectorStatus`, `SaveOverrideInput`, `OverrideResult`) and the `Api` interface, all re-exported from `@/lib/api`.

- [ ] **Step 1: Append the contract types to `frontend/lib/api/types.ts`**

```ts
/** A source's value for a field. `value: null` means the source has no data. */
export interface SourceValue {
  source: string;
  value: string | null;
}

export type ExceptionStatus = "conflict" | "missing";

export interface ReconciliationException {
  id: string;
  entity: string;
  field: string;
  sources: SourceValue[];
  status: ExceptionStatus;
}

export interface ReconciliationField {
  name: string;
  label: string;
  sources: SourceValue[];
  overridden: boolean;
  authoritativeSource?: string;
}

export interface ReconciliationRecord {
  id: string;
  entity: string;
  entityType: string;
  fields: ReconciliationField[];
}

export type ConnectorRunStatus = "success" | "partial" | "failed" | "no_new_data" | "never_run";

export interface ConnectorStatus {
  source: string;
  connectorName: string;
  lastRunAt: string | null;
  status: ConnectorRunStatus;
  failureCount: number;
}

export interface DashboardSummary {
  ingestionCompleteness: number | null;
  openConflicts: number;
  timeToDetectFailure: string | null;
  overrideUsage: { count: number; period: string } | null;
}

export interface SaveOverrideInput {
  recordId: string;
  field: string;
  source: string;
  reason?: string;
}

export interface OverrideResult {
  ok: true;
  recordId: string;
  field: string;
}

/** The data contract the screens depend on. `demoApi` and `liveApi` both implement it. */
export interface Api {
  getDashboardSummary(): Promise<DashboardSummary>;
  listReconciliationExceptions(): Promise<ReconciliationException[]>;
  getReconciliationRecord(id: string): Promise<ReconciliationRecord>;
  listConnectorStatuses(): Promise<ConnectorStatus[]>;
  saveOverride(input: SaveOverrideInput): Promise<OverrideResult>;
}
```

- [ ] **Step 2: Re-export the new types from `frontend/lib/api/index.ts`**

Replace the trailing `export type { ... }` line with:

```ts
export type {
  Api,
  ConnectorRunStatus,
  ConnectorStatus,
  DashboardSummary,
  ExceptionStatus,
  OverrideResult,
  ReconciliationException,
  ReconciliationField,
  ReconciliationRecord,
  SaveOverrideInput,
  SourceValue,
} from "./types";
```

- [ ] **Step 3: Verify**

```bash
cd frontend && bun run typecheck
```

Expected: passes (types are additive; nothing imports them yet).

- [ ] **Step 4: Commit**

```bash
cd /srv/ai/projects/goldys && git add frontend/lib/api/types.ts frontend/lib/api/index.ts
git commit -m "feat: define frontend data contract types"
```

---

### Task 2: Demo fixtures and live API implementations

**Files:**
- Create: `frontend/lib/api/demo.ts`
- Create: `frontend/lib/api/live.ts`

**Interfaces:**
- Consumes: the `Api` interface and contract types from Task 1, `fetchApi` from `./client`, `ApiError` from `./errors`.
- Produces: `demoApi: Api` (in-memory mutable fixtures with latency) and `liveApi: Api` (real `fetchApi` calls).

- [ ] **Step 1: Write `frontend/lib/api/demo.ts`**

```ts
import { ApiError } from "./errors";
import type {
  Api,
  ConnectorStatus,
  DashboardSummary,
  ReconciliationException,
  ReconciliationRecord,
  SaveOverrideInput,
} from "./types";

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

// In-memory, mutable fixtures so the override flow genuinely resolves an exception
// and it drops from the list — the demo behaves like the real data would.
const records: Record<string, ReconciliationRecord> = {
  "sale-4821": {
    id: "sale-4821",
    entity: "Sale #4821",
    entityType: "sale",
    fields: [
      {
        name: "quantity_sold",
        label: "Quantity sold",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "14" },
          { source: "Cooking the Books", value: "12" },
        ],
      },
      {
        name: "amount",
        label: "Net amount",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "$248.50" },
          { source: "Cooking the Books", value: "$212.00" },
        ],
      },
      {
        name: "gross_sales",
        label: "Gross sales",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "$260.00" },
          { source: "Cooking the Books", value: null },
        ],
      },
    ],
  },
  "sale-4836": {
    id: "sale-4836",
    entity: "Sale #4836",
    entityType: "sale",
    fields: [
      {
        name: "quantity_sold",
        label: "Quantity sold",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "3" },
          { source: "Cooking the Books", value: "3" },
        ],
      },
      {
        name: "amount",
        label: "Net amount",
        overridden: false,
        sources: [
          { source: "Lightspeed", value: "$54.00" },
          { source: "Cooking the Books", value: "$51.25" },
        ],
      },
    ],
  },
};

const connectorStatuses: ConnectorStatus[] = [
  { source: "Lightspeed", connectorName: "lightspeed-scrape", lastRunAt: "2026-09-20T09:05:00Z", status: "success", failureCount: 0 },
  { source: "Cooking the Books", connectorName: "ctb-export", lastRunAt: "2026-09-20T09:00:00Z", status: "partial", failureCount: 1 },
  { source: "Deputy", connectorName: "deputy-api", lastRunAt: "2026-09-19T22:30:00Z", status: "failed", failureCount: 2 },
  { source: "OpenTable", connectorName: "opentable-guestcenter", lastRunAt: null, status: "never_run", failureCount: 0 },
];

function deriveExceptions(): ReconciliationException[] {
  const result: ReconciliationException[] = [];
  for (const record of Object.values(records)) {
    for (const field of record.fields) {
      if (field.overridden) continue;
      const values = field.sources.map((s) => s.value);
      const anyMissing = field.sources.some((s) => s.value === null);
      const allEqual = values.length > 1 && values.every((v) => v === values[0]);
      if (anyMissing || !allEqual) {
        result.push({
          id: `${record.id}:${field.name}`,
          entity: record.entity,
          field: field.name,
          sources: field.sources,
          status: anyMissing ? "missing" : "conflict",
        });
      }
    }
  }
  return result;
}

export const demoApi: Api = {
  async getDashboardSummary(): Promise<DashboardSummary> {
    await delay(400);
    return {
      ingestionCompleteness: 92,
      openConflicts: deriveExceptions().length,
      timeToDetectFailure: "42m avg",
      overrideUsage: { count: 3, period: "this week" },
    };
  },

  async listReconciliationExceptions(): Promise<ReconciliationException[]> {
    await delay(400);
    return deriveExceptions();
  },

  async getReconciliationRecord(id: string): Promise<ReconciliationRecord> {
    await delay(300);
    const record = records[id];
    if (!record) throw new ApiError("VALIDATION_FAILED", `No record with id ${id}.`);
    return record;
  },

  async listConnectorStatuses(): Promise<ConnectorStatus[]> {
    await delay(400);
    return connectorStatuses;
  },

  async saveOverride(input: SaveOverrideInput): Promise<{ ok: true; recordId: string; field: string }> {
    await delay(500);
    const record = records[input.recordId];
    const field = record?.fields.find((f) => f.name === input.field);
    if (!record || !field) {
      throw new ApiError("VALIDATION_FAILED", "Unknown record or field.");
    }
    field.overridden = true;
    field.authoritativeSource = input.source;
    return { ok: true, recordId: input.recordId, field: input.field };
  },
};
```

- [ ] **Step 2: Write `frontend/lib/api/live.ts`**

```ts
import { fetchApi } from "./client";
import type {
  Api,
  ConnectorStatus,
  DashboardSummary,
  ReconciliationException,
  ReconciliationRecord,
  SaveOverrideInput,
} from "./types";

/** Real backend calls. These endpoints don't exist yet, so in live mode the screens
 *  show the existing honest empty/error states until the backend lands. */
export const liveApi: Api = {
  getDashboardSummary: () => fetchApi<DashboardSummary>("/api/dashboard/summary"),
  listReconciliationExceptions: () =>
    fetchApi<ReconciliationException[]>("/api/reconciliation/exceptions"),
  getReconciliationRecord: (id: string) =>
    fetchApi<ReconciliationRecord>(`/api/reconciliation/records/${id}`),
  listConnectorStatuses: () => fetchApi<ConnectorStatus[]>("/api/connectors"),
  saveOverride: (input: SaveOverrideInput) =>
    fetchApi(`/api/reconciliation/records/${input.recordId}/override`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
};
```

- [ ] **Step 3: Verify**

```bash
cd frontend && bun run typecheck && bun run lint
```

Expected: both pass.

- [ ] **Step 4: Commit**

```bash
cd /srv/ai/projects/goldys && git add frontend/lib/api/demo.ts frontend/lib/api/live.ts
git commit -m "feat: add demo fixtures and live api implementations"
```

---

### Task 3: Demo-mode provider, data hook, banner, and wiring

**Files:**
- Create: `frontend/lib/demo-mode.tsx`
- Create: `frontend/lib/use-api-data.ts`
- Create: `frontend/components/demo-banner.tsx`
- Create: `frontend/components/settings/demo-mode-toggle.tsx`
- Modify: `frontend/components/app-shell/app-shell.tsx` (render banner)
- Modify: `frontend/app/(app)/layout.tsx` (wrap in provider)
- Modify: `frontend/app/(app)/settings/page.tsx` (render toggle)

**Interfaces:**
- Consumes: `demoApi`/`liveApi`, `Api` type, `isApiError`/`ApiError`.
- Produces: `useDemoMode()` → `{ demo, setDemo }`; `useApi()` → `Api`; `useApiData<T>(fetcher, deps)` → `{ data, loading, error, reload }`; `<DemoBanner />`; `<DemoModeToggle />`.

- [ ] **Step 1: Write `frontend/lib/demo-mode.tsx`**

```tsx
"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { demoApi } from "@/lib/api/demo";
import { liveApi } from "@/lib/api/live";
import type { Api } from "@/lib/api";

interface DemoModeContextValue {
  demo: boolean;
  setDemo: (demo: boolean) => void;
}

const DemoModeContext = createContext<DemoModeContextValue | null>(null);

export function useDemoMode(): DemoModeContextValue {
  const ctx = useContext(DemoModeContext);
  if (!ctx) throw new Error("useDemoMode must be used within a DemoModeProvider");
  return ctx;
}

export function DemoModeProvider({ children }: { children: ReactNode }) {
  const [demo, setDemoState] = useState<boolean>(() => {
    if (typeof window === "undefined") return true;
    const stored = window.localStorage.getItem("goldys-demo-mode");
    if (stored != null) return stored === "true";
    return process.env.NEXT_PUBLIC_DEMO_MODE !== "false";
  });

  const setDemo = useCallback((value: boolean) => {
    setDemoState(value);
    window.localStorage.setItem("goldys-demo-mode", String(value));
  }, []);

  const value = useMemo(() => ({ demo, setDemo }), [demo, setDemo]);

  return <DemoModeContext.Provider value={value}>{children}</DemoModeContext.Provider>;
}

/** The data source for the current demo/live mode. Screens call this, never demo/live directly. */
export function useApi(): Api {
  const { demo } = useDemoMode();
  return demo ? demoApi : liveApi;
}
```

- [ ] **Step 2: Write `frontend/lib/use-api-data.ts`**

```ts
"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, isApiError, type Api } from "@/lib/api";
import { useApi } from "@/lib/demo-mode";

/**
 * Fetch data through the current Api (demo or live), exposing loading/error/data.
 * `fetcher` is read via a ref (not a dependency) so inline arrow functions don't
 * cause an infinite loop; `deps` controls when to re-fetch.
 */
export function useApiData<T>(fetcher: (api: Api) => Promise<T>, deps: unknown[] = []) {
  const api = useApi();
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setData(null);
    fetcherRef.current(api)
      .then((d) => {
        if (!cancelled) setData(d);
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(isApiError(e) ? e : new ApiError("UNEXPECTED_STATUS", "Something went wrong."));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [api, ...deps]);

  useEffect(() => reload(), [reload]);

  return { data, loading, error, reload };
}
```

- [ ] **Step 3: Write `frontend/components/demo-banner.tsx`**

```tsx
"use client";

import { FlaskConical } from "lucide-react";
import { useDemoMode } from "@/lib/demo-mode";

export function DemoBanner() {
  const { demo } = useDemoMode();
  if (!demo) return null;
  return (
    <div className="flex items-center gap-2 border-b bg-status-warning/15 px-4 py-1.5 text-xs font-medium text-status-warning-foreground">
      <FlaskConical className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>Demo data — not production. Toggle demo mode in Settings.</span>
    </div>
  );
}
```

- [ ] **Step 4: Write `frontend/components/settings/demo-mode-toggle.tsx`**

```tsx
"use client";

import { useDemoMode } from "@/lib/demo-mode";

export function DemoModeToggle() {
  const { demo, setDemo } = useDemoMode();

  return (
    <div className="flex items-center justify-between rounded-lg border p-4">
      <div>
        <p className="text-sm font-medium">Demo data</p>
        <p className="text-sm text-muted-foreground">
          Use sample data instead of the live backend.
        </p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={demo}
        onClick={() => setDemo(!demo)}
        className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
          demo ? "bg-primary" : "bg-muted"
        }`}
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
            demo ? "translate-x-6" : "translate-x-1"
          }`}
        />
      </button>
    </div>
  );
}
```

- [ ] **Step 5: Render the banner in `frontend/components/app-shell/app-shell.tsx`**

Add the import and render it above `AppHeader`:

```tsx
import { DemoBanner } from "@/components/demo-banner";
// ...
<SidebarInset>
  <DemoBanner />
  <AppHeader />
  <main className="flex flex-1 flex-col gap-6 p-4 md:p-6">
    <ScreenErrorBoundary>{children}</ScreenErrorBoundary>
  </main>
</SidebarInset>
```

- [ ] **Step 6: Wrap the layout in `DemoModeProvider`**

In `frontend/app/(app)/layout.tsx`, add the import and wrap:

```tsx
import { DemoModeProvider } from "@/lib/demo-mode";
// ...
<CurrentUserProvider>
  <DemoModeProvider>
    <ToastProvider>
      <AppShell>{children}</AppShell>
    </ToastProvider>
  </DemoModeProvider>
</CurrentUserProvider>
```

- [ ] **Step 7: Render the toggle in `frontend/app/(app)/settings/page.tsx`**

Add the import and render it above the deferred note:

```tsx
import { DemoModeToggle } from "@/components/settings/demo-mode-toggle";
// ... after <IdentityCard />
<DemoModeToggle />
```

- [ ] **Step 8: Verify**

```bash
cd frontend && bun run typecheck && bun run lint && bun run build
```

Expected: all pass.

- [ ] **Step 9: Commit**

```bash
cd /srv/ai/projects/goldys && git add frontend/lib/demo-mode.tsx frontend/lib/use-api-data.ts frontend/components/demo-banner.tsx frontend/components/settings/demo-mode-toggle.tsx frontend/components/app-shell/app-shell.tsx "frontend/app/(app)/layout.tsx" "frontend/app/(app)/settings/page.tsx"
git commit -m "feat: add demo-mode provider, banner, and data hook"
```

---

### Task 4: Dashboard screen

**Files:**
- Create: `frontend/components/dashboard/stat-card.tsx`
- Modify: `frontend/app/(app)/dashboard/page.tsx`

**Interfaces:**
- Consumes: `useApiData`, `getDashboardSummary` via the `Api` interface, `DashboardSummary`.
- Produces: a Dashboard page rendering four stat cards + an open-exceptions note, with loading/error/empty states.

- [ ] **Step 1: Write `frontend/components/dashboard/stat-card.tsx`**

```tsx
import type { LucideIcon } from "lucide-react";

interface StatCardProps {
  label: string;
  value: string;
  hint?: string;
  icon: LucideIcon;
}

export function StatCard({ label, value, hint, icon: Icon }: StatCardProps) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Icon className="h-4 w-4" aria-hidden="true" />
        <span>{label}</span>
      </div>
      <p className="mt-3 text-2xl font-semibold">{value}</p>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
```

- [ ] **Step 2: Rewrite `frontend/app/(app)/dashboard/page.tsx` as a client component**

```tsx
"use client";

import { Activity, Scale, Timer, Wrench } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { StatCard } from "@/components/dashboard/stat-card";

export default function DashboardPage() {
  const { data, loading, error, reload } = useApiData((api) => api.getDashboardSummary());

  if (loading) return <LoadingState rows={2} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load the dashboard"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }
  if (!data) {
    return (
      <EmptyState
        title="No dashboard data"
        description="Metrics will appear once connectors run and conflicts are surfaced."
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Operational overview of ingestion and reconciliation.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Ingestion completeness"
          value={data.ingestionCompleteness == null ? "—" : `${data.ingestionCompleteness}%`}
          hint="Share of scheduled connector runs that succeed"
          icon={Activity}
        />
        <StatCard
          label="Open conflicts"
          value={String(data.openConflicts)}
          hint="Fields awaiting a decision"
          icon={Scale}
        />
        <StatCard
          label="Time to detect failure"
          value={data.timeToDetectFailure ?? "—"}
          hint="How quickly a failed run becomes visible"
          icon={Timer}
        />
        <StatCard
          label="Manual overrides"
          value={data.overrideUsage ? String(data.overrideUsage.count) : "—"}
          hint={data.overrideUsage ? data.overrideUsage.period : "How often staff resolve by hand"}
          icon={Wrench}
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Verify**

```bash
cd frontend && bun run typecheck && bun run lint && bun run build
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
cd /srv/ai/projects/goldys && git add frontend/components/dashboard/stat-card.tsx "frontend/app/(app)/dashboard/page.tsx"
git commit -m "feat: build dashboard stat cards against the data contract"
```

---

### Task 5: Reconciliation screen (list, drill-in, override)

**Files:**
- Create: `frontend/components/reconciliation/drill-in.tsx`
- Modify: `frontend/app/(app)/reconciliation/page.tsx`

**Interfaces:**
- Consumes: `useApiData`, the reconciliation types, `useToast`, `Badge`, `Button`.
- Produces: an exception-first list that opens a per-record drill-in with a field × source comparison and a manual-override form; override resolves the exception and refreshes the list.

- [ ] **Step 1: Write `frontend/components/reconciliation/drill-in.tsx`**

```tsx
"use client";

import { useState } from "react";
import { ArrowLeft } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import type { ReconciliationField } from "@/lib/api";

interface DrillInProps {
  recordId: string;
  onBack: () => void;
  onSave: (field: string, source: string, reason?: string) => Promise<void>;
  saving: boolean;
}

function needsDecision(field: ReconciliationField): boolean {
  if (field.overridden) return false;
  const values = field.sources.map((s) => s.value);
  const anyMissing = field.sources.some((s) => s.value === null);
  const allEqual = values.length > 1 && values.every((v) => v === values[0]);
  return anyMissing || !allEqual;
}

export function ReconciliationDrillIn({ recordId, onBack, onSave, saving }: DrillInProps) {
  const { data: record, loading, error, reload } = useApiData(
    (api) => api.getReconciliationRecord(recordId),
    [recordId],
  );
  const [selection, setSelection] = useState<Record<string, string>>({});
  const [reasons, setReasons] = useState<Record<string, string>>({});

  if (loading) return <LoadingState rows={3} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load this record"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }
  if (!record) return null;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={onBack} aria-label="Back to list">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        </Button>
        <div>
          <h1 className="text-lg font-semibold">{record.entity}</h1>
          <p className="text-xs text-muted-foreground">Entity type: {record.entityType}</p>
        </div>
      </div>

      <div className="flex flex-col gap-3">
        {record.fields.map((field) => {
          const decide = needsDecision(field);
          return (
            <div key={field.name} className="rounded-lg border bg-card p-4">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">{field.label}</p>
                {field.overridden ? (
                  <Badge variant="secondary">Overridden · {field.authoritativeSource}</Badge>
                ) : decide ? (
                  <Badge variant="outline">Needs decision</Badge>
                ) : null}
              </div>

              <div className="mt-2 grid grid-cols-2 gap-2">
                {field.sources.map((source) => (
                  <div key={source.source} className="rounded-md border p-2">
                    <p className="text-xs text-muted-foreground">{source.source}</p>
                    {source.value == null ? (
                      <p className="mt-1 text-sm text-status-missing">
                        No data from {source.source}
                      </p>
                    ) : (
                      <p className="mt-1 text-sm font-medium">{source.value}</p>
                    )}
                  </div>
                ))}
              </div>

              {decide ? (
                <div className="mt-3 flex flex-col gap-2 border-t pt-3">
                  <p className="text-xs font-medium text-muted-foreground">Set authoritative source</p>
                  <div className="flex flex-wrap gap-2">
                    {field.sources
                      .filter((s) => s.value != null)
                      .map((source) => (
                        <button
                          key={source.source}
                          type="button"
                          onClick={() =>
                            setSelection((prev) => ({ ...prev, [field.name]: source.source }))
                          }
                          className={`rounded-md border px-3 py-1.5 text-sm ${
                            selection[field.name] === source.source
                              ? "border-primary bg-primary/10 font-medium"
                              : "hover:bg-accent"
                          }`}
                        >
                          {source.source}
                        </button>
                      ))}
                  </div>
                  <input
                    type="text"
                    placeholder="Reason (optional)"
                    value={reasons[field.name] ?? ""}
                    onChange={(e) =>
                      setReasons((prev) => ({ ...prev, [field.name]: e.target.value }))
                    }
                    className="rounded-md border px-3 py-1.5 text-sm"
                  />
                  <Button
                    size="sm"
                    disabled={!selection[field.name] || saving}
                    onClick={() =>
                      onSave(field.name, selection[field.name], reasons[field.name] || undefined)
                    }
                  >
                    {saving ? "Saving…" : "Save override"}
                  </Button>
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Rewrite `frontend/app/(app)/reconciliation/page.tsx` as a client component**

```tsx
"use client";

import { useState } from "react";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { useToast } from "@/components/feedback/toast";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { ReconciliationDrillIn } from "@/components/reconciliation/drill-in";

export default function ReconciliationPage() {
  const api = useApi();
  const { data: exceptions, loading, error, reload } = useApiData((a) =>
    a.listReconciliationExceptions(),
  );
  const { toast } = useToast();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function handleSave(field: string, source: string, reason?: string) {
    if (!selectedId) return;
    setSaving(true);
    try {
      await api.saveOverride({ recordId: selectedId, field, source, reason });
      toast({ title: "Override saved", description: `${field} is now resolved from ${source}.`, tone: "success" });
      await reload();
      setSelectedId(null);
    } catch (e) {
      toast({ title: "Couldn't save override", description: e instanceof Error ? e.message : undefined, tone: "error" });
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <LoadingState rows={4} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load exceptions"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }

  if (selectedId) {
    return (
      <ReconciliationDrillIn
        recordId={selectedId}
        onBack={() => setSelectedId(null)}
        onSave={handleSave}
        saving={saving}
      />
    );
  }

  if (!exceptions || exceptions.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <div>
          <h1 className="text-xl font-semibold">Reconciliation</h1>
          <p className="text-sm text-muted-foreground">
            Field-level conflicts between sources, shown exception-first.
          </p>
        </div>
        <EmptyState
          title="No conflicts to review"
          description="When two sources disagree on the same fact, the exception appears here."
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Reconciliation</h1>
        <p className="text-sm text-muted-foreground">
          Field-level conflicts between sources, shown exception-first.
        </p>
      </div>
      <div className="flex flex-col gap-3">
        {exceptions.map((ex) => (
          <button
            key={ex.id}
            type="button"
            onClick={() => setSelectedId(ex.id.split(":")[0])}
            className="flex items-center justify-between rounded-lg border bg-card p-4 text-left hover:bg-accent"
          >
            <div>
              <p className="text-sm font-medium">{ex.entity}</p>
              <p className="text-sm text-muted-foreground">{ex.field}</p>
            </div>
            <Badge variant={ex.status === "conflict" ? "secondary" : "outline"}>
              {ex.status === "conflict" ? "Conflict" : "Missing data"}
            </Badge>
          </button>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Verify**

```bash
cd frontend && bun run typecheck && bun run lint && bun run build
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
cd /srv/ai/projects/goldys && git add frontend/components/reconciliation/drill-in.tsx "frontend/app/(app)/reconciliation/page.tsx"
git commit -m "feat: build reconciliation list, drill-in, and override flow"
```

---

### Task 6: Connectors screen and final verification

**Files:**
- Modify: `frontend/app/(app)/connectors/page.tsx`
- Modify: `docs/testing.md`

**Interfaces:**
- Consumes: `useApiData`, `listConnectorStatuses`, `ConnectorStatus`.
- Produces: a Connectors page rendering the four sources with run-state badges and last-run times; a testing.md note.

- [ ] **Step 1: Rewrite `frontend/app/(app)/connectors/page.tsx` as a client component**

```tsx
"use client";

import { useApiData } from "@/lib/use-api-data";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import type { ConnectorRunStatus } from "@/lib/api";

const STATUS_LABEL: Record<ConnectorRunStatus, string> = {
  success: "Success",
  partial: "Partial",
  failed: "Failed",
  no_new_data: "No new data",
  never_run: "Never run",
};

const STATUS_VARIANT: Record<ConnectorRunStatus, "default" | "secondary" | "destructive" | "outline"> = {
  success: "secondary",
  partial: "outline",
  failed: "destructive",
  no_new_data: "outline",
  never_run: "outline",
};

export default function ConnectorsPage() {
  const { data, loading, error, reload } = useApiData((api) => api.listConnectorStatuses());

  if (loading) return <LoadingState rows={4} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load connector status"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }
  if (!data || data.length === 0) {
    return (
      <EmptyState
        title="No connector data"
        description="Run status will appear here once ingestion starts."
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Connectors</h1>
        <p className="text-sm text-muted-foreground">In-scope Phase 1 sources and their latest run.</p>
      </div>
      <div className="rounded-lg border">
        {data.map((c, i) => (
          <div key={c.source} className={`flex items-center justify-between gap-4 p-4 ${i > 0 ? "border-t" : ""}`}>
            <div>
              <p className="text-sm font-medium">{c.source}</p>
              <p className="text-xs text-muted-foreground">
                {c.connectorName}
                {c.lastRunAt ? ` · last run ${new Date(c.lastRunAt).toLocaleString()}` : " · never run"}
              </p>
            </div>
            <Badge variant={STATUS_VARIANT[c.status]}>{STATUS_LABEL[c.status]}</Badge>
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Record verification in `docs/testing.md`**

Append a note under the frontend section:

```markdown
### Data screens (demo mode)

The Dashboard, Reconciliation, and Connectors screens are driven by a demo-mode flag
(`NEXT_PUBLIC_DEMO_MODE`, default on) with a Settings toggle and a "Demo data" banner.
They call a typed `Api` contract with two implementations: `demoApi` (fixtures) and
`liveApi` (real endpoints, not yet implemented). Verified with `bun run typecheck`,
`bun run lint`, and `bun run build`; demo fixtures are not production data.
```

- [ ] **Step 3: Final verification**

```bash
cd frontend && bun run typecheck && bun run lint && bun run build
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
cd /srv/ai/projects/goldys && git add "frontend/app/(app)/connectors/page.tsx" docs/testing.md
git commit -m "feat: build connectors status screen"
```

---

## Checkpoint: Data Screens Complete

- [ ] `bun run typecheck`, `bun run lint`, `bun run build` pass.
- [ ] Demo banner + Settings toggle visible; toggling re-renders all screens through the same path.
- [ ] Dashboard renders four stat cards from fixtures; Reconciliation lists exceptions, opens the drill-in, and an override resolves + drops the exception; Connectors renders the four sources with status badges.
- [ ] Toggling demo off shows honest empty/error states (no stale fixtures).
- [ ] Screens never import `demo.ts`/`live.ts` directly.
- [ ] Six atomic Conventional Commits; no secrets or unrelated untracked files staged.
