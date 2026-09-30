# Frontend Management IA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reposition the Goldy's frontend from a platform-operations tool to a pub-management tool — a business dashboard, five business sub-pages, and diagnostics moved to a dedicated Data health page — with no backend changes.

**Architecture:** Pure frontend information-architecture change in the existing Next.js App Router app. The dashboard becomes "trust-first, then business": a live "Needs a decision" hero backed by existing endpoints, above a five-tile business KPI grid that renders `AwaitingData` placeholders. The current technical dashboard content and Connectors page merge into a single Data health page; `/connectors` redirects there.

**Tech Stack:** Next.js 15 (App Router), React 19, TypeScript, Tailwind, shadcn/ui, lucide-react, Vitest + @testing-library/react (jsdom), Bun 1.4.2.

**Spec:** `docs/superpowers/specs/2026-09-29-frontend-management-ia-design.md`

## Global Constraints

- Bun only — run everything with `bun run <script>`, never npm/yarn.
- shadcn defaults unmodified (slate, default radius/font); semantic status colors only: `status-success`, `status-warning`, `status-missing`; `destructive` reserved for genuine failures, never conflicts.
- Never render fabricated business numbers as operational data — placeholders must show only label + description + reason.
- Permission denial is a full lock state, never a partial view. Wage/labor-cost surfaces are Owner-only.
- Desktop-first, light mode. Copy is calm/trustworthy, not marketing.
- Verification gates for every task: `bun run typecheck`, `bun run lint`, `bun run build`; where a task adds tests, `bun run test`.
- Atomic Conventional Commits. Never commit to main. Work continues on a feature branch off the current branch (which already contains the spec commit): `git checkout -b feature/frontend-management-ia` before Task 1.

## Review Focus

These are the input classes / failure modes the spec implies but whose happy-path tests wouldn't otherwise exercise. Each is pinned by a test in the named task.

1. **Zero open conflicts** — the hero must render the "All numbers reconcile" success state, not a broken empty count. → Task 2 test (`openCount={0}`).
2. **Exactly one open conflict** — singular copy "1 number needs a decision" (no pluralization bug). → Task 2 test (`openCount={1}`).
3. **Any non-Owner seniority** — the labor tile must lock for anything other than exactly `"Owner"` (including `"Manager"`, `"Staff"`, or a future value). → Task 3 test.
4. **A connector that has never run vs. one that failed** — Data health must keep `never_run` muted vs `failed` destructive. → Task 5 test for `ConnectorStatusBadge`.
5. **A stale `/connectors` deep link** — must still resolve to the diagnostics page, not 404. → Task 5 redirect (`/connectors` → `/data-health`).

---

## Task 1: `AwaitingData` placeholder component

**Files:**
- Create: `frontend/components/states/awaiting-data.tsx`
- Test: `frontend/components/states/awaiting-data.test.tsx`

**Interfaces:**
- Produces: `AwaitingData({ label: string; description: string; reason: string; icon?: LucideIcon })` — a forward-looking placeholder. Used by Tasks 3 and 6.

- [ ] **Step 1: Write the failing test**

`frontend/components/states/awaiting-data.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AwaitingData } from "./awaiting-data";

describe("AwaitingData", () => {
  it("renders label, description, and reason without any fabricated value", () => {
    render(
      <AwaitingData
        label="Sales"
        description="Net sales today and this week vs. last week."
        reason="Awaiting sales-reporting endpoint."
      />,
    );

    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Net sales today and this week vs. last week.")).toBeInTheDocument();
    expect(screen.getByText("Awaiting sales-reporting endpoint.")).toBeInTheDocument();
    // Never renders a numeric/currency value that would imply operational data.
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\d/)).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- components/states/awaiting-data.test.tsx`
Expected: FAIL — cannot find module `./awaiting-data`.

- [ ] **Step 3: Write the component**

`frontend/components/states/awaiting-data.tsx`:

