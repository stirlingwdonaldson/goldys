# Resolution Rules UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the "Resolution rules" page (PRD Requirement 8's UI) — a rule list with unresolved badges, a rule editor, a recompute-status banner, and a rule audit — as a UI-first slice against the typed `Api` contract with demo fixtures, deferring the live backend.

**Architecture:** Follows the existing frontend pattern exactly: a typed `Api` contract (`lib/api/types.ts`) with two implementations — `demoApi` (in-memory mutable fixtures) and `liveApi` (endpoint stubs that are not yet implemented). New pure rule-derivation logic lives in `lib/rule-logic.ts`; new presentational components live in `components/rules/`; the page is a client component under `app/(app)/resolution-rules/`. No backend changes.

**Tech Stack:** Next.js 15 (App Router), React 19, TypeScript, Tailwind, shadcn/ui, lucide-react, Vitest + @testing-library/react (jsdom), Bun 1.4.2.

**Spec:** `docs/superpowers/specs/2026-09-30-phase-two-ui-design.md` (§5 Resolution rules)

## Global Constraints

- Bun only — run everything with `bun run <script>`, never npm/yarn.
- shadcn defaults unmodified (slate); semantic status colors (`status-success`, `status-warning`, `status-missing`); `destructive` for genuine failures only. A rule "unresolved" field is an expected state, not an error.
- Never present fabricated operational data as real — demo fixtures are clearly demo (the existing "Demo data" banner already covers this).
- Permission model is department × seniority; denied = explicit. For this UI-first slice, the field picker lists all fields (the field-to-role mapping is a PRD open question and a backend concern) — do not hard-code a permission matrix.
- Verification gates for every task: `bun run typecheck`, `bun run lint`, `bun run build`; where a task adds tests, `bun run test`.
- Atomic Conventional Commits. Never commit to main. Branch from the frontend-IA work so the sidebar already has the Data group: `git checkout feature/frontend-management-ia && git checkout -b feature/resolution-rules-ui` (if that branch is unavailable, merge it first — this plan assumes the Business/Data sidebar exists).

## Review Focus

These are the input classes / failure modes the spec implies but whose happy-path tests would not otherwise exercise. Each is pinned by a test in the named task.

1. **A field with no rule** — must render an explicit `unresolved` badge, never be silently omitted or shown as an empty row. → Task 4.
2. **A `custom` rule with each declarative value** — "flag" must render distinctly from "highest"/"lowest"/"newest" (not collapse into one label). → Task 1.
3. **A `manual` rule** — must render "Manual override — always ask", never a priority or a pick value. → Task 1.
4. **Recompute in a non-`complete` state** — `recomputing` and `failed` must be visually distinct from `complete` (a manager must not mistake a mid-recompute state for done). → Task 3.
5. **Editing a rule must not mutate the original in place** — the editor starts from a copy; saving produces a new/updated rule; canceling leaves the list unchanged. → Task 5.

---

## Task 1: Rule-derivation logic

**Files:**
- Create: `frontend/lib/rule-logic.ts`
- Test: `frontend/lib/rule-logic.test.ts`

**Interfaces:**
- Consumes: `ResolutionRule`, `CustomLogic`, `RuleStrategy` types (defined in Task 2; for this task, declare them inline in `rule-logic.ts` and re-export — Task 2 will move them to `types.ts` and this file imports from there).
- Produces: `summarizeRule(rule): string`, `ruleDetail(rule): string`, `buildRuleRows(rules, knownFields): RuleRow[]`. Used by Tasks 4 and 5.

- [ ] **Step 1: Write the failing test**

