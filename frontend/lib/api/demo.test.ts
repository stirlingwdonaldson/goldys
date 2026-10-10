import { describe, it, expect } from "vitest";
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