```tsx
import type { LucideIcon } from "lucide-react";
import { Hourglass } from "lucide-react";

interface AwaitingDataProps {
  label: string;
  description: string;
  reason: string;
  icon?: LucideIcon;
}

/**
 * A forward-looking placeholder for a surface that is not wired to data yet.
 * Deliberately renders no values — see docs/design-system.md ("never present
 * mock data as operational"). Distinct from `EmptyState`, which means "the data
 * exists but is empty right now".
 */
export function AwaitingData({
  label,
  description,
  reason,
  icon: Icon = Hourglass,
}: AwaitingDataProps) {
  return (
    <div className="rounded-lg border border-dashed bg-card p-4">
      <div className="flex items-center gap-2 text-sm font-medium">
        <Icon className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
        <span>{label}</span>
      </div>
      <p className="mt-3 text-sm text-muted-foreground">{description}</p>
      <p className="mt-2 text-xs text-muted-foreground/70">{reason}</p>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun run test -- components/states/awaiting-data.test.tsx`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`
Then:

```bash
git add frontend/components/states/awaiting-data.tsx frontend/components/states/awaiting-data.test.tsx
git commit -m "feat: add awaiting-data placeholder component"
```

---

## Task 2: `NeedsDecisionBand` hero component

**Files:**
- Create: `frontend/components/dashboard/needs-decision-band.tsx`
- Test: `frontend/components/dashboard/needs-decision-band.test.tsx`

**Interfaces:**
- Produces: `NeedsDecisionBand({ openCount: number; href?: string })` — the dashboard's live "needs a decision" hero; default `href="/reconciliation"`. Used by Task 4.

- [ ] **Step 1: Write the failing test**

`frontend/components/dashboard/needs-decision-band.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { NeedsDecisionBand } from "./needs-decision-band";