`frontend/lib/rule-logic.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { summarizeRule, ruleDetail, buildRuleRows } from "./rule-logic";
import type { ResolutionRule } from "./rule-logic";

function rule(overrides: Partial<ResolutionRule> = {}): ResolutionRule {
  return {
    id: "rule-1",
    entity: "Sales",
    field: "quantity_sold",
    strategy: "priority",
    sourcePriority: ["Cooking the Books", "Lightspeed"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
    ...overrides,
  };
}

describe("summarizeRule", () => {
  it("summarizes a priority rule as '<first source> wins'", () => {
    expect(summarizeRule(rule())).toBe("Cooking the Books wins");
  });

  it("summarizes a manual rule distinctly", () => {
    expect(summarizeRule(rule({ strategy: "manual", sourcePriority: undefined }))).toBe(
      "Manual override — always ask",
    );
  });

  it("maps every custom logic value to a distinct label", () => {
    const base = { strategy: "custom" as const, sourcePriority: undefined };
    expect(summarizeRule(rule({ ...base, customLogic: "flag" }))).toBe("Flag unresolved");
    expect(summarizeRule(rule({ ...base, customLogic: "highest" }))).toBe("Pick highest");
    expect(summarizeRule(rule({ ...base, customLogic: "lowest" }))).toBe("Pick lowest");
    expect(summarizeRule(rule({ ...base, customLogic: "newest" }))).toBe("Pick newest");
  });
});

describe("ruleDetail", () => {
  it("shows the full priority order for a priority rule", () => {
    expect(ruleDetail(rule())).toBe("priority: Cooking the Books > Lightspeed");
  });

  it("returns an empty string for non-priority rules", () => {
    expect(ruleDetail(rule({ strategy: "manual", sourcePriority: undefined }))).toBe("");
  });
});

describe("buildRuleRows", () => {
  it("emits one row per known field, marking unruly fields unresolved", () => {
    const rules = [rule()]; // only Sales.quantity_sold has a rule
    const known = { Sales: ["quantity_sold", "net_amount"] };
    const rows = buildRuleRows(rules, known);
    expect(rows).toHaveLength(2);
    const resolved = rows.find((r) => r.field === "quantity_sold");
    const unresolved = rows.find((r) => r.field === "net_amount");
    expect(resolved?.rule).not.toBeNull();
    expect(unresolved?.rule).toBeNull();
  });

  it("groups rows by entity and sorts fields alphabetically", () => {
    const rules = [rule()];
    const known = { Sales: ["net_amount", "quantity_sold"], Shifts: ["hours_worked"] };
    const rows = buildRuleRows(rules, known);
    expect(rows.map((r) => `${r.entity}:${r.field}`)).toEqual([
      "Sales:net_amount",
      "Sales:quantity_sold",
      "Shifts:hours_worked",
    ]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- lib/rule-logic.test.ts`
Expected: FAIL — cannot find module `./rule-logic`.

- [ ] **Step 3: Write the logic**

`frontend/lib/rule-logic.ts`:

```ts
export type RuleStrategy = "priority" | "manual" | "custom";
export type CustomLogic = "flag" | "highest" | "lowest" | "newest";

export interface ResolutionRule {
  id: string;
  entity: string;
  field: string;
  strategy: RuleStrategy;
  sourcePriority?: string[];
  customLogic?: CustomLogic;
  updatedAt: string;
  updatedBy: string;
}

const CUSTOM_LABEL: Record<CustomLogic, string> = {
  flag: "Flag unresolved",
  highest: "Pick highest",
  lowest: "Pick lowest",
  newest: "Pick newest",
};

/** One-line effect of a rule, shown in the rule list. */
export function summarizeRule(rule: ResolutionRule): string {
  if (rule.strategy === "priority") return `${rule.sourcePriority?.[0] ?? "First source"} wins`;
  if (rule.strategy === "manual") return "Manual override — always ask";
  return rule.customLogic ? CUSTOM_LABEL[rule.customLogic] : "Flag unresolved";
}

/** Secondary line for a rule (e.g. the full source order); empty when none applies. */
export function ruleDetail(rule: ResolutionRule): string {
  if (rule.strategy === "priority" && rule.sourcePriority && rule.sourcePriority.length > 0) {
    return `priority: ${rule.sourcePriority.join(" > ")}`;
  }
  return "";
}

export interface RuleRow {
  entity: string;
  field: string;
  rule: ResolutionRule | null; // null => unresolved (no rule)
}

/**
 * One row per known field, in entity/field order. A field present in `knownFields`
 * but absent from `rules` yields a `rule: null` (unresolved) row — absence is
 * surfaced, never omitted.
 */
export function buildRuleRows(
  rules: ResolutionRule[],
  knownFields: Record<string, string[]>,
): RuleRow[] {
  const byKey = new Map(rules.map((r) => [`${r.entity}:${r.field}`, r]));
  const rows: RuleRow[] = [];
  for (const entity of Object.keys(knownFields).sort()) {
    for (const field of [...knownFields[entity]].sort()) {
      rows.push({ entity, field, rule: byKey.get(`${entity}:${field}`) ?? null });
    }
  }
  return rows;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun run test -- lib/rule-logic.test.ts`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`
Then:

```bash
git add frontend/lib/rule-logic.ts frontend/lib/rule-logic.test.ts
git commit -m "feat: add resolution-rule derivation logic"
```

---

## Task 2: API contract + demo fixtures

**Files:**
- Modify: `frontend/lib/api/types.ts` (add types + `Api` methods; re-export the rule types from `rule-logic` via a local `import type`)
- Modify: `frontend/lib/api/demo.ts` (fixtures + methods)
- Modify: `frontend/lib/api/live.ts` (endpoint stubs)

**Interfaces:**
- Consumes: `ResolutionRule`, `CustomLogic` from `lib/rule-logic.ts` (Task 1).
- Produces (on `Api`): `listResolutionRules()`, `saveResolutionRule(input)`, `deleteResolutionRule(id)`, `getRecomputeStatus()`, `listRuleAudit()`. Used by the page (Task 7).

- [ ] **Step 1: Extend types.ts**

Add to `frontend/lib/api/types.ts` (import the rule types and add new interfaces + `Api` methods):

```ts
import type { CustomLogic, ResolutionRule, RuleStrategy } from "@/lib/rule-logic";

