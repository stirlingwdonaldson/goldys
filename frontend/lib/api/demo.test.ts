import { describe, it, expect, vi } from "vitest";
import { demoApi } from "./demo";

describe("demoApi sales-detail lists", () => {
  it("filters payments by payment type", async () => {
    const page = await demoApi.listPayments({ paymentType: "Tyro" }, 0, 20);
    expect(page.items.length).toBeGreaterThan(0);
    for (const row of page.items) {
      expect(row.paymentTypeName).toBe("Tyro");
    }
  });

  it("filters payments by sale number", async () => {
    const page = await demoApi.listPayments({ saleNumber: "SALE-1001" }, 0, 20);
    expect(page.items.length).toBeGreaterThan(0);
    for (const row of page.items) {
      expect(row.saleNumber).toBe("SALE-1001");
    }
  });

  it("filters sale items by category", async () => {
    const page = await demoApi.listSaleItems({ category: "Food" }, 0, 20);
    expect(page.items.length).toBeGreaterThan(0);
    for (const row of page.items) {
      expect(row.categoryName).toBe("Food");
    }
  });

  it("returns at least two distinct payment types in the mix", async () => {
    const mix = await demoApi.getPaymentMix("2026-10-01", "2026-10-05");
    const types = new Set(mix.map((m) => m.paymentTypeName));
    expect(types.size).toBeGreaterThanOrEqual(2);
  });
});

describe("demoApi resolution rules", () => {
  it("applies a changed entity type, field key, and source priority when editing a rule", async () => {
    await demoApi.saveResolutionRule({
      id: "rule-2",
      entityType: "product_sales",
      fieldKey: "pint carlton draught",
      strategy: "priority",
      sourcePriority: ["LIGHTSPEED", "CTB"],
    });

    const rules = await demoApi.listResolutionRules();
    const edited = rules.find((r) => r.id === "rule-2");

    expect(edited?.entityType).toBe("product_sales");
    expect(edited?.fieldKey).toBe("pint carlton draught");
    expect(edited?.sourcePriority).toEqual(["LIGHTSPEED", "CTB"]);
  });

  it("lists the demo products", async () => {
    const products = await demoApi.listProducts();
    expect(products).toContain("garlic aioli");
    expect(products).toContain("pint carlton draught");
  });
});

