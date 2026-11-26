import { test, expect } from "@playwright/test";

for (const width of [1440, 390]) {
  test(`Tanda setup preserves missing data and links to connection health at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
    await page.route("**/api/**", route => {
      const path = new URL(route.request().url()).pathname;
      if (path === "/api/me") return route.fulfill({ json: { displayName: "Test Owner", department: "ALL", seniority: "OWNER" } });
      if (path === "/api/labour/summary") return route.fulfill({ json: {
        from: "2026-10-28", to: "2026-11-26", scheduledHours: null, actualHours: null,
        labourCost: null, variance: null, hoursPerCover: null, labourCostPerCover: null,
        fohLabourCostPercent: null, bohLabourCostPercent: null, notices: ["Tanda mapping awaits validation"]
      } });
      if (path === "/api/connectors") return route.fulfill({ json: [{ source: "TANDA", connectorName: "tanda-api", lastRunAt: null, status: "never_run", failureCount: 0, failure: null, runnable: true }] });
      return route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Not included in fixture" } });
    });
    await page.goto("/staff");
    await expect(page.getByRole("heading", { name: "Tanda reporting" })).toBeVisible();
    const link = page.getByRole("link", { name: "Open Tanda connection status" });
    await expect(link).toBeVisible();
    await expect(page.getByText(/Deputy/)).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `/tmp/goldys-tanda-${width}.png`, fullPage: true, animations: "disabled" });
    await link.focus();
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/data-health/);
    await expect(page.getByText("Tanda", { exact: true }).first()).toBeVisible();
  });
}

for (const width of [1440, 390]) {
  test(`Tanda native teams and missing worked hours render at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
    await page.route("**/api/**", route => {
      const path = new URL(route.request().url()).pathname;
      if (path === "/api/me") return route.fulfill({ json: { displayName: "Test Owner", department: "ALL", seniority: "OWNER" } });
      if (path === "/api/labour/summary") return route.fulfill({ json: {
        from: "2026-10-28", to: "2026-11-26", scheduledHours: 195.5, actualHours: 21,
        labourCost: 168.53, variance: null, hoursPerCover: null, labourCostPerCover: null,
        fohLabourCostPercent: null, bohLabourCostPercent: null,
        notices: ["Recorded source observations; some days and costs are missing."],
        days: ["Kitchen", "Bar", "Floor", "Bussy", "FOH Managers", "Kitchen Managers", "Admin"].map((department, i) => ({
          date: "2026-11-27", department, scheduledHours: i + 5, actualHours: null,
          scheduledCost: 200 + i * 50, actualCost: null, authoritativeSource: "TANDA", hasConflict: false,
        })),
      } });
      return route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Not included in fixture" } });
    });
    await page.goto("/staff");
    await expect(page.getByRole("heading", { name: "Daily team figures" })).toBeVisible();
    for (const name of ["Kitchen", "Bar", "Floor", "Bussy", "FOH Managers", "Kitchen Managers", "Admin"]) {
      await expect(page.getByRole("cell", { name, exact: true })).toBeVisible();
    }
    await expect(page.getByText("Connect Tanda", { exact: true })).toHaveCount(0);
    await expect(page.getByText("FOH, % of sales", { exact: true })).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `/tmp/goldys-tanda-populated-${width}.png`, fullPage: true, animations: "disabled" });
  });
}