export type { CustomLogic, ResolutionRule, RuleStrategy };

export interface SaveResolutionRuleInput {
  id?: string; // present when editing an existing rule
  entity: string;
  field: string;
  strategy: RuleStrategy;
  sourcePriority?: string[];
  customLogic?: CustomLogic;
}

export type RecomputeState = "idle" | "recomputing" | "complete" | "failed";

export interface RecomputeStatus {
  state: RecomputeState;
  lastCompletedAt: string | null;
}

export interface RuleAuditEntry {
  id: string;
  ruleId: string | null; // null => the rule was deleted
  field: string;
  change: "created" | "updated" | "deleted";
  at: string;
  by: string;
}
```

And add these five methods to the `Api` interface (before the closing brace):

```ts
  listResolutionRules(): Promise<ResolutionRule[]>;
  saveResolutionRule(input: SaveResolutionRuleInput): Promise<ResolutionRule>;
  deleteResolutionRule(id: string): Promise<void>;
  getRecomputeStatus(): Promise<RecomputeStatus>;
  listRuleAudit(): Promise<RuleAuditEntry[]>;
```

- [ ] **Step 2: Extend demo.ts**

Add mutable fixtures near the other fixtures in `frontend/lib/api/demo.ts`, and implement the five methods on `demoApi` (import the new types at the top):

```ts
const KNOWN_FIELDS: Record<string, string[]> = {
  Sales: ["quantity_sold", "net_amount", "gross_sales"],
  Shifts: ["hours_worked"],
  Products: ["quantity_sold", "unit_price"],
};

let rules: ResolutionRule[] = [
  {
    id: "rule-1",
    entity: "Sales",
    field: "quantity_sold",
    strategy: "priority",
    sourcePriority: ["Cooking the Books", "Lightspeed"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
  },
  {
    id: "rule-2",
    entity: "Shifts",
    field: "hours_worked",
    strategy: "priority",
    sourcePriority: ["Deputy", "Lightspeed"],
    updatedAt: "2026-09-28T09:30:00Z",
    updatedBy: "Stirling Donaldson",
  },
  {
    id: "rule-3",
    entity: "Sales",
    field: "gross_sales",
    strategy: "manual",
    updatedAt: "2026-09-27T16:00:00Z",
    updatedBy: "Stirling Donaldson",
  },
];

let recomputeStatus: RecomputeStatus = {
  state: "complete",
  lastCompletedAt: "2026-09-30T08:00:00Z",
};

let ruleAudit: RuleAuditEntry[] = [
  {
    id: "audit-1",
    ruleId: "rule-1",
    field: "quantity_sold",
    change: "created",
    at: "2026-09-29T18:00:00Z",
    by: "Stirling Donaldson",
  },
];

// In the demoApi object, add:
  async listResolutionRules(): Promise<ResolutionRule[]> {
    await delay(400);
    return [...rules];
  },

  async saveResolutionRule(input: SaveResolutionRuleInput): Promise<ResolutionRule> {
    await delay(500);
    const now = new Date().toISOString();
    if (input.id) {
      const existing = rules.find((r) => r.id === input.id);
      if (!existing) throw new ApiError("VALIDATION_FAILED", `No rule with id ${input.id}.`);
      Object.assign(existing, {
        strategy: input.strategy,
        sourcePriority: input.sourcePriority,
        customLogic: input.customLogic,
        updatedAt: now,
      });
      ruleAudit = [
        { id: `audit-${Date.now()}`, ruleId: existing.id, field: existing.field, change: "updated", at: now, by: "You" },
        ...ruleAudit,
      ];
      recomputeStatus = { state: "complete", lastCompletedAt: now };
      return { ...existing };
    }
    const created: ResolutionRule = {
      id: `rule-${Date.now()}`,
      entity: input.entity,
      field: input.field,
      strategy: input.strategy,
      sourcePriority: input.sourcePriority,
      customLogic: input.customLogic,
      updatedAt: now,
      updatedBy: "You",
    };
    rules = [...rules, created];
    ruleAudit = [
      { id: `audit-${Date.now()}`, ruleId: created.id, field: created.field, change: "created", at: now, by: "You" },
      ...ruleAudit,
    ];
    recomputeStatus = { state: "complete", lastCompletedAt: now };
    return created;
  },

  async deleteResolutionRule(id: string): Promise<void> {
    await delay(400);
    const existing = rules.find((r) => r.id === id);
    if (!existing) throw new ApiError("VALIDATION_FAILED", `No rule with id ${id}.`);
    rules = rules.filter((r) => r.id !== id);
    ruleAudit = [
      { id: `audit-${Date.now()}`, ruleId: null, field: existing.field, change: "deleted", at: new Date().toISOString(), by: "You" },
      ...ruleAudit,
    ];
  },

  async getRecomputeStatus(): Promise<RecomputeStatus> {
    await delay(300);
    return { ...recomputeStatus };
  },

  async listRuleAudit(): Promise<RuleAuditEntry[]> {
    await delay(300);
    return [...ruleAudit];
  },
```

Also export `KNOWN_FIELDS` from `demo.ts` so the page can compute unresolved rows:

```ts
export function getKnownFields(): Record<string, string[]> {
  return { ...KNOWN_FIELDS };
}
```

- [ ] **Step 3: Extend live.ts**

Add to `frontend/lib/api/live.ts` (import the new types; endpoints are not yet implemented on the backend — this matches the existing "live endpoints, not yet implemented" convention):

```ts
  listResolutionRules: () => fetchApi<ResolutionRule[]>("/api/reconciliation/rules"),
  saveResolutionRule: (input: SaveResolutionRuleInput) =>
    fetchApi<ResolutionRule>("/api/reconciliation/rules", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }),
  deleteResolutionRule: (id: string) =>
    fetchApi<void>(`/api/reconciliation/rules/${id}`, { method: "DELETE" }),
  getRecomputeStatus: () => fetchApi<RecomputeStatus>("/api/reconciliation/recompute/status"),
  listRuleAudit: () => fetchApi<RuleAuditEntry[]>("/api/reconciliation/rules/audit"),
