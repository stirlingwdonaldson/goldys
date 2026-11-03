import { test, expect } from "@playwright/test";

for (const width of [1280, 390]) {
  test(`weekly report and Excel download at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "true"));
    await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
    await page.route("**/api/**", route => route.fulfill({ json:
      new URL(route.request().url()).pathname === "/api/me"
        ? { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" }
        : [] }));
    await page.goto("/weekly-report?from=2026-10-19&weeks=2");
    await expect(page.getByRole("heading", { name: "Weekly report", exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: "2026-10-26 – 2026-11-01", exact: true })).toBeVisible();
    await expect(page.getByText("Food COGS YTD %", { exact: true })).toBeVisible();
    await expect(page.getByText("Average review rating", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "2026-10-19 – 2026-10-25", exact: true }).click();
    await expect(page.getByRole("heading", { name: "2026-10-19 – 2026-10-25", exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const pending = page.waitForEvent("download");
    await page.getByRole("button", { name: "Download Excel", exact: true }).click();
    const download = await pending;
    expect(download.suggestedFilename()).toBe("DEMO-Goldys-weekly-2026-10-19-to-2026-11-01.xlsx");
    await download.saveAs(test.info().outputPath("weekly-report.xlsx"));
    await page.screenshot({ path: test.info().outputPath("weekly-report.png"), fullPage: true });
  });
}

test("live weekly-report permission denial cannot produce a download", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" } });
    if (path === "/api/reports/weekly") return route.fulfill({ status: 403, json: { code: "NOT_PERMITTED", message: "Inventory cost access required" } });
    return route.fulfill({ json: [] });
  });
  await page.goto("/weekly-report");
  await expect(page.getByText("You don't have access to the weekly report", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Download Excel", exact: true })).toBeDisabled();
});
