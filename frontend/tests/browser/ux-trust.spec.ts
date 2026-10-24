import { test, expect } from "@playwright/test";

const summary = { ingestionCompleteness: null, openConflicts: 0, timeToDetectFailure: null, overrideUsage: null };
const bootstrap = {
  summary, latestSales: { date: "2026-10-24", total: 100 },
  salesTrend: { points: [], notices: ["Incomplete sales subtotal"] }, activity: [],
  topSellers: { items: [], notices: [] },
  coversToday: { covers: 10, bookings: null, noShows: null },
  // Even a legacy non-null percentage cannot turn invoiced purchases into consumed cost.
  foodCost: { purchases: 500, wastage: 0, foodCostPercent: 0.31 },
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("goldys-demo-mode", "false");
    localStorage.setItem("goldys-show-incomplete-notices", "false");
  });
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" } });
    if (path === "/api/dashboard/summary") return route.fulfill({ json: summary });
    if (path === "/api/dashboard/bootstrap") return route.fulfill({ json: bootstrap });
    if (path === "/api/connectors") return route.fulfill({ json: [
      { source: "CTB", connectorName: "ctb", status: "partial", lastRunAt: "2026-10-24T04:00:00Z", failureCount: 0, runnable: true },
      { source: "OPENTABLE", connectorName: "opentable", status: "never_run", lastRunAt: null, failureCount: 0, runnable: false },
    ] });
    return route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Check unavailable in fixture" } });
  });
});

for (const width of [1440, 390]) {
  test(`Home keeps caveats, unknowns and purchase basis at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto("/dashboard");
    await expect(page.getByText("Incomplete sales subtotal", { exact: true })).toBeVisible();
    await expect(page.getByText("— bookings · — declared no-shows", { exact: true })).toBeVisible();
    await expect(page.getByText("Attendance proxy today", { exact: true })).toBeVisible();
    await expect(page.getByText("Invoice purchases", { exact: true })).toBeVisible();
    await expect(page.getByText("$500.00", { exact: true })).toBeVisible();
    await expect(page.getByText("31%", { exact: true })).toHaveCount(0);
    await expect(page.getByText(/^Not checked:/)).toBeVisible();
    await expect(page.getByText("All numbers reconcile", { exact: true })).toHaveCount(0);
    const collection = page.getByRole("link", { name: /Last collection run/ });
    await expect(collection).toHaveAccessibleName(/0 of 2 latest runs completed without errors/);
    await expect(collection).toHaveAccessibleName(/partial.*never run/);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}

test("unavailable work discovery does not announce an all-clear", async ({ page }) => {
  await page.route("**/api/connectors", route => route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Feed checks unavailable" } }));
  await page.goto("/my-work");
  await expect(page.getByText("No known items in this view", { exact: true })).toBeVisible();
  await expect(page.getByText(/Not included:/)).toBeVisible();
  await expect(page.getByText(/does not establish that every source was checked/)).toBeVisible();
  await expect(page.getByText(/Every figure.*reconciles and every feed/)).toHaveCount(0);
});

test("both Home and My work disclose the bounded decision page", async ({ page }) => {
  await page.route("**/api/connectors", route => route.fulfill({ json: [] }));
  const items = Array.from({ length: 200 }, (_, i) => ({
    id: `decision-${i}`, recordId: "2026-10-24", entity: "daily_sales", field: "total_sales", status: "conflict",
    sources: [{ source: "LIGHTSPEED", value: "100" }, { source: "CTB", value: "99" }],
  }));
  await page.route("**/api/reconciliation/exceptions?*", route => route.fulfill({ json: { items, total: 201, page: 0, size: 200 } }));
  await page.goto("/my-work");
  await expect(page.getByText(/Showing the first 200 daily-sales decisions/)).toBeVisible();
  await expect(page.getByText("200 shown", { exact: true })).toBeVisible();
  await page.goto("/dashboard");
  await expect(page.getByText(/Only the first 200 daily-sales decisions are included/)).toBeVisible();
});