```

- [ ] **Step 4: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint && bun run build`
Expected: all pass (both `demoApi` and `liveApi` now satisfy the extended `Api` interface).

```bash
git add frontend/lib/api/types.ts frontend/lib/api/demo.ts frontend/lib/api/live.ts
git commit -m "feat: add resolution-rules api contract and demo fixtures"
```

---

## Task 3: Recompute banner component

**Files:**
- Create: `frontend/components/rules/recompute-banner.tsx`
- Test: `frontend/components/rules/recompute-banner.test.tsx`

**Interfaces:**
- Consumes: `RecomputeStatus`, `RecomputeState` from `lib/api`.
- Produces: `RecomputeBanner({ status, onRetry? })`. Used by the page (Task 7).

- [ ] **Step 1: Write the failing test**

`frontend/components/rules/recompute-banner.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RecomputeBanner } from "./recompute-banner";
import type { RecomputeStatus } from "@/lib/api";

function status(state: RecomputeStatus["state"], lastCompletedAt: string | null = null): RecomputeStatus {
  return { state, lastCompletedAt };
}

describe("RecomputeBanner", () => {
  it("shows the last-completed time when complete", () => {
    render(<RecomputeBanner status={status("complete", "2026-09-30T08:00:00Z")} />);
    expect(screen.getByText(/recomputed/i)).toBeInTheDocument();
    expect(screen.getByText(/sep 30/i)).toBeInTheDocument();
  });

  it("warns while recomputing", () => {
    render(<RecomputeBanner status={status("recomputing")} />);
    expect(screen.getByText(/recomputing/i)).toBeInTheDocument();
  });

  it("treats failed distinctly from complete", () => {
    render(<RecomputeBanner status={status("failed")} />);
    expect(screen.getByText(/failed/i)).toBeInTheDocument();
  });

  it("renders an idle note when never completed", () => {
    render(<RecomputeBanner status={status("idle")} />);
    expect(screen.getByText(/never/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- components/rules/recompute-banner.test.tsx`
Expected: FAIL — cannot find module `./recompute-banner`.

- [ ] **Step 3: Write the component**

`frontend/components/rules/recompute-banner.tsx`:

```tsx
import { AlertCircle, CheckCircle2, RefreshCw } from "lucide-react";
import type { RecomputeStatus } from "@/lib/api";

interface RecomputeBannerProps {
  status: RecomputeStatus;
  onRetry?: () => void;
}

/** The recompute-state banner: a manager must not read a partially-recomputed view. */
export function RecomputeBanner({ status }: RecomputeBannerProps) {
  if (status.state === "recomputing") {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-transparent bg-status-warning/15 p-3 text-sm">
        <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />
        <span>Rules changed — recomputing resolved views…</span>
      </div>
    );
  }

  if (status.state === "failed") {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-transparent bg-destructive/10 p-3 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" aria-hidden="true" />
        <span>Recompute failed. Resolved views may be stale.</span>
      </div>
    );
  }

  if (status.state === "complete" && status.lastCompletedAt) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-transparent bg-status-success/10 p-3 text-sm">
        <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
        <span>
          Last recomputed {new Date(status.lastCompletedAt).toLocaleString()}
        </span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 rounded-lg border p-3 text-sm text-muted-foreground">
      <RefreshCw className="h-4 w-4" aria-hidden="true" />
      <span>Resolved views have never been recomputed.</span>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun run test -- components/rules/recompute-banner.test.tsx`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`
Then:

```bash
git add frontend/components/rules/recompute-banner.tsx frontend/components/rules/recompute-banner.test.tsx
git commit -m "feat: add recompute-status banner"
```

---

## Task 4: Rule list component

**Files:**
- Create: `frontend/components/rules/rule-list.tsx`
- Test: `frontend/components/rules/rule-list.test.tsx`

**Interfaces:**
- Consumes: `ResolutionRule`, `RuleRow` via `buildRuleRows` (Task 1).
- Produces: `RuleList({ rows, onEdit, onDelete, onNew })`. Used by the page (Task 7).

- [ ] **Step 1: Write the failing test**

`frontend/components/rules/rule-list.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { RuleList } from "./rule-list";
import type { RuleRow } from "@/lib/rule-logic";

