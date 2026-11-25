import { test, expect } from "@playwright/test";

const at = "2026-11-25T06:45:00Z";
const feeds = [
  { source: "LIGHTSPEED", connectorName: "lightspeed-sale-items", status: "partial", lastRunAt: at, failureCount: 1, runnable: false,
    failure: { type: "SALE_ITEM_METADATA_UNOBSERVED", message: "Delivery has 1325 lines without receipt number; prior values are retained where available, otherwise unknown.", at, stackTrace: null } },
  { source: "CTB", connectorName: "ctb-revenue", status: "success", lastRunAt: at, failureCount: 0, failure: null, runnable: true },
  { source: "CTB", connectorName: "ctb-invoices", status: "partial", lastRunAt: at, failureCount: 1, runnable: true,
    failure: { type: "INVOICE_IDENTITY_WITHHELD", message: "Invoice at export position 1 of 2 withheld. Legacy supplier or line parentage requires review.", at, stackTrace: null } },
  { source: "CTB", connectorName: "ctb-invoice-pdf", status: "partial", lastRunAt: at, failureCount: 1, runnable: true,
    failure: { type: "PDF_ENRICHMENT_INCOMPLETE", message: "PDF retained but enrichment did not complete.", at, stackTrace: null } },
];

for (const width of [1440, 390]) {
  test(`shows qualifications and retained failures separately and retries invoice files at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
    let invoiceRetries = 0;
    let revenueRuns = 0;
    await page.route("**/api/**", route => {
      const path = new URL(route.request().url()).pathname;
      if (path === "/api/me") return route.fulfill({ json: { displayName: "Test Owner", department: "ALL", seniority: "OWNER" } });
      if (path === "/api/connectors") return route.fulfill({ json: feeds });
      if (path === "/api/connectors/CTB/sftp/run") { invoiceRetries++; return route.fulfill({ status: 204 }); }
      if (path === "/api/connectors/CTB/run") { revenueRuns++; return route.fulfill({ json: feeds[1] }); }
      if (path === "/api/dashboard/summary") return route.fulfill({ json: { ingestionCompleteness: null, openConflicts: 0, timeToDetectFailure: null, overrideUsage: null } });
      return route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Not included in this fixture" } });
    });
    await page.goto("/data-health");
    await expect(page.getByText("2 sources need attention", { exact: true })).toBeVisible();
    await expect(page.getByText("Receipt metadata needs review", { exact: true })).toBeVisible();
    await expect(page.getByText("Some invoices need review", { exact: true })).toBeVisible();
    await expect(page.getByText("PDF enrichment is incomplete", { exact: true })).toBeVisible();
    await expect(page.getByText(/figures.*are missing until it succeeds/i)).toHaveCount(0);
    const retry = page.getByRole("button", { name: "Retry invoice files", exact: true });
    await retry.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByText("Invoice files checked", { exact: true })).toBeVisible();
    expect(invoiceRetries).toBe(1);
    expect(revenueRuns).toBe(0);
    await expect(page.getByText("Some invoices need review", { exact: true })).toBeVisible();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `/tmp/goldys-ingestion-health-${width}.png`, fullPage: true });
  });
}
