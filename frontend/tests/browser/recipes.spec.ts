import { test, expect, type Page } from "@playwright/test";
import observations from "./fixtures/recipe-observations.json";
import type {
  RecipeComposition,
  RecipeDetails,
  RecipeHeader,
} from "../../lib/api/types";

function composition(id: number): RecipeComposition | null {
  const row = observations.find((r) => r.recipeId === id);
  if (!row) return null;
  const header: RecipeHeader = {
    recipeId: row.recipeId,
    name: row.name,
    portionCount: row.portionCount,
    portionSize: row.portionSize,
    unit: row.unit,
    totalCost: row.totalCost,
    folderId: null,
    active: null,
    locked: null,
    unitId: null,
    costPerPortion: null,
    costPerPortionIncludingLabour: null,
    labourCost: null,
    sellingPriceIncludingGst: null,
    revenueExcludingGst: null,
    gst: null,
    foodCostPercent: null,
    grossProfit: null,
    description: null,
    menuDescription: null,
    hints: null,
    preparationMinutes: null,
    productionItem: null,
    countableOnStocktake: null,
    videoUrl: null,
    recipeDate: null,
    createdByName: null,
    difficultyRating: null,
    barcode: null,
    stockCategoryId: null,
    foodCostLimit: null,
    foodCostLimitIncludingLabour: null,
  };
  return {
    definition: {
      header,
      stocks: row.stocks,
      usages: row.usages,
      observedAt: "2026-10-23T18:00:00Z",
    },
    folderPath: ["Browser recipe fixtures"],
    outlets: [],
    sourceComponentTotal: null,
    calculatedTotal: null,
    difference: null,
    issues: [],
    stale: false,
  };
}

async function isolate(page: Page, demo = false) {
  await page.addInitScript(
    (value) => localStorage.setItem("goldys-demo-mode", value),
    String(demo),
  );
  await page.route("**/monitoring**", (route) =>
    route.fulfill({ status: 204 }),
  );
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    let json: unknown = [];
    if (path === "/api/me")
      json = {
        displayName: "Fixture Owner",
        department: "ALL",
        seniority: "OWNER",
      };
    else if (path === "/api/recipes")
      json = observations.map((r) => ({
        recipeId: r.recipeId,
        name: r.name,
        folder: "Browser recipe fixtures",
        portionSize: `${r.portionCount} × ${r.portionSize} ${r.unit}`,
        costPerPortion: null,
        menuPrice: null,
        priceSource: "NONE",
        coverage: "COMPLETE",
        stale: false,
      }));
    else if (/\/api\/recipes\/\d+\/composition$/.test(path))
      json = composition(Number(path.split("/")[3]));
    else if (/\/api\/recipes\/\d+\/details$/.test(path)) {
      const id = Number(path.split("/")[3]);
      const row = observations.find((r) => r.recipeId === id);
      json = row
        ? ({
            recipeId: id,
            name: row.name,
            costSelection: "Captured CTB composition",
            sections: [],
            latestAttempts: [],
            usedIn: observations
              .filter((r) => r.usages.some((u) => u.recipeId === id))
              .map((r) => ({
                recipeId: r.recipeId,
                name: r.name,
                available: true,
              })),
            uses: row.usages.map((u) => ({
              recipeId: u.recipeId,
              name: u.name,
              available: true,
            })),
            compositionSources: [],
            collectionEnabled: true,
          } satisfies RecipeDetails)
        : null;
    }
    // No browser request reaches a real backend.
    await route.fulfill({ json });
  });
}

