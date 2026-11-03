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

for (const width of [1280, 390])
  test(`review queue ranks real source exposure and refreshes after approval at ${width}px`, async ({
    page,
  }) => {
    const fs = await import("node:fs/promises");
    const before = JSON.parse(
      await fs.readFile("../backend/build/review-queue-before.json", "utf8"),
    );
    const after = JSON.parse(
      await fs.readFile("../backend/build/review-queue-after.json", "utf8"),
    );
    let saved = false;
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(() =>
      localStorage.setItem("goldys-demo-mode", "false"),
    );
    await page.route("**/monitoring**", (r) => r.fulfill({ status: 204 }));
    await page.route("**/api/**", async (r) => {
      const url = new URL(r.request().url());
      if (url.pathname === "/api/me")
        return r.fulfill({
          json: {
            displayName: "Fixture Owner",
            department: "ALL",
            seniority: "OWNER",
          },
        });
      if (url.pathname.endsWith("/taxonomy"))
        return r.fulfill({
          json: [
            {
              id: "fixture-food",
              name: "Food",
              groupName: null,
              productCount: 1,
            },
          ],
        });
      if (url.pathname.endsWith("/queue")) {
        expect(url.searchParams.get("from")).toBe("2026-11-02");
        expect(url.searchParams.get("to")).toBe("2026-11-02");
        return r.fulfill({ json: saved ? after : before });
      }
      if (url.pathname.endsWith("/preview"))
        return r.fulfill({
          json: {
            mapping: r.request().postDataJSON(),
            reviewable: true,
            through: "2026-11-02",
            observedRows: 1,
            knownAmount: 866.7919,
            evidence:
              "Exact CTB native site/code; independently verified source category",
            amountBasis: "Recorded gross; review date",
            notes: [],
          },
        });
      if (
        url.pathname.endsWith("/reporting-mappings") &&
        r.request().method() === "POST"
      ) {
        saved = true;
        return r.fulfill({ status: 200, body: "" });
      }
      return r.fulfill({ json: [] });
    });
    await page.goto("/products");
    await page
      .getByRole("tab", { name: "Reporting mappings", exact: true })
      .click();
    await page.getByLabel("Review from", { exact: true }).fill("2026-11-02");
    await page.getByLabel("Review to", { exact: true }).fill("2026-11-02");
    await page.getByRole("button", { name: "Refresh review queue" }).click();
    await expect(page.getByText(/Below 95%/)).toBeVisible();
    const queue = page.getByRole("region", {
      name: "Ongoing reporting review queue",
    });
    await expect(
      queue.getByRole("button", { name: /^Review site:/ }).first(),
    ).toHaveText("Review site:0:code:8:20161229");
    await queue
      .getByRole("button", {
        name: "Review site:0:code:8:20161229",
        exact: true,
      })
      .click();
    await expect(page.getByLabel(/Exact identity/)).toHaveValue(
      "site:0:code:8:20161229",
    );
    await expect(
      page.getByLabel("Effective from", { exact: true }),
    ).toHaveValue("2026-11-02");
    await expect(
      page.getByRole("button", { name: "Approve reviewed mapping" }),
    ).toHaveCount(0);
    await page.getByLabel("Sales nature").selectOption("FOOD");
    await page
      .getByLabel("Review reason and evidence")
      .fill("Source review, explicit effective date");
    await page.getByRole("button", { name: "Preview mapping impact" }).click();
    await expect(
      page.getByText(
        /Proposed identity: CTB \/ CTB_PRODUCT \/ site:0:code:8:20161229/,
      ),
    ).toBeVisible();
    expect(saved).toBe(false);
    await page
      .getByRole("button", { name: "Approve reviewed mapping" })
      .click();
    await expect(
      page.getByRole("status").filter({ hasText: "Reviewed revision saved" }),
    ).toBeVisible();
    await queue
      .getByText("Daily sales classification coverage and alerts", {
        exact: true,
      })
      .click();
    await expect(queue.getByText(/100%/)).toBeVisible();
    await expect(
      queue.getByRole("button", {
        name: "Review site:0:code:8:20161229",
        exact: true,
      }),
    ).toHaveCount(0);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  });