describe("demoApi dashboards v2", () => {
  it("lists starting-point templates with v2 widgets", async () => {
    const templates = await demoApi.listDashboardTemplates();
    expect(templates.length).toBeGreaterThan(0);
    for (const t of templates) {
      expect(t.widgets.length).toBeGreaterThan(0);
      for (const w of t.widgets) {
        expect(w.renderType).toBeTruthy();
        expect(w.queries.length).toBeGreaterThan(0);
        expect(w.layout.w).toBeGreaterThanOrEqual(1);
        expect(w.layout.h).toBeGreaterThanOrEqual(1);
      }
    }
  });

  it("persists filters, visibility and a pinned flag when saving a dashboard", async () => {
    const created = await demoApi.saveDashboard({
      title: "Sales",
      layout: "grid",
      filters: {
        dateRange: { from: "2026-10-01", to: "2026-10-05", calendar: "CALENDAR" },
        comparison: "PREVIOUS_WEEK",
        dimensions: ["DEPARTMENT"],
      },
      visibility: "SHARED",
      widgets: [
        {
          id: "w1",
          renderType: "time-series",
          queries: [
            {
              metric: "sales.gross",
              range: { from: "2026-10-01", to: "2026-10-05", calendar: "CALENDAR" },
              grain: "DAY",
              dimensions: [],
              comparison: null,
            },
          ],
          layout: { w: 6, h: 2 },
        },
      ],
    });

    expect(created.schemaVersion).toBe(2);
    expect(created.filters.comparison).toBe("PREVIOUS_WEEK");
    expect(created.filters.dimensions).toEqual(["DEPARTMENT"]);
    expect(created.visibility).toBe("SHARED");
    expect(created.pinned).toBe(false);
  });

  it("instantiates a dashboard from a template as PRIVATE and unpinned", async () => {
    const templates = await demoApi.listDashboardTemplates();
    const created = await demoApi.createDashboardFromTemplate(templates[0].id);

    expect(created.title).toBe(templates[0].name);
    expect(created.widgets).toEqual(templates[0].widgets);
    expect(created.visibility).toBe("PRIVATE");
    expect(created.pinned).toBe(false);
  });

  it("renders a dashboard as RenderedWidget[] with a deniedResource field", async () => {
    const created = await demoApi.saveDashboard({
      title: "Sales",
      widgets: [
        {
          id: "w1",
          renderType: "time-series",
          queries: [
            {
              metric: "sales.gross",
              range: { from: "2026-10-01", to: "2026-10-05", calendar: "CALENDAR" },
              grain: "DAY",
              dimensions: [],
              comparison: null,
            },
          ],
          layout: { w: 6, h: 2 },
        },
      ],
    });

    const rendered = await demoApi.renderDashboard(created.id);
    expect(rendered).toHaveLength(1);
    expect(rendered[0].widgetId).toBe("w1");
    expect(rendered[0].widget).not.toBeNull();
    expect(rendered[0].deniedResource).toBeNull();
  });

  it("toggles the pinned flag", async () => {
    const created = await demoApi.saveDashboard({ title: "Pinned", widgets: [] });
    const toggled = await demoApi.toggleDashboardPin(created.id);
    expect(toggled.pinned).toBe(true);
  });

  it("returns and updates sharing visibility", async () => {
    const created = await demoApi.saveDashboard({ title: "Shared", widgets: [] });

    const before = await demoApi.getDashboardSharing(created.id);
    expect(before.visibility).toBe("PRIVATE");

    const after = await demoApi.setDashboardSharing(created.id, {
      visibility: "SHARED",
      roles: [{ department: { value: "BOH" }, seniority: { value: "MANAGER" } }],
    });
    expect(after.visibility).toBe("SHARED");
    expect(after.roles).toHaveLength(1);

    const doc = await demoApi.getDashboard(created.id);
    expect(doc.visibility).toBe("SHARED");
  });

  it("lists revisions and restores the initial revision", async () => {
    const created = await demoApi.saveDashboard({ title: "Versioned", widgets: [] });

    const revisions = await demoApi.listDashboardRevisions(created.id);
    expect(revisions).toEqual([{ revision: 1, createdBy: "You", createdAt: created.createdAt }]);

    const restored = await demoApi.restoreDashboardRevision(created.id, 1);
    expect(restored.title).toBe("Versioned");
  });
});

describe("demoApi platform map", () => {
  it("shows failing, stale and never-run sources and only links known nodes", async () => {
    const map = await demoApi.getPlatformMap();
    const tones = Object.fromEntries(
      map.nodes.filter((n) => n.kind === "source").map((n) => [n.id, n.tone]),
    );
    expect(tones).toMatchObject({ "source:CTB": "fail", "source:OPENTABLE": "warn", "source:DEPUTY": "missing" });
    const ids = new Set(map.nodes.map((n) => n.id));
    expect(ids.size).toBe(map.nodes.length);
    for (const e of map.edges) {
      expect(ids.has(e.source), e.source).toBe(true);
      expect(ids.has(e.target), e.target).toBe(true);
    }
  });
});