async function noWrap(page: Page) {
  const cells = page.locator("th, td");
  await expect(cells.first()).toBeVisible();
  expect(
    await cells.evaluateAll((elements) =>
      elements.every((cell) => getComputedStyle(cell).whiteSpace === "nowrap"),
    ),
  ).toBe(true);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect(
    await page.locator("table").evaluateAll((tables) =>
      tables.every((table) => {
        const container = table.parentElement!;
        return (
          getComputedStyle(container).overflowX === "auto" &&
          container.clientWidth <= innerWidth
        );
      }),
    ),
  ).toBe(true);
  // Text node rectangles detect actual soft wrapping, not just a CSS class.
  expect(
    await cells.evaluateAll((elements) =>
      elements.every((cell) => {
        const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
        while (walker.nextNode()) {
          const node = walker.currentNode;
          if (
            !node.textContent?.trim() ||
            !node.parentElement?.getClientRects().length
          )
            continue;
          const range = document.createRange();
          range.selectNodeContents(node);
          const tops = new Set(
            [...range.getClientRects()]
              .filter((r) => r.width > 0)
              .map((r) => Math.round(r.top)),
          );
          if (tops.size > 1) return false;
        }
        return true;
      }),
    ),
  ).toBe(true);
}

for (const width of [1440, 390]) {
  test(`recipes show real composition and parent links at ${width}px`, async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await isolate(page);
    await page.setViewportSize({ width, height: 1000 });
    await page.goto("/recipes?search=Fairy&folder=Browser+recipe+fixtures");
    await expect(page.getByRole("columnheader")).toHaveCount(6);
    await noWrap(page);
    await page
      .getByRole("link", { name: "Fairy Bread Pudding", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Fairy Bread Pudding", exact: true }),
    ).toBeVisible();
    await expect(page.getByText("200 grams", { exact: true })).toBeVisible();
    await expect(page.getByText("50 ml", { exact: true })).toBeVisible();
    await noWrap(page);
    await expect(page.locator("#source-details")).not.toHaveAttribute("open");
    await page
      .getByRole("link", { name: "Vanilla custard", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Vanilla custard", exact: true }),
    ).toBeVisible();
    await page
      .getByRole("link", { name: /Fairy Bread Pudding · #401/ })
      .click();
    await expect(
      page.getByRole("heading", { name: "Fairy Bread Pudding", exact: true }),
    ).toBeVisible();
    await page.goBack();
    await page.goBack();
    await page.goBack();
    await expect(page.getByLabel("Search recipes")).toHaveValue("Fairy");
    await page.goto("/recipes/314");
    await expect(
      page.getByText("BELVEDERE PURE VODKA : 700 ml", { exact: true }),
    ).toBeVisible();
    await expect(page.locator("tbody tr")).toHaveCount(3);
    await noWrap(page);
    await page
      .getByRole("button", { name: "Stock & supplier" })
      .first()
      .click();
    await expect(page.getByRole("dialog")).toContainText("Oranges & Lemons");
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await page.screenshot({
      path: `/tmp/goldys-recipes-detail-${width}.png`,
      fullPage: true,
    });
    await page.goto("/recipes/485");
    await expect(
      page.getByRole("link", { name: "Eggplant Parma", exact: true }),
    ).toBeVisible();
    await expect(page.locator("tbody tr")).toHaveCount(1);
    expect(errors).toEqual([]);
  });

  test(`shared tables stay single-line and contained at ${width}px`, async ({
    page,
  }) => {
    await isolate(page, true);
    await page.setViewportSize({ width, height: 1000 });
    for (const path of [
      "/recipes",
      "/kitchen",
      "/sales",
      "/logs",
      "/data?layer=raw",
      "/invoices/id/10000000-0000-0000-0000-000000000001",
    ]) {
      await page.goto(path);
      await expect(page.locator("table").first()).toBeVisible();
      await noWrap(page);
    }
    await page.goto("/forecasting");
    await page
      .getByRole("button", { name: "Run forecast", exact: true })
      .click();
    await expect(page.locator("table").first()).toBeVisible();
    await noWrap(page);
    await page.goto("/recipes/3");
    await expect(
      page.getByRole("link", { name: "Burger sauce", exact: true }),
    ).toBeVisible();
    await page.getByRole("link", { name: "Burger sauce", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "Burger sauce", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: /Cheeseburger · #3/ }),
    ).toBeVisible();
  });
}
