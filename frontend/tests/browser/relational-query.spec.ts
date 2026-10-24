import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() =>
    localStorage.setItem("goldys-demo-mode", "true"),
  );
  await page.route("**/monitoring**", (route) =>
    route.fulfill({ status: 204 }),
  );
  await page.route("**/api/**", (route) =>
    route.fulfill({
      json:
        new URL(route.request().url()).pathname === "/api/me"
          ? {
              displayName: "Fixture Owner",
              department: "ALL",
              seniority: "OWNER",
            }
          : [],
    }),
  );
});

for (const width of [1440, 390]) {
  test(`query controls and results work at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto("/data?layer=query");
    const run = page.getByRole("button", { name: "Run query", exact: true });
    await expect(run).toBeEnabled();
    const dataset = page.getByRole("combobox", {
      name: "Dataset",
      exact: true,
    });
    await dataset.click();
    await page
      .getByRole("option", { name: "Resolved daily sales", exact: true })
      .click();
    await expect(
      page.getByLabel("Select trading_date", { exact: true }),
    ).toBeChecked();
    await expect(
      page.getByRole("combobox", {
        name: "Calculation for Trading date",
        exact: true,
      }),
    ).toHaveText("Original value — no summary");
    await page
      .getByRole("combobox", { name: "Filter field", exact: true })
      .click();
    await page
      .getByRole("option", { name: "Trading date", exact: true })
      .click();
    await page.getByLabel("Filter value", { exact: true }).fill("2026-10-01");
    await page.getByRole("button", { name: "Add filter", exact: true }).click();
    await run.click();
    await expect(page.getByRole("table")).toBeVisible();
    await expect(page.getByText(/rows returned · Refreshed/)).toBeVisible();
    const refresh = page.getByRole("switch", {
      name: "Auto-refresh every 30 seconds",
    });
    await refresh.click();
    await expect(refresh).toHaveAttribute("aria-checked", "true");
    await page
      .getByRole("spinbutton", { name: "Row limit", exact: true })
      .fill("20");
    await expect(page.getByText(/You have unapplied changes/)).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await page.screenshot({
      path: `/tmp/goldys-query-${width}.png`,
      fullPage: true,
    });
  });
}
