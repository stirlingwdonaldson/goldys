import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "true"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", route => route.fulfill({ json:
    new URL(route.request().url()).pathname === "/api/me"
      ? { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" }
      : [] }));
});

test("qualifications survive the old hidden preference, reload and navigation", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("goldys-show-incomplete-notices", "false"));
  await page.goto("/kitchen");
  await expect(page.getByText(/Incomplete example observations/)).toBeVisible();
  await page.reload();
  await expect(page.getByText(/Incomplete example observations/)).toBeVisible();
  await page.goto("/settings");
  await expect(page.getByRole("heading", { name: "Settings", exact: true })).toBeVisible();
  await expect(page.getByRole("switch", { name: "Show incomplete data notices" })).toHaveCount(0);
  await page.goto("/kitchen");
  await expect(page.getByText(/Incomplete example observations/)).toBeVisible();
});

test("kitchen observations use shared controls and table", async ({ page }) => {
  await page.goto("/kitchen");
  const from = page.getByRole("button", { name: "Observation from", exact: true });
  await expect(from).toBeVisible();
  await from.click();
  await expect(page.getByRole("grid")).toBeVisible();
  await page.keyboard.press("Escape");
  const kind = page.getByRole("combobox", { name: "Observation kind", exact: true });
  await kind.click();
  await page.getByRole("option", { name: "Waste", exact: true }).click();
  await expect(kind).toHaveText("Waste");
  const observations = page.getByRole("region", { name: "Dated source observations", exact: true });
  await expect(observations.getByRole("table")).toBeVisible();
  await expect(observations.getByRole("button", { name: "Columns", exact: true })).toBeVisible();
});

for (const width of [1440, 390]) {
  test(`seven taxonomy nodes remain readable at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto("/products");
    const canvas = page.getByRole("region", { name: /Product taxonomy/ });
    const nodes = canvas.locator(".react-flow__node");
    await expect(nodes).toHaveCount(7);
    await expect.poll(async () => {
      const box = await nodes.first().boundingBox();
      return box?.width ?? 0;
    }).toBeGreaterThan(130);
    const bounds = await canvas.boundingBox();
    for (const node of await nodes.all()) {
      const box = await node.boundingBox();
      expect(box!.x).toBeGreaterThanOrEqual(bounds!.x);
      expect(box!.x + box!.width).toBeLessThanOrEqual(bounds!.x + bounds!.width);
      expect(box!.y).toBeGreaterThanOrEqual(bounds!.y);
      expect(box!.y + box!.height).toBeLessThanOrEqual(bounds!.y + bounds!.height);
    }
    await canvas.screenshot({ path: `/tmp/goldys-taxonomy-${width}.png` });
    await canvas.getByText("Beer", { exact: true }).click();
    await expect(page.getByRole("heading", { name: "Beer", exact: true })).toBeVisible();
  });
}