describe("NeedsDecisionBand", () => {
  it("shows the open-conflict state with a review link when conflicts exist", () => {
    render(<NeedsDecisionBand openCount={3} />);
    expect(screen.getByText("3 numbers need a decision")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /review in reconciliation/i }),
    ).toHaveAttribute("href", "/reconciliation");
  });

  it("uses singular copy for exactly one conflict", () => {
    render(<NeedsDecisionBand openCount={1} />);
    expect(screen.getByText("1 number needs a decision")).toBeInTheDocument();
  });

  it("shows the all-reconcile success state when there are no conflicts", () => {
    render(<NeedsDecisionBand openCount={0} />);
    expect(screen.getByText("All numbers reconcile")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /open reconciliation/i }),
    ).toHaveAttribute("href", "/reconciliation");
  });

  it("honors a custom href", () => {
    render(<NeedsDecisionBand openCount={2} href="/reconciliation?tab=open" />);
    expect(screen.getByRole("link", { name: /review in reconciliation/i })).toHaveAttribute(
      "href",
      "/reconciliation?tab=open",
    );
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- components/dashboard/needs-decision-band.test.tsx`
Expected: FAIL — cannot find module `./needs-decision-band`.

- [ ] **Step 3: Write the component**

`frontend/components/dashboard/needs-decision-band.tsx`:

```tsx
import Link from "next/link";
import { Scale } from "lucide-react";
import { Button } from "@/components/ui/button";

interface NeedsDecisionBandProps {
  openCount: number;
  href?: string;
}

/** The dashboard's one live signal: how many numbers still need a decision. */
export function NeedsDecisionBand({ openCount, href = "/reconciliation" }: NeedsDecisionBandProps) {
  const hasConflicts = openCount > 0;
  return (
    <div
      className={`flex flex-col gap-3 rounded-lg border p-4 sm:flex-row sm:items-center sm:justify-between ${
        hasConflicts
          ? "border-transparent bg-status-warning/15"
          : "border-transparent bg-status-success/10"
      }`}
    >
      <div className="flex items-start gap-3">
        <Scale className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div>
          <p className="text-sm font-semibold">
            {hasConflicts
              ? `${openCount} number${openCount === 1 ? "" : "s"} need a decision`
              : "All numbers reconcile"}
          </p>
          <p className="text-sm text-muted-foreground">
            {hasConflicts
              ? "These figures will disagree in your reports until resolved."
              : "Every reported figure currently agrees across your sources."}
          </p>
        </div>
      </div>
      <Button asChild variant={hasConflicts ? "default" : "outline"} size="sm">
        <Link href={href}>
          {hasConflicts ? "Review in Reconciliation" : "Open Reconciliation"}
        </Link>
      </Button>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun run test -- components/dashboard/needs-decision-band.test.tsx`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`
Then:

```bash
git add frontend/components/dashboard/needs-decision-band.tsx frontend/components/dashboard/needs-decision-band.test.tsx
git commit -m "feat: add needs-a-decision dashboard band"
```

---

## Task 3: `BusinessKpiGrid` component

**Files:**
- Create: `frontend/components/dashboard/business-kpi-grid.tsx`
- Test: `frontend/components/dashboard/business-kpi-grid.test.tsx`

**Interfaces:**
- Consumes: `AwaitingData` (Task 1).
- Produces: `BusinessKpiGrid({ seniority?: string })` — the five business KPI tiles; the labor tile locks when `seniority !== "Owner"`. Used by Task 4.

- [ ] **Step 1: Write the failing test**

`frontend/components/dashboard/business-kpi-grid.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { BusinessKpiGrid } from "./business-kpi-grid";

describe("BusinessKpiGrid", () => {
  it("renders all five business tiles for an Owner, including labor", () => {
    render(<BusinessKpiGrid seniority="Owner" />);
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Labor cost %")).toBeInTheDocument();
    expect(screen.getByText("Covers today")).toBeInTheDocument();
    expect(screen.getByText("Top sellers")).toBeInTheDocument();
    expect(screen.getByText("Food cost")).toBeInTheDocument();
    expect(screen.queryByText(/owner only/i)).not.toBeInTheDocument();
  });

  it("locks the labor tile for a non-Owner seniority", () => {
    render(<BusinessKpiGrid seniority="Manager" />);
    expect(screen.queryByText(/awaiting deputy reporting/i)).not.toBeInTheDocument();
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
    // The other four tiles still render.
    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Covers today")).toBeInTheDocument();
  });

  it("locks the labor tile for any value other than Owner", () => {
    render(<BusinessKpiGrid seniority="Staff" />);
    expect(screen.getByText(/owner only/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- components/dashboard/business-kpi-grid.test.tsx`
Expected: FAIL — cannot find module `./business-kpi-grid`.

- [ ] **Step 3: Write the component**

`frontend/components/dashboard/business-kpi-grid.tsx`:

```tsx
import { CalendarDays, Lock, Percent, TrendingUp, Trophy, Utensils } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

interface BusinessKpiGridProps {
  /** Current user's seniority; gates the Owner-only labor tile. */
  seniority?: string;
}

/**
 * The dashboard's five business KPI tiles. All are placeholders until their
 * reporting data lands; the labor tile is additionally Owner-only per the
 * permission model (wage/labor-cost figures).
 */
export function BusinessKpiGrid({ seniority }: BusinessKpiGridProps) {
  const isOwner = seniority === "Owner";
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
      <AwaitingData
        label="Sales"
        description="Net sales today and this week vs. last week."
        reason="Awaiting sales-reporting endpoint."
        icon={TrendingUp}
      />
      {isOwner ? (
        <AwaitingData
          label="Labor cost %"
          description="Scheduled vs. actual hours and labor % of sales."
          reason="Awaiting Deputy reporting."
          icon={Percent}
        />
      ) : (
        <LockedTile />
      )}
      <AwaitingData
        label="Covers today"
        description="Today's covers, bookings, and no-shows."
        reason="Awaiting OpenTable reporting."
        icon={CalendarDays}
      />
      <AwaitingData
        label="Top sellers"
        description="Best-selling items and revenue mix."
        reason="Awaiting product-sales reporting."
        icon={Trophy}
      />
      <AwaitingData
        label="Food cost"
        description="Cost of goods, stock, and wastage."
        reason="Inventory not yet connected."
        icon={Utensils}
      />
    </div>
  );
}

function LockedTile() {
  return (
    <div className="rounded-lg border border-dashed bg-muted/40 p-4">
      <div className="flex items-center gap-2 text-sm font-medium">
        <Lock className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
        <span>Labor cost %</span>
      </div>
      <p className="mt-3 text-sm text-muted-foreground">Owner only.</p>
      <p className="mt-2 text-xs text-muted-foreground/70">
        Wage and labor-cost figures are restricted.
      </p>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun run test -- components/dashboard/business-kpi-grid.test.tsx`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`
Then:

```bash
git add frontend/components/dashboard/business-kpi-grid.tsx frontend/components/dashboard/business-kpi-grid.test.tsx
git commit -m "feat: add business KPI grid with owner-only labor tile"
```

---

## Task 4: Rewrite the dashboard page

**Files:**
- Modify: `frontend/app/(app)/dashboard/page.tsx`

**Interfaces:**
- Consumes: `NeedsDecisionBand` (Task 2), `BusinessKpiGrid` (Task 3), `useCurrentUser()` (existing), `useApiData` (existing), `getDashboardSummary()` (existing).

- [ ] **Step 1: Replace the page**

Replace the entire contents of `frontend/app/(app)/dashboard/page.tsx` with:

```tsx
"use client";

import { useApiData } from "@/lib/use-api-data";
import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { NeedsDecisionBand } from "@/components/dashboard/needs-decision-band";
import { BusinessKpiGrid } from "@/components/dashboard/business-kpi-grid";

export default function DashboardPage() {
  const { data, loading, error, reload } = useApiData((api) => api.getDashboardSummary());
  const { user } = useCurrentUser();

  if (loading) return <LoadingState rows={2} />;
  if (error) {
    if (error.code === "NOT_PERMITTED") return <PermissionDenied subject="dashboard data" />;
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
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">Your numbers, verified.</p>
      </div>

      <NeedsDecisionBand openCount={data.openConflicts} />

      <BusinessKpiGrid seniority={user?.seniority} />
    </div>
  );
}
```

- [ ] **Step 2: Remove the now-unused dashboard strip component**

The old `ConnectorHealthStrip` is only imported by the old dashboard; delete it:

```bash
git rm frontend/components/dashboard/connector-health-strip.tsx
```

- [ ] **Step 3: Verify build, typecheck, and lint**

Run: `cd frontend && bun run typecheck && bun run lint && bun run build`
Expected: all pass. (Build confirms no lingering imports of the removed strip or `ActivityChart`/`StatCard` from the dashboard.)

- [ ] **Step 4: Commit**

```bash
git add frontend/app/\(app\)/dashboard/page.tsx
git commit -m "feat: replace technical dashboard with business dashboard"
```

---

## Task 5: Data health page + `/connectors` redirect

**Files:**
- Create: `frontend/app/(app)/data-health/page.tsx`
- Modify: `frontend/next.config.ts` (add redirect)
- Delete: `frontend/app/(app)/connectors/page.tsx`, `frontend/app/(app)/connectors/layout.tsx`
- Test: `frontend/components/connectors/connector-status-badge.test.tsx`

**Interfaces:**
- Consumes: `ConnectorStatusBadge`, `StatCard`, `ActivityChart`, `useApiData`, `useApi` (all existing); `listConnectorStatuses()`, `getDashboardSummary()`, `getDashboardActivity()`, `runConnector()`, `uploadOpenTableCsv()` (existing API).

- [ ] **Step 1: Add the connector-status-badge test (pins Review Focus #4)**

`frontend/components/connectors/connector-status-badge.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ConnectorStatusBadge } from "./connector-status-badge";

describe("ConnectorStatusBadge", () => {
  it("distinguishes a failure from a never-run connector", () => {
    const { rerender } = render(<ConnectorStatusBadge status="failed" />);
    expect(screen.getByText("Failed")).toBeInTheDocument();
    rerender(<ConnectorStatusBadge status="never_run" />);
    expect(screen.getByText("Never run")).toBeInTheDocument();
    expect(screen.queryByText("Failed")).not.toBeInTheDocument();
  });

  it("labels the no-new-data state distinctly", () => {
    render(<ConnectorStatusBadge status="no_new_data" />);
    expect(screen.getByText("No new data")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it passes**

Run: `cd frontend && bun run test -- components/connectors/connector-status-badge.test.tsx`
Expected: PASS (existing component already satisfies it).

- [ ] **Step 3: Write the Data health page**

`frontend/app/(app)/data-health/page.tsx`:

```tsx
"use client";

import { useRef, useState } from "react";
import { Activity, Timer, Wrench } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { useApi } from "@/lib/demo-mode";
import { isApiError } from "@/lib/api";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import { StatCard } from "@/components/dashboard/stat-card";
import { ActivityChart } from "@/components/dashboard/activity-chart";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";

function SectionError({ message }: { message: string }) {
  return <p className="text-sm text-destructive">{message}</p>;
}

export default function DataHealthPage() {
  const api = useApi();
  const connectors = useApiData((api) => api.listConnectorStatuses());
  const summary = useApiData((api) => api.getDashboardSummary());
  const activity = useApiData((api) => api.getDashboardActivity());
  const [running, setRunning] = useState<string | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function run(source: string) {
    setRunning(source);
    setRunError(null);
    try {
      await api.runConnector(source);
      await connectors.reload();
    } catch (e) {
      setRunError(isApiError(e) ? e.message : "Something went wrong running the connector.");
    } finally {
      setRunning(null);
    }
  }

  async function uploadCsv(file: File) {
    setUploading(true);
    setUploadMessage(null);
    try {
      await api.uploadOpenTableCsv(file);
      setUploadMessage({ ok: true, text: "CSV uploaded and ingested." });
      await connectors.reload();
    } catch (e) {
      setUploadMessage({ ok: false, text: isApiError(e) ? e.message : "Upload failed." });
    } finally {
      setUploading(false);
    }
  }

  if (connectors.loading) return <LoadingState rows={4} />;
  if (connectors.error) {
    if (connectors.error.code === "NOT_PERMITTED")
      return <PermissionDenied subject="connector status" />;
    return (
      <ErrorState
        title="Couldn't load data health"
        message={connectors.error.message}
        correlationId={connectors.error.correlationId}
        onRetry={connectors.reload}
      />
    );
  }
  if (!connectors.data || connectors.data.length === 0) {
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
        <h1 className="text-xl font-semibold">Data health</h1>
        <p className="text-sm text-muted-foreground">
          Ingestion and connector status across your sources.
        </p>
      </div>

      {runError && <p className="text-sm text-destructive">{runError}</p>}
      {uploadMessage && (
        <p className={`text-sm ${uploadMessage.ok ? "text-emerald-600" : "text-destructive"}`}>
          {uploadMessage.text}
        </p>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {summary.loading ? (
          <Skeleton className="h-24 w-full" />
        ) : summary.error ? (
          <SectionError message="Couldn't load ingestion summary." />
        ) : (
          <>
            <StatCard
              label="Ingestion completeness"
              value={
                summary.data?.ingestionCompleteness == null
                  ? "—"
                  : `${summary.data.ingestionCompleteness}%`
              }
              hint="Share of scheduled connector runs that succeed"
              icon={Activity}
            />
            <StatCard
              label="Time to detect failure"
              value={summary.data?.timeToDetectFailure ?? "—"}
              hint="How quickly a failed run becomes visible"
              icon={Timer}
            />
            <StatCard
              label="Manual overrides"
              value={summary.data?.overrideUsage ? String(summary.data.overrideUsage.count) : "—"}
              hint={summary.data?.overrideUsage?.period ?? "How often staff resolve by hand"}
              icon={Wrench}
            />
          </>
        )}
      </section>

      <section className="rounded-lg border">
        {connectors.data.map((c, i) => (
          <div
            key={c.source}
            className={`flex items-center justify-between gap-4 p-4 ${i > 0 ? "border-t" : ""}`}
          >
            <div>
              <p className="text-sm font-medium">{c.source}</p>
              <p className="text-xs text-muted-foreground">
                {c.connectorName}
                {c.lastRunAt
                  ? ` · last run ${new Date(c.lastRunAt).toLocaleString()}`
                  : " · never run"}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <ConnectorStatusBadge status={c.status} />
              {c.source.toLowerCase() === "opentable" ? (
                <>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".csv,text/csv"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) uploadCsv(file);
                      e.target.value = "";
                    }}
                  />
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploading}
                  >
                    {uploading ? "Uploading…" : "Upload CSV"}
                  </Button>
                </>
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => run(c.source)}
                  disabled={running !== null}
                >
                  {running === c.source ? "Running…" : "Run now"}
                </Button>
              )}
            </div>
          </div>
        ))}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Run activity · last 14 days</h2>
        {activity.loading ? (
          <Skeleton className="h-40 w-full" />
        ) : activity.error ? (
          <SectionError message="Couldn't load activity." />
        ) : (
          <ActivityChart points={activity.data ?? []} />
        )}
      </section>
    </div>
  );
}
```

- [ ] **Step 4: Add the redirect**

In `frontend/next.config.ts`, change the `nextConfig` object to include a `redirects` function. Replace:

```ts
const nextConfig: NextConfig = {
  reactStrictMode: true,
```

with:

```ts
const nextConfig: NextConfig = {
  reactStrictMode: true,
  // The diagnostics moved from /connectors to /data-health; keep old links working.
  async redirects() {
    return [{ source: "/connectors", destination: "/data-health", permanent: true }];
  },
```

- [ ] **Step 5: Remove the old connectors route**

```bash
git rm "frontend/app/(app)/connectors/page.tsx" "frontend/app/(app)/connectors/layout.tsx"
```

- [ ] **Step 6: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint && bun run build`
Expected: all pass.

```bash
git add frontend/app/\(app\)/data-health/page.tsx frontend/next.config.ts frontend/components/connectors/connector-status-badge.test.tsx
git commit -m "feat: add data-health page and redirect connectors"
```

---

## Task 6: Five business sub-page placeholders

**Files:**
- Create: `frontend/app/(app)/sales/page.tsx`
- Create: `frontend/app/(app)/staff/page.tsx`
- Create: `frontend/app/(app)/reservations/page.tsx`
- Create: `frontend/app/(app)/kitchen/page.tsx`
- Create: `frontend/app/(app)/recipes/page.tsx`

**Interfaces:**
- Consumes: `AwaitingData` (Task 1).

- [ ] **Step 1: Write the Sales page**

`frontend/app/(app)/sales/page.tsx`:

```tsx
import type { Metadata } from "next";
import { TrendingUp } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Sales" };

export default function SalesPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Sales</h1>
        <p className="text-sm text-muted-foreground">Net sales and product mix.</p>
      </div>
      <AwaitingData
        label="Sales reporting"
        description="Net sales today and this week vs. last week, with product mix."
        reason="Awaiting sales-reporting endpoint."
        icon={TrendingUp}
      />
    </div>
  );
}
```

- [ ] **Step 2: Write the Staff & Labor page**

`frontend/app/(app)/staff/page.tsx`:

```tsx
import type { Metadata } from "next";
import { Users } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Staff & Labor" };

export default function StaffPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Staff &amp; Labor</h1>
        <p className="text-sm text-muted-foreground">Roster, hours, and labor cost.</p>
      </div>
      <AwaitingData
        label="Rostering and labor"
        description="Scheduled vs. actual hours and labor % of sales."
        reason="Awaiting Deputy reporting."
        icon={Users}
      />
    </div>
  );
}
```

- [ ] **Step 3: Write the Reservations page**

`frontend/app/(app)/reservations/page.tsx`:

```tsx
import type { Metadata } from "next";
import { CalendarDays } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Reservations" };

export default function ReservationsPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Reservations</h1>
        <p className="text-sm text-muted-foreground">Covers, bookings, and no-shows.</p>
      </div>
      <AwaitingData
        label="Reservations"
        description="Today's covers, bookings, no-shows, and walk-ins."
        reason="Awaiting OpenTable reporting."
        icon={CalendarDays}
      />
    </div>
  );
}
```

- [ ] **Step 4: Write the Kitchen page**

`frontend/app/(app)/kitchen/page.tsx`:

```tsx
import type { Metadata } from "next";
import { ChefHat } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Kitchen" };

export default function KitchenPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Kitchen</h1>
        <p className="text-sm text-muted-foreground">Food cost, stock, and wastage.</p>
      </div>
      <AwaitingData
        label="Food cost"
        description="Cost of goods, stock levels, stocktakes, and wastage."
        reason="Inventory not yet connected."
        icon={ChefHat}
      />
    </div>
  );
}
```

- [ ] **Step 5: Write the Recipes page**

`frontend/app/(app)/recipes/page.tsx`:

```tsx
import type { Metadata } from "next";
import { BookOpen } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Recipes" };

