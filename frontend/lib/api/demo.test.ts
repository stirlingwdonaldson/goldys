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
    expect(foodCost).toBeUndefined();
  });

  it("creates a succeeded run that echoes the request and is immediately retrievable", async () => {
    const run = await demoApi.createForecastRun({
      target: "sales.net",
      covariates: [],
      historyFrom: "2026-09-26",
      horizonDays: 7,
    });
    expect(run.id).toBe("fc-1");
    expect(run.status).toBe("SUCCEEDED");
    expect(run.targetMetric).toBe("sales.net");
    expect(run.covariates).toEqual([]);
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

  it("getForecastRunReturnsDiagnosticsAndSplit", async () => {
    const run = await demoApi.getForecastRun("fc-2");
    expect(run.diagnostics).not.toBeNull();
    expect(run.diagnostics!.importance!.length).toBeGreaterThan(0);
    expect(run.diagnostics!.backtest!.byWeekday!.length).toBeGreaterThan(0);
    expect(run.diagnostics!.backtest!.rows!.length).toBeGreaterThan(0);
    expect(run.diagnostics!.holdoutNetXgboost).not.toBeNull();
    expect(run.series).not.toBeNull();
    for (const p of run.series!) {
      expect(p.food).toBeTypeOf("number");
      expect(p.bev).toBeTypeOf("number");
      expect(p.baseline).toBeTypeOf("number");
    }
  });

  it("returns a comparison with a small MAPE/MAE and actuals on past dates", async () => {
    const comparison = await demoApi.getForecastComparison("fc-2");
    expect(comparison.runId).toBe("fc-2");
    expect(comparison.mape).not.toBeNull();
    expect(comparison.mae).not.toBeNull();
    expect(comparison.mape!).toBeLessThan(10); // percent, matching the live score()
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

describe("demoApi product categorisation", () => {
  it("returns taxonomy fixtures including a flat null-group category", async () => {
    const taxonomy = await demoApi.getCategoryTaxonomy();
    expect(taxonomy.length).toBeGreaterThan(0);
    const flat = taxonomy.filter((c) => c.groupName === null);
    expect(flat.length).toBeGreaterThan(0);
    expect(taxonomy.some((c) => c.groupName !== null)).toBe(true);
  });

  it("returns assignments with a NEEDS_REVIEW row and treats no status as all", async () => {
    const all = await demoApi.getCategoryAssignments();
    expect(all.length).toBeGreaterThan(0);
    expect(all.some((a) => a.status === "NEEDS_REVIEW")).toBe(true);

    const reviewed = await demoApi.getCategoryAssignments("NEEDS_REVIEW");
    expect(reviewed.length).toBeGreaterThan(0);
    for (const row of reviewed) {
      expect(row.status).toBe("NEEDS_REVIEW");
    }
  });

  it("returns embedding-space points with numeric coordinates", async () => {
    const points = await demoApi.getCategoryEmbeddingSpace();
    expect(points.length).toBeGreaterThan(0);
    for (const p of points) {
      expect(typeof p.x).toBe("number");
      expect(typeof p.y).toBe("number");
    }
  });

  it("lists runs newest-first with a succeeded historical run", async () => {
    const runs = await demoApi.listClassificationRuns();
    expect(runs.length).toBeGreaterThan(0);
    expect(runs[0].status).toBe("SUCCEEDED");
    expect(runs[0].kind).toBe("FULL");
    expect(runs[0].productCount).not.toBeNull();
  });

  it("submits a run and reports it succeeded on the next poll", async () => {
    await demoApi.runClassification("FULL");
    const runs = await demoApi.listClassificationRuns();

    // Most recent first: the just-submitted run is at the head, completed by the poll.
    expect(runs[0].kind).toBe("FULL");
    expect(runs[0].status).toBe("SUCCEEDED");
    expect(runs[0].productCount).not.toBeNull();
    expect(runs[0].finishedAt).not.toBeNull();
  });

  it("lists suggestions and applies them into the taxonomy", async () => {
    const suggestions = await demoApi.getCategorySuggestions();
    expect(suggestions.length).toBeGreaterThan(0);
    expect(suggestions.some((s) => s.groupName === null)).toBe(true);
    expect(suggestions.some((s) => s.hint !== null)).toBe(true);

    const before = (await demoApi.getCategoryTaxonomy()).length;
    const created = await demoApi.applyCategorySuggestions();
    expect(created.length).toBe(suggestions.length);

    const taxonomy = await demoApi.getCategoryTaxonomy();
    expect(taxonomy.length).toBe(before + suggestions.length);
    expect(taxonomy.some((c) => c.name === "Craft Beer")).toBe(true);
    expect((await demoApi.getCategorySuggestions()).length).toBe(0);
  });

  it("deletes a category and unassigns its products", async () => {
    const before = (await demoApi.getCategoryTaxonomy()).length;
    await demoApi.deleteCategory("cat-beer");

    const taxonomy = await demoApi.getCategoryTaxonomy();
    expect(taxonomy.length).toBe(before - 1);
    expect(taxonomy.some((c) => c.id === "cat-beer")).toBe(false);

    const beer = (await demoApi.getCategoryAssignments()).find(
      (a) => a.productNameKey === "pint carlton draught",
    );
    expect(beer?.categoryId).toBeNull();
  });
});

describe("demoApi invoices", () => {
  it("lists a page containing both a linked (hasPdf) and an unlinked invoice", async () => {
    const page = await demoApi.listInvoices({}, 0, 20);
    expect(page.items.length).toBeGreaterThan(0);
    expect(page.items.some((i) => i.hasPdf)).toBe(true);
    expect(page.items.some((i) => !i.hasPdf)).toBe(true);
  });

  it("returns an empty page when no invoice matches the supplier", async () => {
    const page = await demoApi.listInvoices({ supplier: "No Such Supplier" }, 0, 20);
    expect(page.items).toEqual([]);
    expect(page.total).toBe(0);
  });

  it("returns a header plus lines for a known invoice", async () => {
    const detail = await demoApi.getInvoice("INV-1042");
    expect(detail.invoiceNumber).toBe("INV-1042");
    expect(detail.lines.length).toBeGreaterThan(0);
    for (const line of detail.lines) {
      expect(line.productNameKey).toBeTruthy();
    }
  });

  it("throws NOT_FOUND for an unknown invoice number", async () => {
    await expect(demoApi.getInvoice("NOPE")).rejects.toMatchObject({ code: "NOT_FOUND" });
  });
});

describe("demoApi reviews analytics", () => {
  it("returns a non-zero overview total and a negative count", async () => {
    const overview = await demoApi.getReviewOverview("2026-10-01", "2026-10-07");
    expect(overview.total).toBeGreaterThan(0);
    expect(overview.negative).toBeGreaterThan(0);
  });

  it("filters reviews by aspect, returning only reviews mentioning that aspect", async () => {
    const reviews = await demoApi.getReviews({ aspect: "service" });
    expect(reviews.length).toBeGreaterThan(0);
    for (const review of reviews) {
      expect(review.aspects.map((a) => a.aspect)).toContain("service");
    }
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

describe("qualified invoice demo identity", () => {
  it("keeps same-number invoices separate and refuses a legacy first match", async () => {
    const page = await demoApi.listInvoices({},0,20);
    const shared = page.items.filter(i => i.invoiceNumber === "INV-SHARED");
    expect(shared).toHaveLength(2);
    expect(shared[0].invoiceId).not.toBe(shared[1].invoiceId);
    const details = await Promise.all(shared.map(i => demoApi.getInvoiceById(i.invoiceId!)));
    expect(details.map(i => i.lines[0].lineTotal)).toEqual([100,200]);
    await expect(demoApi.getInvoice("INV-SHARED")).rejects.toMatchObject({code:"VALIDATION_FAILED"});
  });
});

describe("demo dashboard qualifications", () => {
  it("keeps the requested sales grid, explicit zero, unresolved and absent amounts", async () => {
    const result = await demoApi.getSalesTrend("2026-10-01", "2026-10-06");
    expect(result.points).toHaveLength(6);
    expect(result.points[0].total).toBe(0);
    expect(result.points[3].total).toBeNull();
    expect(result.points[5].total).toBeNull();
    expect(result.provenance.coverage?.unresolvedDates).toEqual(["2026-10-04"]);
    expect(result.provenance.coverage?.absentDates).toEqual(["2026-10-06"]);
    expect(result.notices.join(" ")).toContain("Incomplete subtotal");
  });

  it("shares ranking values and qualifications and keeps them for empty periods", async () => {
    const bootstrap = await demoApi.getDashboardBootstrap();
    expect(bootstrap.topSellers).toEqual(await demoApi.getTopSellers());
    expect(bootstrap.salesTrend).toEqual(await demoApi.getSalesTrend());
    const ranking = await demoApi.queryMetrics({metric: "product.top_sellers", range: {from: "2026-10-05", to: "2026-10-05", calendar: "CALENDAR"}, grain: "DAY", dimensions: [], comparison: null});
    const direct = await demoApi.getTopSellers("2026-10-05", "2026-10-05");
    expect(ranking.notices).toEqual(direct.notices);
    expect(ranking.provenance.coverage).toEqual(direct.coverage);
    expect(ranking.provenance.missingPeriods).toEqual(["2026-10-05"]);
    const empty = await demoApi.getTopSellers("2026-10-06", "2026-10-06");
    expect(empty.items).toEqual([]);
    expect(empty.coverage.absentDates).toEqual(["2026-10-06"]);
    expect(empty.notices).toContain("Ranked by known sales; full-period ranking unverified.");
  });
});

it("keeps Home and Kitchen purchases on the same qualified demo basis", async () => {
  const [home, kitchen] = await Promise.all([demoApi.getDashboardBootstrap(), demoApi.getInventorySummary("2026-09-25", "2026-10-24")]);
  expect(home.foodCost).toEqual({ purchases: kitchen.purchases, wastage: kitchen.wastage, foodCostPercent: null });
});
