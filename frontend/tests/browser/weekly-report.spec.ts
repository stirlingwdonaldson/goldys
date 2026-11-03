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
    await expect(page.getByRole("cell", { name: /^No reason recorded/ })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Stock counts", exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: "0.25%", exact: true }).first()).toBeVisible();
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
  await expect(
    page
      .getByRole("cell", { name: "Known product sales ex GST", exact: true })
      .locator(".."),
  ).toContainText("$141,527.41");
  await expect(
    page
      .getByRole("cell", { name: "Food sales ex GST", exact: true })
      .locator(".."),
  ).toContainText("$48,442.78");
  await expect(
    page
      .getByRole("cell", { name: "Beverage recorded wastage", exact: true })
      .locator(".."),
  ).toContainText("$535.86");
  await expect(
    page
      .getByRole("cell", { name: "Unclassified recorded wastage", exact: true })
      .locator(".."),
  ).toContainText("$0.00");
  for (const [label, value] of [
    ["food sales versus target", "$2,842.78"],
    ["Beverage recorded wastage / matching sales", "0.58%"],
    ["Food recorded wastage / matching sales", "0.05%"],
  ]) {
    await expect(page.getByRole("cell", { name: label, exact: true }).locator("..")).toContainText(value);
  }
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
