import { test, expect } from "@playwright/test";
for (const width of [1280, 390])
  test(`review mappings without automatic approval at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(() =>
      localStorage.setItem("goldys-demo-mode", "true"),
    );
    await page.route("**/monitoring**", (r) => r.fulfill({ status: 204 }));
    await page.route("**/api/**", (r) =>
      r.fulfill({
        json:
          new URL(r.request().url()).pathname === "/api/me"
            ? {
                displayName: "Fixture Owner",
                department: "ALL",
                seniority: "OWNER",
              }
            : [],
      }),
    );
    await page.goto("/products");
    await page
      .getByRole("tab", { name: "Reporting mappings", exact: true })
      .click();
    await page.getByLabel(/Exact identity/).fill("steak");
    await page.getByLabel("Sales nature").selectOption("FOOD");
    await page
      .getByLabel("Review reason and evidence")
      .fill("Explicit source review; date scope approved");
    await expect(
      page.getByRole("button", { name: "Approve reviewed mapping" }),
    ).toHaveCount(0);
    await page.getByRole("button", { name: "Preview mapping impact" }).click();
    await expect(
      page.getByText(/Proposed identity:.*Decision: FOOD/),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Approve reviewed mapping" })
      .click();
    await expect(
      page.getByRole("status").filter({ hasText: "Reviewed revision saved" }),
    ).toBeVisible();
    await page.getByLabel("Identity scope").selectOption("CTB_PRODUCT");
    await page.getByLabel(/Exact identity/).fill("site:0:code:8:22101357");
    await page.getByRole("button", { name: "Preview mapping impact" }).click();
    await expect(
      page.getByText(
        /Proposed identity: CTB \/ CTB_PRODUCT \/ site:0:code:8:22101357/,
      ),
    ).toBeVisible();
    await page.getByRole("button", { name: "Load wastage evidence" }).click();
    await expect(
      page.getByRole("cell", { name: "No reason recorded", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Recipe 388" }),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await page.screenshot({
      path: test.info().outputPath("mapping-review.png"),
      fullPage: true,
    });
  });
