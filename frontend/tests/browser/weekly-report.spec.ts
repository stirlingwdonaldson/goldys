import { demoWeeklyReport } from "../../lib/api/weekly-report-demo";
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
    await page.screenshot({ path: test.info().outputPath("weekly-report-overview.png"), fullPage: false });
    await page.screenshot({ path: test.info().outputPath("weekly-report-collapsed.png"), fullPage: true });
    const summary = page.getByRole("region", { name: "Selected week summary" });
    await expect(summary.getByText("$97,000.00", { exact: true })).toBeVisible();
    await expect(summary.getByText("4.5 stars", { exact: true })).toBeVisible();
    const targets = page.getByRole("table", { name: "Effective weekly targets", exact: true });
    await expect(targets.getByText("Below target", { exact: true })).toHaveCount(3);
    await expect(targets.getByText("Incomplete daily values", { exact: true })).toHaveCount(3);
    await page.getByRole("button", { name: "2026-10-19 – 2026-10-25", exact: true }).click();
    await expect(page.getByRole("heading", { name: "2026-10-19 – 2026-10-25", exact: true })).toBeVisible();
    await expect(summary.getByText("$90,000.00", { exact: true })).toBeVisible();
    await expect(targets.getByText("Below target", { exact: true })).toHaveCount(0);
    await expect(targets.getByText("Incomplete daily values", { exact: true })).toHaveCount(4);
    await page.locator("summary").filter({ hasText: "Wastage reasons & stock counts" }).click();
    await expect(page.getByRole("cell", { name: /^No reason recorded/ })).toBeVisible();
    const catalogue = page.getByRole("region", { name: "All report fields" });
    for (const disclosure of await catalogue.locator("summary").all()) await disclosure.click();
    await expect(catalogue.getByRole("rowheader")).toHaveCount(85);
    await expect(catalogue.getByText("Food COGS YTD %", { exact: true })).toBeVisible();
    const notes = catalogue.getByRole("button", { name: "Source notes for Food COGS YTD %, week of 2026-10-19", exact: true });
    await notes.focus();
    await notes.press("Enter");
    await expect(page.getByRole("dialog")).toContainText("verified costs, classification and cumulative coverage");
    expect(await page.getByRole("dialog").evaluate(el => el.getBoundingClientRect().right <= innerWidth)).toBe(true);
    await page.keyboard.press("Escape");
    await expect(notes).toBeFocused();
    await expect(page.getByRole("heading", { name: "Stock counts", exact: true })).toBeVisible();
    await page.getByText("Edit weekly targets", { exact: true }).click();
    await page.getByLabel("Beverage wastage target % (%)", { exact: true }).fill("0.5");
    await page.getByRole("button", { name: "Save targets", exact: true }).click();
    await expect(page.getByText("Targets saved.", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Beverage wastage target % (%)", { exact: true })).toHaveValue("0.5");
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
  await page.screenshot({ path: test.info().outputPath("weekly-report-denied.png"), fullPage: true });
  await expect(page.getByRole("button", { name: "Download Excel", exact: true })).toBeDisabled();
});

test("retained real completed week matches API snapshot and Excel download", async ({
  page,
}) => {
  test.skip(
    !process.env.GOLDYS_REAL_WEEK_REPORT,
    "Run ReportingRealWeekIntegrationTest and supply its JSON artifact.",
  );
  const { readFileSync } = await import("node:fs");
  const snapshot = JSON.parse(
    readFileSync(process.env.GOLDYS_REAL_WEEK_REPORT!, "utf8"),
  );
  await page.addInitScript(() =>
    localStorage.setItem("goldys-demo-mode", "false"),
  );
  await page.route("**/monitoring**", (route) =>
    route.fulfill({ status: 204 }),
  );
  await page.route("**/api/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/me")
      return route.fulfill({
        json: {
          displayName: "Fixture Owner",
          department: "ALL",
          seniority: "OWNER",
        },
      });
    if (path === "/api/reports/weekly")
      return route.fulfill({ json: snapshot });
    return route.fulfill({ json: [] });
  });
  await page.goto("/weekly-report?from=2026-10-26&weeks=1");
  await expect(
    page.getByRole("heading", { name: "2026-10-26 – 2026-11-01", exact: true }),
  ).toBeVisible();
  for (const disclosure of await page.getByRole("region", { name: "All report fields" }).locator("summary").all()) await disclosure.click();
  await expect(
    page
      .getByRole("rowheader", { name: "Known product sales ex GST", exact: true })
      .locator(".."),
  ).toContainText("$141,527.41");
  await expect(
    page
      .getByRole("rowheader", { name: "Food sales ex GST", exact: true })
      .locator(".."),
  ).toContainText("$48,442.78");
  await expect(
    page
      .getByRole("rowheader", { name: "Beverage recorded wastage", exact: true })
      .locator(".."),
  ).toContainText("$535.86");
  await expect(
    page
      .getByRole("rowheader", { name: "Unclassified recorded wastage", exact: true })
      .locator(".."),
  ).toContainText("$0.00");
  for (const [label, value] of [
    ["food sales versus target", "$2,842.78"],
    ["Beverage recorded wastage / matching sales", "0.58%"],
    ["Food recorded wastage / matching sales", "0.05%"],
  ]) {
    await expect(page.getByRole("rowheader", { name: label, exact: true }).locator("..")).toContainText(value);
  }
  for (const [label, value] of [
    ["Food known CTB theoretical cost subtotal", "$14,143.34"],
    ["Beverage known CTB theoretical cost subtotal", "$4,771.40"],
    ["Total known CTB theoretical cost subtotal", "$18,914.74"],
  ]) {
    await expect(page.getByRole("rowheader", { name: label, exact: true }).locator("..")).toContainText(value);
  }
  await expect(page.getByRole("rowheader", { name: "Food theoretical COGS (CTB estimate)", exact: true }).locator("..")).toContainText("Unavailable");
  const pending = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "Download Excel", exact: true })
    .click();
  const download = await pending;
  expect(download.suggestedFilename()).toBe(
    "Goldys-weekly-2026-10-26-to-2026-11-01.xlsx",
  );
  await download.saveAs(test.info().outputPath("real-week-report.xlsx"));
  await page.screenshot({
    path: test.info().outputPath("real-week-report.png"),
    fullPage: true,
  });
});

