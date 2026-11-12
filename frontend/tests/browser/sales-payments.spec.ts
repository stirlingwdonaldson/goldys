import { test, expect, type Page } from "@playwright/test";
import balancedDay from "../../../docs/audits/sales-payment-2026-11-12/balanced-day.json";

async function isolate(page: Page, difference = 0) {
  await page.addInitScript(() => window.localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    let body: unknown = [];
    if (path === "/api/me") body = { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" };
    else if (path === "/api/dashboard/summary") body = { ingestionCompleteness: 100, openConflicts: 0, timeToDetectFailure: "Unknown", overrideUsage: { count: 0, period: "this week" } };
    else if (path === "/api/sale-items/mix") body = balancedDay.saleItemMix.map(row => ({ ...row, quantity: 1, hasConflict: false }));
    else if (path === "/api/payments/mix") body = balancedDay.paymentMix.map((row, i) => ({ ...row, amount: Number(row.amount) + (i === 0 ? difference : 0), tip: 0, count: 1, hasConflict: false }));
    else if (path === "/api/payments" || path.startsWith("/api/data/")) body = { items: [], total: 0, page: 0, size: 10 };
    // All API traffic is local fixture data; these tests never contact the production backend.
    await route.fulfill({ json: body });
  });
}

for (const width of [1440, 390]) {
  test(`balanced sales/payment comparison at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await isolate(page);
    await page.goto("/sales-detail");
    const graph = page.getByLabel("Merchandise sales compared with POS payments", { exact: true });
    await expect(graph).toBeVisible();
    await expect(page.getByLabel("Sales and payment totals")).toContainText("$13,075.07");
    const payment = graph.locator('[data-id="settled"]');
    await expect(payment.getByTitle("$13,075.07", { exact: true })).toBeVisible();
    await expect(payment.getByTitle("Difference vs sales: $0.00", { exact: true })).toBeVisible();
    await expect(payment.getByTitle("OK", { exact: true })).toBeVisible();
    await expect(payment.getByTitle("Needs attention", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "Sales and POS payments" })).toBeVisible();
    await page.screenshot({ path: `/tmp/goldys-sales-comparison-${width}.png`, fullPage: true });
  });
}

test("a real one-cent gap remains visible and flagged", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await isolate(page, -0.01);
  await page.goto("/sales-detail");
  const payment = page.getByLabel("Merchandise sales compared with POS payments", { exact: true }).locator('[data-id="settled"]');
  await expect(payment.getByTitle("Difference vs sales: −$0.01", { exact: true })).toBeVisible();
  await expect(payment.getByTitle("Needs attention", { exact: true })).toBeVisible();
});