describe("demoApi forecasting", () => {
  it("lists forecastable metrics with the picker fields", async () => {
    const metrics = await demoApi.listForecastMetrics();
    expect(metrics.length).toBeGreaterThan(0);
    for (const m of metrics) {
      expect(m.metric).toBeTruthy();
      expect(m.name).toBeTruthy();
      expect(m.unit).toBeTruthy();
      expect(m.sourceDomain).toBeTruthy();
      expect(m.requiredPermission).toBeTruthy();
    }
    const foodCost = metrics.find((m) => m.metric === "inventory.food_cost_percent");
    expect(foodCost?.sourceDomain).toBe("derived");
  });

  it("creates a succeeded run that echoes the request and is immediately retrievable", async () => {
    const run = await demoApi.createForecastRun({
      target: "sales.gross",
      covariates: ["reservations.covers"],
      historyFrom: "2026-09-26",
      horizonDays: 7,
    });
    expect(run.id).toBe("fc-1");
    expect(run.status).toBe("SUCCEEDED");
    expect(run.targetMetric).toBe("sales.gross");
    expect(run.covariates).toEqual(["reservations.covers"]);
    expect(run.horizonDays).toBe(7);
    expect(run.errorMessage).toBeNull();
    expect(run.modelVersion).toBeTruthy();
    expect(run.series).not.toBeNull();
    expect(run.series!.length).toBe(7);
    for (const p of run.series!) {
      expect(p.p10!).toBeLessThanOrEqual(p.median!);
      expect(p.median!).toBeLessThanOrEqual(p.p90!);
      expect(p.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    }

    // The run-flow poll loop must see SUCCEEDED straight away, not a queued run.
    const reloaded = await demoApi.getForecastRun(run.id);
    expect(reloaded.status).toBe("SUCCEEDED");
    expect(reloaded.series).not.toBeNull();
    expect(reloaded.series!.length).toBe(7);
  });

  it("lists two runs, newest first, including a succeeded one", async () => {
    const runs = await demoApi.listForecastRuns();
    expect(runs.length).toBe(2);
    expect(runs[0].id).toBe("fc-2");
    expect(runs[0].status).toBe("SUCCEEDED");
    expect(runs[1].status).toBe("QUEUED");
  });

  it("returns a succeeded run with an ordered median/p10/p90 series", async () => {
    const run = await demoApi.getForecastRun("fc-2");
    expect(run.status).toBe("SUCCEEDED");
    expect(run.series).not.toBeNull();
    expect(run.series!.length).toBeGreaterThan(0);
    for (const p of run.series!) {
      expect(p.p10!).toBeLessThanOrEqual(p.median!);
      expect(p.median!).toBeLessThanOrEqual(p.p90!);
      expect(p.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    }
  });

  it("returns a comparison with a small MAPE/MAE and actuals on past dates", async () => {
    const comparison = await demoApi.getForecastComparison("fc-2");
    expect(comparison.runId).toBe("fc-2");
    expect(comparison.mape).not.toBeNull();
    expect(comparison.mae).not.toBeNull();
    expect(comparison.mape!).toBeLessThan(0.1);
    expect(comparison.points.length).toBeGreaterThan(0);
    for (const p of comparison.points) {
      expect(p.predictedMedian).toBeTypeOf("number");
    }
    const past = comparison.points.filter((p) => p.actual !== null);
    expect(past.length).toBeGreaterThan(0);
  });

  it("rejects an unknown run id", async () => {
    await expect(demoApi.getForecastRun("nope")).rejects.toThrow();
  });
});

describe("demoApi OpenTable assisted sign-in", () => {
  it("walks sign-in, sync and completion, streaming a frame while signing in", async () => {
    vi.useFakeTimers();
    try {
      const states: string[] = [];
      const frames: number[] = [];
      const stop = demoApi.watchOpenTableSession({
        onState: (s) => states.push(s.state),
        onFrame: (f) => frames.push(f.width),
        onClosed: () => {},
      });

      const started = demoApi.startOpenTableSession("INCREMENTAL");
      await vi.advanceTimersByTimeAsync(300);
      expect((await started).state).toBe("LOGIN_REQUIRED");
      expect(frames).toContain(1280);

      // Typing before Enter doesn't sign in; Enter simulates completing the login.
      const typed = demoApi.sendOpenTableInput({ type: "text", text: "a" });
      await vi.advanceTimersByTimeAsync(20);
      await typed;
      expect(states.at(-1)).toBe("LOGIN_REQUIRED");
      const enter = demoApi.sendOpenTableInput({ type: "key", action: "down", key: "Enter" });
      await vi.advanceTimersByTimeAsync(20);
      await enter;
      expect(states.at(-1)).toBe("SYNCING");

      await vi.advanceTimersByTimeAsync(2500);
      const done = demoApi.getOpenTableSession();
      await vi.advanceTimersByTimeAsync(150);
      expect((await done).state).toBe("DONE");
      expect((await done).runStatus).toBe("SUCCESS");
      stop();
    } finally {
      vi.useRealTimers();
    }
  });
});
