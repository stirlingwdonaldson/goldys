import { test, expect, type Page } from "@playwright/test";

const empty = { items: [], total: 0, page: 0, size: 10 };
const day = (index: number) => new Date(Date.UTC(2026, 9, 24 - index)).toISOString().slice(0, 10);

async function isolate(page: Page) {
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" } });
    if (path === "/api/dashboard/summary") return route.fulfill({ json: { openConflicts: 0 } });
    if (path === "/api/connectors") return route.fulfill({ json: [] });
    if (path.includes("/reconciliation/") && !path.includes("records/")) return route.fulfill({ json: empty });
    if (path === "/api/sales/daily") {
      const url = new URL(route.request().url());
      const index = Number(url.searchParams.get("page")) * Number(url.searchParams.get("size"));
      return route.fulfill({ json: { items: Array.from({ length: 10 }, (_, i) => ({ date: day(index + i), source: "CTB", totalSales: 100, gst: 10, net: 90 })), total: 30, page: index / 10, size: 10 } });
    }
    if (path === "/api/dashboard/sales-trend") return route.fulfill({ json: { points: [], notices: ["Incomplete sales subtotal"], provenance: { range: { from: "2026-10-01", to: "2026-10-24" } } } });
    if (path === "/api/reservations/summary") return route.fulfill({ json: { date: "2026-10-24", bookings: 10, attended: 8, covers: 24, cancelled: 1, noShows: 1, walkIns: 2, avgPartySize: 3, noShowRate: 0.1, bookingToCoverConversion: 0.8 } });
    if (path === "/api/reservations/covers") return route.fulfill({ json: Array.from({ length: 25 }, (_, i) => ({ date: day(i), covers: 24, authoritativeSource: "OPENTABLE", hasConflict: false })) });
    if (path.startsWith("/api/provenance/")) {
      const parts = path.split("/");
      return route.fulfill({ json: { metric: parts[3], date: parts[4], resolvedValue: 100,
        trust: { state: "SINGLE_SOURCE", freshness: "UNKNOWN", authoritativeSource: "CTB", resolvedAt: null, lastIngestionAt: null, threshold: null },
        sources: [{ sourceSystem: "CTB", value: 100, recordedAt: null }],
        resolution: { kind: "single_source", source: "CTB", reason: null, actor: null, at: null }, rawRecordIds: [],
      } });
    }
    return route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Fixture unavailable" } });
  });
}

for (const kind of ["daily", "product"]) {
  test(`${kind} exceptions beyond 200 are reachable with page-scoped filters`, async ({ page }) => {
    await isolate(page);
    const requests: string[] = [];
    const endpoint = kind === "daily" ? "/api/reconciliation/exceptions" : "/api/reconciliation/products/exceptions";
    await page.route(`**${endpoint}?*`, route => {
      requests.push(route.request().url());
      const url = new URL(route.request().url());
      const pageIndex = Number(url.searchParams.get("page"));
      const size = Number(url.searchParams.get("size"));
      const all = Array.from({ length: 201 }, (_, i) => ({ id: `${day(i)}:${kind === "product" ? `product-${i}` : "daily_sales"}`,
        recordId: kind === "product" ? `product-${i}` : day(i), entity: kind, field: "amount", status: i === 200 ? "missing" : "conflict",
        sources: [{ source: "CTB", value: "100" }, { source: "LIGHTSPEED", value: i === 200 ? null : "99" }],
      }));
      return route.fulfill({ json: { items: all.slice(pageIndex * size, (pageIndex + 1) * size), total: 201, page: pageIndex, size } });
    });
    await page.goto(`/reconciliation?tab=${kind}`);
    await expect(page.getByText("201 open exceptions", { exact: true })).toBeVisible();
    await expect(page.getByText(/Status counts and search apply to this page only/)).toBeVisible();
    await page.getByRole("radio", { name: /^Missing / }).click();
    await expect(page.getByText(/No matches on this page/)).toBeVisible();
    await page.getByRole("button", { name: "Last page", exact: true }).click();
    await expect(page).toHaveURL(/page=20/);
    await expect(page.getByRole("button", { name: /^Review / })).toHaveCount(1);
    await expect(page.getByText("201–201 of 201", { exact: true })).toBeVisible();
    expect(requests.length).toBeLessThanOrEqual(3);
    expect(requests.every(url => Number(new URL(url).searchParams.get("size")) <= 100)).toBe(true);
    await page.reload();
    await expect(page.getByRole("button", { name: /^Review / })).toHaveCount(1);
  });
}