test("loading, request failure, retry and refresh use only the loaded snapshot", async ({ page }) => {
  const snapshot = demoWeeklyReport("2026-10-26", 1);
  let requests = 0;
  let release: () => void = () => {};
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" } });
    if (path === "/api/reports/weekly") {
      requests++;
      if (requests === 1) {
        await pending;
        return route.fulfill({ status: 500, json: { code: "INTERNAL_ERROR", message: "Fixture report temporarily unavailable" } });
      }
      return route.fulfill({ json: snapshot });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto("/weekly-report?from=2026-10-26&weeks=1");
  await expect(page.locator('[aria-busy="true"]').first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Download Excel", exact: true })).toBeDisabled();
  await page.screenshot({ path: test.info().outputPath("weekly-report-loading.png"), fullPage: true });
  release();
  await expect(page.getByRole("alert").filter({ hasText: "Couldn't load weekly report" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Download Excel", exact: true })).toBeDisabled();
  await page.screenshot({ path: test.info().outputPath("weekly-report-error.png"), fullPage: true });
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.getByRole("heading", { name: "2026-10-26 – 2026-11-01", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect.poll(() => requests).toBe(3);
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download Excel", exact: true }).click();
  expect((await download).suggestedFilename()).toBe("Goldys-weekly-2026-10-26-to-2026-11-01.xlsx");
  expect(requests).toBe(3);
});

test("missing values remain explicit and URL week controls still select the report range", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  const snapshot = demoWeeklyReport("2026-10-26", 1);
  for (const row of snapshot.weeks[0].rows) {
    row.total = null;
    row.days = Array(7).fill(null);
    row.status = "UNAVAILABLE";
  }
  snapshot.weeks[0].targets = [];
  snapshot.weeks[0].wastage = { lines: 0, valuedLines: 0, value: null, reasonCoverageLines: null, reasonCoverageValue: null, reasons: [] };
  snapshot.weeks[0].stockCounts = [];
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" } });
    return route.fulfill({ json: path === "/api/reports/weekly" ? snapshot : [] });
  });
  await page.goto("/weekly-report?from=2026-10-26&weeks=1");
  await expect(page.getByText("85 / 85 fields unavailable", { exact: true })).toBeVisible();
  const summary = page.getByRole("region", { name: "Selected week summary" });
  await expect(summary.getByText("—", { exact: true })).toHaveCount(3);
  await expect(summary.getByText("$0.00", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("table", { name: "Effective weekly targets", exact: true }).getByText("Actual unavailable", { exact: true })).toHaveCount(6);
  await page.locator("summary").filter({ hasText: "Wastage reasons & stock counts" }).click();
  await expect(page.getByText("No wastage observations received for this week. This does not establish zero wastage.", { exact: true })).toBeVisible();
  await expect(page.getByText("No stock-count observations received for this week.", { exact: true })).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("weekly-report-missing.png"), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByLabel("First week (Monday)", { exact: true }).fill("2026-10-19");
  await expect(page).toHaveURL(/from=2026-10-19&weeks=1/);
  await page.getByLabel("Number of weeks", { exact: true }).selectOption("2");
  await expect(page).toHaveURL(/from=2026-10-19&weeks=2/);
});
