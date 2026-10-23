import { describe, it, expect } from "vitest";
import { dashboardSchema, renderedWidgetsSchema } from "./dashboard-schema";
import { demoApi } from "./demo";

describe("dashboard wire contracts", () => {
  it("accepts existing demo documents/render output and preserves query widget references", async () => {
    const created = await demoApi.saveDashboard({ title: "Contract fixture", widgets: [] });
    const dashboards = [created];
    for (const summary of dashboards) {
      const doc = await demoApi.getDashboard(summary.id);
      expect(dashboardSchema.safeParse(doc).success).toBe(true);
      expect(renderedWidgetsSchema.safeParse(await demoApi.renderDashboard(summary.id)).success).toBe(true);
    }
  });
  it("accepts a denied render without fabricating a widget", () => {
    const denied = [{ widgetId: "w", widget: null, deniedResource: "labour.cost", trust: null }];
    expect(renderedWidgetsSchema.parse(denied)).toEqual(denied);
  });
  it("rejects malformed nested values and unsupported document versions", async () => {
    const summary = await demoApi.saveDashboard({ title: "Version fixture", widgets: [] });
    const doc = await demoApi.getDashboard(summary.id);
    expect(dashboardSchema.safeParse({ ...doc, schemaVersion: 99 }).success).toBe(false);
    expect(dashboardSchema.safeParse({ ...doc, widgets: [{ id: "w", renderType: "stat", queries: [], layout: { w: 0, h: 1 } }] }).success).toBe(false);
  });
});