export default function RecipesPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Recipes</h1>
        <p className="text-sm text-muted-foreground">Recipe list and ingredient costing.</p>
      </div>
      <AwaitingData
        label="Recipes"
        description="Recipe list, per-dish ingredient cost, and gross margin."
        reason="Recipes not yet connected."
        icon={BookOpen}
      />
    </div>
  );
}
```

- [ ] **Step 6: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint && bun run build`
Expected: all pass.

```bash
git add frontend/app/\(app\)/sales/page.tsx frontend/app/\(app\)/staff/page.tsx frontend/app/\(app\)/reservations/page.tsx frontend/app/\(app\)/kitchen/page.tsx frontend/app/\(app\)/recipes/page.tsx
git commit -m "feat: add sales, staff, reservations, kitchen, recipes placeholder pages"
```

---

## Task 7: Regroup the sidebar

**Files:**
- Modify: `frontend/components/app-shell/app-sidebar.tsx`

**Interfaces:**
- Consumes: existing `Sidebar*` primitives, `UserMenu`. No new interfaces; routes from Tasks 4–6 now exist.

- [ ] **Step 1: Replace the sidebar**

Replace the entire contents of `frontend/components/app-shell/app-sidebar.tsx` with:

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import {
  Activity,
  BookOpen,
  CalendarDays,
  ChefHat,
  LayoutDashboard,
  Scale,
  Settings,
  TrendingUp,
  Users,
} from "lucide-react";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import { UserMenu } from "./user-menu";