function row(entity: string, field: string, rule: RuleRow["rule"]): RuleRow {
  return { entity, field, rule };
}

const rows: RuleRow[] = [
  row("Sales", "net_amount", null),
  row("Sales", "quantity_sold", {
    id: "rule-1",
    entity: "Sales",
    field: "quantity_sold",
    strategy: "priority",
    sourcePriority: ["Cooking the Books", "Lightspeed"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
  }),
];

describe("RuleList", () => {
  it("renders each field with its effect or an unresolved badge", () => {
    render(<RuleList rows={rows} onEdit={() => {}} onDelete={() => {}} onNew={() => {}} />);
    expect(screen.getByText("Cooking the Books wins")).toBeInTheDocument();
    expect(screen.getByText("net_amount")).toBeInTheDocument();
    expect(screen.getByText(/unresolved/i)).toBeInTheDocument();
  });

  it("calls onEdit with the rule id when an edit action is clicked", () => {
    const onEdit = vi.fn();
    render(<RuleList rows={rows} onEdit={onEdit} onDelete={() => {}} onNew={() => {}} />);
    screen.getByRole("button", { name: /edit quantity_sold/i }).click();
    expect(onEdit).toHaveBeenCalledWith("rule-1");
  });

  it("renders the new-rule button", () => {
    const onNew = vi.fn();
    render(<RuleList rows={rows} onEdit={() => {}} onDelete={() => {}} onNew={onNew} />);
    screen.getByRole("button", { name: /new rule/i }).click();
    expect(onNew).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- components/rules/rule-list.test.tsx`
Expected: FAIL — cannot find module `./rule-list`.

- [ ] **Step 3: Write the component**

`frontend/components/rules/rule-list.tsx`:

```tsx
import { Pencil, Plus, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ruleDetail, summarizeRule, type RuleRow } from "@/lib/rule-logic";

interface RuleListProps {
  rows: RuleRow[];
  onEdit: (id: string) => void;
  onDelete: (id: string) => void;
  onNew: () => void;
}

/** The grouped rule list: one row per known field, unresolved fields flagged. */
export function RuleList({ rows, onEdit, onDelete, onNew }: RuleListProps) {
  let lastEntity: string | null = null;
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-muted-foreground">Rules by entity</h2>
        <Button size="sm" onClick={onNew}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          New rule
        </Button>
      </div>

      <div className="rounded-lg border">
        {rows.map((r, i) => {
          const header = r.entity !== lastEntity ? r.entity : null;
          lastEntity = r.entity;
          return (
            <div key={`${r.entity}:${r.field}`}>
              {header ? (
                <div className="border-b bg-muted/40 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {header}
                </div>
              ) : null}
              <div
                className={`flex items-center justify-between gap-4 p-3 ${i > 0 && !header ? "border-t" : ""}`}
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium">{r.field}</p>
                  {r.rule ? (
                    <p className="truncate text-xs text-muted-foreground">
                      {summarizeRule(r.rule)}
                      {ruleDetail(r.rule) ? ` · ${ruleDetail(r.rule)}` : ""}
                    </p>
                  ) : (
                    <p className="text-xs text-muted-foreground">No rule set</p>
                  )}
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {r.rule ? (
                    <>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Edit ${r.field}`}
                        onClick={() => onEdit(r.rule!.id)}
                      >
                        <Pencil className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Delete ${r.field}`}
                        onClick={() => onDelete(r.rule!.id)}
                      >
                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                      </Button>
                    </>
                  ) : (
                    <Badge variant="outline" className="border-transparent bg-status-warning/15 text-status-warning-foreground">
                      Unresolved
                    </Badge>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun run test -- components/rules/rule-list.test.tsx`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`
Then:

```bash
git add frontend/components/rules/rule-list.tsx frontend/components/rules/rule-list.test.tsx
git commit -m "feat: add resolution-rule list"
```

---

## Task 5: Rule editor (dialog)

**Files:**
- Add shadcn primitives: `dialog`, `select`, `radio-group`, `checkbox`, `label`
- Create: `frontend/components/rules/rule-editor.tsx`
- Test: `frontend/components/rules/rule-editor.test.tsx`

**Interfaces:**
- Consumes: `ResolutionRule`, `SaveResolutionRuleInput`, `RuleStrategy`, `CustomLogic` (Task 2); `summarizeRule` (Task 1); shadcn `Dialog`, `Select`, `RadioGroup`, `Checkbox`, `Label`.
- Produces: `RuleEditor({ open, initial, entities, fieldsByEntity, onSave, onCancel })`. Used by the page (Task 7).

- [ ] **Step 1: Add shadcn primitives**

Run: `cd frontend && bunx shadcn@latest add dialog select radio-group checkbox label`
Expected: adds `components/ui/dialog.tsx`, `select.tsx`, `radio-group.tsx`, `checkbox.tsx`, `label.tsx`; no errors.

- [ ] **Step 2: Write the failing test**

`frontend/components/rules/rule-editor.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { RuleEditor } from "./rule-editor";
import type { ResolutionRule } from "@/lib/api";

const existing: ResolutionRule = {
  id: "rule-1",
  entity: "Sales",
  field: "quantity_sold",
  strategy: "priority",
  sourcePriority: ["Cooking the Books", "Lightspeed"],
  updatedAt: "2026-09-29T18:00:00Z",
  updatedBy: "Stirling Donaldson",
};

describe("RuleEditor", () => {
  it("does not mutate the initial rule when the user cancels", () => {
    const onCancel = vi.fn();
    render(
      <RuleEditor
        open
        onCancel={onCancel}
        onSave={() => {}}
        entities={["Sales"]}
        fieldsByEntity={{ Sales: ["quantity_sold", "net_amount"] }}
        initial={existing}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /cancel/i }));
    expect(existing.strategy).toBe("priority");
    expect(existing.sourcePriority).toEqual(["Cooking the Books", "Lightspeed"]);
    expect(onCancel).toHaveBeenCalled();
  });

  it("submits a new rule with the chosen strategy", () => {
    const onSave = vi.fn();
    render(
      <RuleEditor
        open
        onCancel={() => {}}
        onSave={onSave}
        entities={["Sales"]}
        fieldsByEntity={{ Sales: ["quantity_sold", "net_amount"] }}
        initial={null}
      />,
    );
    fireEvent.click(screen.getByRole("radio", { name: /manual override/i }));
    fireEvent.click(screen.getByRole("button", { name: /save rule/i }));
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ strategy: "manual", entity: "Sales", field: "quantity_sold" }),
    );
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && bun run test -- components/rules/rule-editor.test.tsx`
Expected: FAIL — cannot find module `./rule-editor`.

- [ ] **Step 4: Write the component**

`frontend/components/rules/rule-editor.tsx`:

```tsx
"use client";

import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { CustomLogic, ResolutionRule, RuleStrategy, SaveResolutionRuleInput } from "@/lib/api";

const CUSTOM_OPTIONS: { value: CustomLogic; label: string }[] = [
  { value: "flag", label: "Flag unresolved" },
  { value: "highest", label: "Pick highest" },
  { value: "lowest", label: "Pick lowest" },
  { value: "newest", label: "Pick newest" },
];

interface RuleEditorProps {
  open: boolean;
  initial: ResolutionRule | null; // null => creating
  entities: string[];
  fieldsByEntity: Record<string, string[]>;
  onSave: (input: SaveResolutionRuleInput) => void;
  onCancel: () => void;
}

/**
 * The rule editor. It edits a local copy (never the live rule in place), and
 * calls onSave with a SaveResolutionRuleInput on submit.
 */
export function RuleEditor({
  open,
  initial,
  entities,
  fieldsByEntity,
  onSave,
  onCancel,
}: RuleEditorProps) {
  const [entity, setEntity] = useState(initial?.entity ?? entities[0] ?? "");
  const [field, setField] = useState(initial?.field ?? fieldsByEntity[entities[0]]?.[0] ?? "");
  const [strategy, setStrategy] = useState<RuleStrategy>(initial?.strategy ?? "priority");
  const [customLogic, setCustomLogic] = useState<CustomLogic>(initial?.customLogic ?? "flag");
  const [recomputeHistory, setRecomputeHistory] = useState(false);

  useEffect(() => {
    if (open) {
      setEntity(initial?.entity ?? entities[0] ?? "");
      setField(initial?.field ?? fieldsByEntity[entities[0]]?.[0] ?? "");
      setStrategy(initial?.strategy ?? "priority");
      setCustomLogic(initial?.customLogic ?? "flag");
      setRecomputeHistory(false);
    }
  }, [open, initial, entities, fieldsByEntity]);

  const fields = fieldsByEntity[entity] ?? [];

  function submit() {
    onSave({
      id: initial?.id,
      entity,
      field,
      strategy,
      sourcePriority: strategy === "priority" ? ["Cooking the Books", "Lightspeed"] : undefined,
      customLogic: strategy === "custom" ? customLogic : undefined,
    });
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onCancel()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{initial ? "Edit rule" : "New rule"}</DialogTitle>
          <DialogDescription>
            Choose a field and how disagreements on it should resolve.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="rule-entity">Entity</Label>
            <Select value={entity} onValueChange={setEntity}>
              <SelectTrigger id="rule-entity">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {entities.map((e) => (
                  <SelectItem key={e} value={e}>
                    {e}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="rule-field">Field</Label>
            <Select value={field} onValueChange={setField}>
              <SelectTrigger id="rule-field">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {fields.map((f) => (
                  <SelectItem key={f} value={f}>
                    {f}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-2">
            <Label>Strategy</Label>
            <RadioGroup value={strategy} onValueChange={(v) => setStrategy(v as RuleStrategy)}>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="priority" id="strategy-priority" />
                <Label htmlFor="strategy-priority">Priority by source</Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="manual" id="strategy-manual" />
                <Label htmlFor="strategy-manual">Manual override</Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="custom" id="strategy-custom" />
                <Label htmlFor="strategy-custom">Custom logic</Label>
              </div>
            </RadioGroup>
          </div>

          {strategy === "custom" ? (
            <div className="flex flex-col gap-2">
              <Label htmlFor="rule-custom">Custom logic</Label>
              <Select value={customLogic} onValueChange={(v) => setCustomLogic(v as CustomLogic)}>
                <SelectTrigger id="rule-custom">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CUSTOM_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}

          <div className="flex items-center gap-2">
            <Checkbox
              id="rule-recompute"
              checked={recomputeHistory}
              onCheckedChange={(c) => setRecomputeHistory(c === true)}
            />
            <Label htmlFor="rule-recompute">Recompute history with this rule</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button onClick={submit}>Save rule</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
```

Note: `Label` is added in Step 1.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && bun run test -- components/rules/rule-editor.test.tsx`
Expected: PASS.

- [ ] **Step 6: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint && bun run build`
Then:

```bash
git add frontend/components/ui/ frontend/components/rules/rule-editor.tsx frontend/components/rules/rule-editor.test.tsx frontend/package.json frontend/bun.lock
git commit -m "feat: add resolution-rule editor"
```

---

## Task 6: Rule audit component

**Files:**
- Create: `frontend/components/rules/rule-audit.tsx`
- Test: `frontend/components/rules/rule-audit.test.tsx`

**Interfaces:**
- Consumes: `RuleAuditEntry` (Task 2).
- Produces: `RuleAudit({ entries })`. Used by the page (Task 7).

- [ ] **Step 1: Write the failing test**

`frontend/components/rules/rule-audit.test.tsx`:

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RuleAudit } from "./rule-audit";
import type { RuleAuditEntry } from "@/lib/api";

const entries: RuleAuditEntry[] = [
  { id: "a1", ruleId: "rule-1", field: "quantity_sold", change: "created", at: "2026-09-29T18:00:00Z", by: "Stirling Donaldson" },
  { id: "a2", ruleId: null, field: "net_amount", change: "deleted", at: "2026-09-30T08:00:00Z", by: "Stirling Donaldson" },
];

describe("RuleAudit", () => {
  it("renders each entry with its change, field, and actor", () => {
    render(<RuleAudit entries={entries} />);
    expect(screen.getByText(/quantity_sold/i)).toBeInTheDocument();
    expect(screen.getByText(/created/i)).toBeInTheDocument();
    expect(screen.getByText(/deleted/i)).toBeInTheDocument();
    expect(screen.getAllByText(/stirling donaldson/i)).toHaveLength(2);
  });

  it("shows an empty note when there is no history", () => {
    render(<RuleAudit entries={[]} />);
    expect(screen.getByText(/no changes yet/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && bun run test -- components/rules/rule-audit.test.tsx`
Expected: FAIL — cannot find module `./rule-audit`.

- [ ] **Step 3: Write the component**

`frontend/components/rules/rule-audit.tsx`:

```tsx
import { Badge } from "@/components/ui/badge";
import type { RuleAuditEntry } from "@/lib/api";

/** The rule change history (who / what / when). */
export function RuleAudit({ entries }: { entries: RuleAuditEntry[] }) {
  if (entries.length === 0) {
    return <p className="text-sm text-muted-foreground">No changes yet.</p>;
  }

  return (
    <div className="rounded-lg border">
      {entries.map((e, i) => (
        <div
          key={e.id}
          className={`flex items-center justify-between gap-4 p-3 ${i > 0 ? "border-t" : ""}`}
        >
          <div className="min-w-0">
            <p className="text-sm font-medium">{e.field}</p>
            <p className="truncate text-xs text-muted-foreground">
              {e.by} · {new Date(e.at).toLocaleString()}
            </p>
          </div>
          <Badge variant={e.change === "deleted" ? "destructive" : "secondary"}>{e.change}</Badge>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && bun run test -- components/rules/rule-audit.test.tsx`
Expected: PASS.

- [ ] **Step 5: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint`
Then:

```bash
git add frontend/components/rules/rule-audit.tsx frontend/components/rules/rule-audit.test.tsx
git commit -m "feat: add rule audit history"
```

---

## Task 7: Page + sidebar entry

**Files:**
- Create: `frontend/app/(app)/resolution-rules/page.tsx`
- Create: `frontend/app/(app)/resolution-rules/layout.tsx`
- Modify: `frontend/components/app-shell/app-sidebar.tsx`

**Interfaces:**
- Consumes: `RuleList` (Task 4), `RuleEditor` (Task 5), `RecomputeBanner` (Task 3), `RuleAudit` (Task 6), `buildRuleRows` (Task 1), `getKnownFields` (Task 2), the `Api` rule methods (Task 2), `useApiData` and `useApi` (existing).

- [ ] **Step 1: Write the page**

`frontend/app/(app)/resolution-rules/layout.tsx`:

```tsx
import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = { title: "Resolution rules" };

export default function ResolutionRulesLayout({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
```

`frontend/app/(app)/resolution-rules/page.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { getKnownFields } from "@/lib/api/demo";
import { buildRuleRows } from "@/lib/rule-logic";
import { RecomputeBanner } from "@/components/rules/recompute-banner";
import { RuleList } from "@/components/rules/rule-list";
import { RuleEditor } from "@/components/rules/rule-editor";
import { RuleAudit } from "@/components/rules/rule-audit";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import type { ResolutionRule } from "@/lib/api";

const ENTITIES = ["Sales", "Shifts", "Products"];

export default function ResolutionRulesPage() {
  const api = useApi();
  const { data: rules, loading, error, reload } = useApiData((api) => api.listResolutionRules());
  const { data: status } = useApiData((api) => api.getRecomputeStatus());
  const { data: audit } = useApiData((api) => api.listRuleAudit());

  const [editing, setEditing] = useState<ResolutionRule | null>(null);
  const [creating, setCreating] = useState(false);

  if (loading) return <LoadingState rows={4} />;
  if (error) {
    return (
      <ErrorState
        title="Couldn't load resolution rules"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }

  const rows = buildRuleRows(rules ?? [], getKnownFields());

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Resolution rules</h1>
        <p className="text-sm text-muted-foreground">
          How disagreements between sources are resolved, per field.
        </p>
      </div>

      {status ? <RecomputeBanner status={status} /> : null}

      <RuleList
        rows={rows}
        onEdit={(id) => setEditing(rules?.find((r) => r.id === id) ?? null)}
        onDelete={async (id) => {
          await api.deleteResolutionRule(id);
          await reload();
        }}
        onNew={() => setCreating(true)}
      />

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Change history</h2>
        <RuleAudit entries={audit ?? []} />
      </section>

      <RuleEditor
        open={creating || editing !== null}
        initial={editing}
        entities={ENTITIES}
        fieldsByEntity={getKnownFields()}
        onSave={async (input) => {
          await api.saveResolutionRule(input);
          setCreating(false);
          setEditing(null);
          await reload();
        }}
        onCancel={() => {
          setCreating(false);
          setEditing(null);
        }}
      />
    </div>
  );
}
```

- [ ] **Step 2: Add the sidebar entry**

In `frontend/components/app-shell/app-sidebar.tsx`, add `Scale`-adjacent icon import (`ShieldCheck`) and a nav item in the Data group's `items` array, between "Reconciliation" and "Data health":

```tsx
{ title: "Resolution rules", href: "/resolution-rules", icon: ShieldCheck },
```

(Add `ShieldCheck` to the lucide-react import list.)

- [ ] **Step 3: Verify and commit**

Run: `cd frontend && bun run typecheck && bun run lint && bun run build`
Expected: all pass; build emits a `/resolution-rules` route.

```bash
git add "frontend/app/(app)/resolution-rules/page.tsx" "frontend/app/(app)/resolution-rules/layout.tsx" frontend/components/app-shell/app-sidebar.tsx
git commit -m "feat: add resolution-rules page and sidebar entry"
```

---

## Task 8: Full verification pass

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full frontend gate**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build`
Expected: all pass; `vitest run` reports the new rule tests plus the retained suite green.

- [ ] **Step 2: Manual smoke check (demo mode)**

Run `cd frontend && bun run dev`, open `http://localhost:3000/resolution-rules`, and confirm:
- The recompute banner shows "Last recomputed …".
- The rule list shows `Sales → quantity_sold → Cooking the Books wins` and `net_amount → Unresolved`.
- "New rule" opens the editor; choosing "Custom logic" reveals the declarative set; saving closes the dialog and the list updates.
- The change history lists the create/edit/delete actions.

- [ ] **Step 3: Commit any fixes**

If the smoke check surfaced a fix, commit it atomically with a `fix:` message; otherwise there is nothing to commit.