for (const status of [403, 503]) {
  test(`failed product check ${status} never becomes an empty success`, async ({ page }) => {
    await isolate(page);
    await page.route("**/api/reconciliation/products/exceptions?*", route => route.fulfill({ status, json: { code: status === 403 ? "NOT_PERMITTED" : "UNAVAILABLE", message: "Product check unavailable" } }));
    await page.goto("/reconciliation?tab=product");
    await expect(page.getByText("Checks unavailable", { exact: true })).toBeVisible();
    await expect(page.getByText("All reconciled", { exact: true })).toHaveCount(0);
    if (status === 403) await expect(page.getByText(/You don't have access to/)).toBeVisible();
    else await expect(page.getByRole("button", { name: "Retry", exact: true })).toBeVisible();
  });
}

for (const report of ["sales", "reservations"]) {
  test(`${report} evidence preserves parent scope across history, reload and close`, async ({ page }) => {
    await isolate(page);
    await page.goto(`/${report}?page=1&size=10${report === "sales" ? "&source=CTB" : ""}`);
    const trace = page.getByRole("button", { name: /^Trace / }).first();
    const name = await trace.getAttribute("aria-label");
    await trace.click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page).toHaveURL(/trace=2026-10-14/);
    await page.goBack();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(page.getByRole("button", { name: name!, exact: true })).toBeFocused();
    await page.goForward();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.reload();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    expect(new URL(page.url()).searchParams.get("page")).toBe("1");
    expect(new URL(page.url()).searchParams.has("trace")).toBe(false);
    if (report === "sales") await expect(page.getByLabel("Filter sources on this page")).toHaveValue("CTB");
    await expect(page.getByRole("button", { name: name!, exact: true })).toBeVisible();
  });
}

test("evidence retry recovers without changing the selected date", async ({ page }) => {
  await isolate(page);
  let attempts = 0;
  await page.route("**/api/provenance/sales.gross/2026-10-24", route => {
    attempts++;
    return route.fulfill({ status: attempts === 1 ? 503 : 403, json: { code: attempts === 1 ? "UNAVAILABLE" : "NOT_PERMITTED", message: "Selected evidence unavailable" } });
  });
  await page.goto("/sales?metric=sales.gross&trace=2026-10-24");
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.getByText(/You don't have access to/)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Gross sales · 2026-10-24" })).toBeVisible();
  expect(attempts).toBe(2);
});

test("report scope and task help explain the existing measures", async ({ page }) => {
  await isolate(page);
  await page.goto("/sales");
  await expect(page.getByText(/2026-10-01 to 2026-10-24/)).toBeVisible();
  await expect(page.getByText(/not restricted to the trend range above/)).toBeVisible();
  await page.getByText("How to read sales and trace a figure", { exact: true }).click();
  await expect(page.getByText(/do not add competing sources together/)).toBeVisible();
  await page.goto("/reservations");
  await expect(page.getByText(/Summary for 2026-10-24/)).toBeVisible();
  await expect(page.getByText(/inclusive · browser calendar dates/)).toBeVisible();
  await page.getByText("How to read reservation measures", { exact: true }).click();
  await expect(page.getByText(/not a verified headcount/)).toBeVisible();
});

test("Home exposes attention work early on a phone and Recipes has a truthful menu label", async ({ page }) => {
  await isolate(page);
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "true"));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard");
  await expect(page.getByRole("link", { name: /Sales Resolved trend and source totals/ })).toBeVisible();
  const attention = page.getByRole("link", { name: "View attention work", exact: true });
  await expect(attention).toBeVisible();
  expect((await attention.boundingBox())!.y).toBeLessThan(300);
  await attention.click();
  await expect(page).toHaveURL(/my-work/);
  await page.getByRole("button", { name: "Toggle Sidebar" }).click();
  await expect(page.getByRole("button", { name: "Recipe evidence", exact: true })).toBeVisible();
  await expect(page.getByText("Coming soon", { exact: true })).toHaveCount(0);
});

for (const path of ["dashboard", "reconciliation", "sales", "reservations", "invoices"]) {
  test(`${path} fits a phone with enlarged text`, async ({ page }) => {
    await isolate(page);
    await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "true"));
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`/${path}`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await page.evaluate(() => document.documentElement.style.fontSize = "200%");
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await expect.poll(() => page.locator("main > div:last-child").evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    if (path === "invoices") {
      await expect(page.getByText(/inclusive · invoice dates/)).toBeVisible();
      await page.getByText("How to inspect invoice evidence", { exact: true }).click();
      await expect(page.getByText(/do not establish consumed food cost/)).toBeVisible();
    }
  });
}

test("long product labels remain bounded and named at enlarged phone text", async ({ page }) => {
  await isolate(page);
  const product = "Long product name with preparation and serving details ".repeat(6).trim();
  await page.route("**/api/reconciliation/products/exceptions?*", route => route.fulfill({ json: { items: [{ id: `2026-10-24:${product}`, recordId: product, entity: product, field: "amount", status: "missing", sources: [{ source: "CTB", value: "100" }, { source: "LIGHTSPEED", value: null }] }], total: 1, page: 0, size: 10 } }));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/reconciliation?tab=product");
  const review = page.getByRole("button", { name: /^Review / });
  await expect(review).toHaveAccessibleName(new RegExp(product));
  await page.evaluate(() => document.documentElement.style.fontSize = "200%");
  await expect.poll(() => page.locator("main > div:last-child").evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await review.focus();
  await expect(review).toBeFocused();
});