interface NavItem {
  title: string;
  href: string;
  icon: LucideIcon;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const navGroups: NavGroup[] = [
  {
    label: "Business",
    items: [
      { title: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
      { title: "Sales", href: "/sales", icon: TrendingUp },
      { title: "Staff & Labor", href: "/staff", icon: Users },
      { title: "Reservations", href: "/reservations", icon: CalendarDays },
      { title: "Kitchen", href: "/kitchen", icon: ChefHat },
      { title: "Recipes", href: "/recipes", icon: BookOpen },
    ],
  },
  {
    label: "Data",
    items: [
      { title: "Reconciliation", href: "/reconciliation", icon: Scale },
      { title: "Data health", href: "/data-health", icon: Activity },
    ],
  },
];

export function AppSidebar() {
  const pathname = usePathname();

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <Link href="/dashboard" aria-label="Goldy's Data Platform">
                <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                  <span className="text-sm font-bold">G</span>
                </div>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-semibold">Goldy&apos;s</span>
                  <span className="truncate text-xs text-muted-foreground">Data Platform</span>
                </div>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        {navGroups.map((group) => (
          <SidebarGroup key={group.label}>
            <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map((item) => {
                  const active =
                    pathname === item.href || pathname.startsWith(`${item.href}/`);
                  return (
                    <SidebarMenuItem key={item.href}>
                      <SidebarMenuButton asChild isActive={active} tooltip={item.title}>
                        <Link href={item.href}>
                          <item.icon />
                          <span>{item.title}</span>
                        </Link>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>

      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              asChild
              isActive={pathname === "/settings"}
              tooltip="Settings"
            >
              <Link href="/settings">
                <Settings />
                <span>Settings</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <UserMenu />
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  );
}
```

- [ ] **Step 2: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint && bun run build`
Expected: all pass.

```bash
git add frontend/components/app-shell/app-sidebar.tsx
git commit -m "feat: regroup sidebar into Business / Data / Settings navigation"
```

---

## Task 8: Update the design-system doc

**Files:**
- Modify: `docs/design-system.md`

**Interfaces:**
- None. Documentation only.

- [ ] **Step 1: Replace the screen-inventory table (§3)**

Replace the table body of §3 "Screen inventory" (the seven rows under the header row) with:

```markdown
| Screen | PRD ref | Notes |
|---|---|---|
| Dashboard / business KPI overview | Goals 1–2, Success Metrics | Trust-first: a live "Needs a decision" band above a five-tile business KPI grid. Business areas render as `AwaitingData` placeholders until reporting endpoints land; no ingestion/connector diagnostics on this screen |
| Sales | Goals 1–2 | Placeholder; net sales trend, vs-prior-period, product mix |
| Staff & Labor | Requirement 3 | Placeholder; roster, scheduled vs actual hours, labor % (wage figures Owner-only) |
| Reservations | Goals 1–2 | Placeholder; covers, bookings, no-shows |
| Kitchen | — | Forward-looking placeholder; food cost, stock, wastage (inventory not ingested) |
| Recipes | — | Forward-looking placeholder; recipe list + ingredient costing (not ingested) |
| Reconciliation view | Requirement 5 | Exception-first summary + drill-in comparison. See §6 |
| Data health / ingestion health | Requirement 6 | Connector status, ingestion completeness, run activity; failure states visually distinct from "no new data" |
| Permission-denied state | Requirement 3 | Explicit "not permitted" — never a silently filtered or partial-looking view |
| Role/permission admin (future) | Requirement 3 | Not scheduled yet; deferred design |
| Conversational BI widgets | Requirement 9 (Phase 2) | Deferred — depends on the JSON widget schema landing first |
| Smart Exporter | Requirement 10 (Phase 2) | Deferred — reuses Conversational BI's rendering, no separate design needed yet |
```

- [ ] **Step 2: Update §4 navigation**

Replace the paragraph in §4 that reads:

```markdown
The cleared rebuild baseline retains shadcn configuration but no application
shell or components. When the Phase 1 shell is implemented, use shadcn's
`sidebar-07` block as the starting point and adapt it to a flat Goldy's
navigation: Dashboard, Reconciliation, Connectors, and Settings.
```

with:

```markdown
The cleared rebuild baseline retains shadcn configuration but no application
shell or components. When the Phase 1 shell is implemented, use shadcn's
`sidebar-07` block as the starting point and adapt it to Goldy's grouped
navigation: a **Business** group (Dashboard, Sales, Staff & Labor,
Reservations, Kitchen, Recipes), a **Data** group (Reconciliation, Data
health), and **Settings** in the footer. Diagnostics live under Data health,
never on the Dashboard.
```

- [ ] **Step 3: Add the `AwaitingData` pattern to §6**

Append to §6 "Core interaction patterns" (after the connector-failure bullet):

```markdown
- **AwaitingData (forward-looking placeholder):** a surface not yet wired to
  data renders its label, a one-line "what you'll see here" description, and a
  muted reason line (e.g. "Awaiting Deputy reporting") — never fabricated
  numbers. Distinct from an empty state, which means "the data exists but is
  empty right now".
```

- [ ] **Step 4: Commit**

```bash
git add docs/design-system.md
git commit -m "docs: update design-system screen inventory and navigation"
```

---

## Task 9: Full verification pass

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full frontend gate**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build`
Expected: all pass; `vitest run` reports the new tests plus the retained `activity-chart.test.tsx` and `reconciliation-logic.test.ts` green.

- [ ] **Step 2: Manual smoke check (demo mode)**

Run `cd frontend && bun run dev`, open `http://localhost:3000/dashboard`, and confirm:
- The hero shows "3 numbers need a decision" (demo data) with a working "Review in Reconciliation" link.
- The five KPI tiles render as placeholders; switching identity to a non-Owner in the demo shows the Labor tile locked.
- `http://localhost:3000/connectors` redirects to `/data-health` and shows connector status + run activity.
- The sidebar shows Business / Data groups with Settings in the footer.

- [ ] **Step 3: Commit any fixes**

If the smoke check surfaced a fix, commit it atomically with a `fix:` message; otherwise there is nothing to commit.
