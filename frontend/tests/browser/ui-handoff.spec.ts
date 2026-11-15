import { test, expect, type Page } from "@playwright/test";

const summary = { ingestionCompleteness: 100, openConflicts: 2, timeToDetectFailure: "Unknown", overrideUsage: { count: 0, period: "this week" } };
const empty = { items: [], total: 0, page: 0, size: 25 };
const bootstrap = { summary, latestSales: { date: "2026-11-15", total: 150 }, salesTrend: { points: [{ date: "2026-11-07", total: 500 }, { date: "2026-11-08", total: 100 }, { date: "2026-11-14", total: null }, { date: "2026-11-15", total: 150 }], notices: ["Incomplete subtotal: one unresolved date", "Net sales exclude GST"], provenance: { range: { from: "2026-11-07", to: "2026-11-15" } } }, topSellers: { items: [{ name: "Fixture beer", quantitySold: 493, amount: 7904.25, hasConflict: false }], notices: [] }, coversToday: { covers: 10, bookings: 2, noShows: 0 }, foodCost: { purchases: null, wastage: null } };
async function isolate(page: Page) {
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    let json: unknown = [];
    if (path === "/api/me") json = { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" };
    if (path === "/api/dashboard/summary") json = summary;
    if (path === "/api/dashboard/bootstrap") json = bootstrap;
    if (path.includes("/exceptions")) json = empty;
    if (path === "/api/reconciliation/recompute/status") json = { status: "IDLE" };
    if (path === "/api/reviews/overview") json = { total: 6, averageRating: 4, positive: 4, neutral: 1, negative: 1, wouldRecommendPct: 90 };
    return route.fulfill({ json });
  });
}

test("Home puts attention first, shares connector reads, and exposes exact dates and isolated actuals", async ({ page }) => {
  await isolate(page);
  let reads = 0;
  await page.route("**/api/connectors", route => { reads++; return route.fulfill({ json: [] }); });
  await page.goto("/dashboard");
  await expect(page.getByText("50% vs Sun, 8 Nov")).toBeVisible();
  await expect(page.getByText("493 sold · $7,904.25 revenue")).toBeVisible();
  await expect.poll(() => reads).toBe(1);
  const attention = page.getByRole("link", { name: /Review|My work/ }).first();
  const sales = page.getByText("$150.00", { exact: true }).first();
  expect((await attention.boundingBox())!.y).toBeLessThan((await sales.boundingBox())!.y);
  await expect(page.locator(".recharts-area-dot")).toHaveCount(3);
  await page.getByRole("link", { name: "My work", exact: true }).first().click();
  await expect.poll(() => reads).toBe(1);
});

for (const width of [1440, 390]) test(`issue summary, details, keyboard, history and focus at ${width}px`, async ({ page }) => {
  await isolate(page);
  await page.setViewportSize({ width, height: 900 });
  await page.goto("/dashboard");
  const trigger = page.getByRole("button", { name: "Net sales coverage: incomplete — view issue", exact: true });
  await expect(trigger).toBeVisible();
  await expect(page.getByText("Net sales exclude GST", { exact: true })).toBeVisible();
  await trigger.hover();
  const summaryLink = page.getByRole("button", { name: "View full issue for Net sales coverage" });
  await expect(summaryLink).toBeVisible();
  await summaryLink.click();
  await expect(page.getByRole("dialog")).toContainText("2026-11-07 to 2026-11-15");
  await expect(summaryLink).toHaveCount(0);
  await page.goBack();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.reload();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
  await expect(page).not.toHaveURL(/issue=/);
  await page.screenshot({ path: `/tmp/goldys-handoff-home-${width}.png`, fullPage: true });
});

test("invoice typing survives latency and errors; sorting, pages and full-detail return retain scope", async ({ page }) => {
  await isolate(page);
  const invoices = Array.from({ length: 80 }, (_, i) => ({ invoiceNumber: `INV-${String(i).padStart(3, "0")}`, supplierName: "Paramount Liquor", invoiceDate: "2026-10-01", dueDate: null, totalAmount: i === 79 ? 9999 : i, purchaseNumber: null, hasPdf: false }));
  await page.route("**/api/invoices?*", async route => {
    const query = new URL(route.request().url()).searchParams;
    await new Promise(resolve => setTimeout(resolve, 150));
    if (query.get("supplier") === "failure") return route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Invoice fixture unavailable" } });
    const sort = query.get("sort"), direction = query.get("direction");
    const sorted = [...invoices].sort((a, b) => sort === "totalAmount" ? (a.totalAmount - b.totalAmount) * (direction === "desc" ? -1 : 1) : a.invoiceNumber.localeCompare(b.invoiceNumber));
    const index = Number(query.get("page")) * Number(query.get("size"));
    return route.fulfill({ json: { ...empty, items: sorted.slice(index, index + Number(query.get("size"))), total: 80 } });
  });
  await page.route("**/api/invoices/INV-*", route => route.fulfill({ json: { ...invoices.find(row => route.request().url().includes(row.invoiceNumber)), lines: [], notices: [] } }));
  await page.goto("/invoices?from=2026-01-01&to=2026-12-31");
  const supplier = page.getByRole("textbox", { name: "Supplier", exact: true });
  await supplier.pressSequentially("Paramount Liquor", { delay: 25 });
  await expect(supplier).toHaveValue("Paramount Liquor");
  await expect(supplier).toBeFocused();
  await expect(page.getByRole("button", { name: "Sort by Total", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Sort by Total", exact: true }).click();
  await expect(page.locator('th[aria-sort="ascending"]')).toHaveText(/Total/);
  await page.getByRole("button", { name: "Sort by Total", exact: true }).click();
  await expect(page.locator('th[aria-sort="descending"]')).toHaveText(/Total/);
  await expect(page.getByText("$9,999.00")).toBeVisible();
  await page.getByRole("button", { name: /Open invoice INV-079/ }).click();
  await page.getByRole("link", { name: "Expand to full page" }).click();
  await page.getByRole("link", { name: "Invoices", exact: true }).last().click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(supplier).toHaveValue("Paramount Liquor");
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await page.reload();
  await expect(page).toHaveURL(/page=1/);
  await expect(page.locator('th[aria-sort="descending"]')).toHaveText(/Total/);
  await supplier.fill("failure");
  await expect(page.getByRole("button", { name: "Retry Supplier invoices" })).toBeVisible();
  await supplier.fill("Paramount");
  await expect(page.getByRole("button", { name: "Sort by Total", exact: true })).toBeVisible();
});

for (const endpoint of ["/api/reviews", "/api/reviews/sentiment-trend", "/api/reviews/rating-trend", "/api/reviews/aspects", "/api/reviews/ratings", "/api/reviews/sources"]) test(`Reviews isolates failure of ${endpoint}`, async ({ page }) => {
  await isolate(page);
  await page.route(`**${endpoint}?*`, route => route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Independent fixture failure" } }));
  await page.goto("/reviews");
  await expect(page.getByText("Total reviews", { exact: true })).toBeVisible();
  await expect(page.getByText("6", { exact: true })).toBeVisible();
  await expect(page.getByRole("alert").filter({ hasText: "unavailable" })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Retry / })).toBeVisible();
});

test("rule history and recompute status fail independently", async ({ page }) => {
  await isolate(page);
  for (const endpoint of ["/api/reconciliation/rules/audit", "/api/reconciliation/recompute/status"]) await page.route(`**${endpoint}`, route => route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Independent fixture failure" } }));
  await page.goto("/resolution-rules");
  await expect(page.getByRole("button", { name: "Retry Change history" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Retry Recompute status" })).toBeVisible();
  await expect(page.getByText("No changes yet")).toHaveCount(0);
});

for (const route of ["/dashboard", "/weekly-report", "/invoices"]) test(`skip navigation focuses route content on ${route}`, async ({ page }) => {
  await isolate(page);
  await page.goto(route);
  await expect(page.getByRole("link", { name: "Skip to main content" })).toBeAttached();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.locator("#main-content")).toBeFocused();
});

for (const points of [[{ date: "2026-11-15", total: 0 }], [{ date: "2026-11-11", total: null }, { date: "2026-11-12", total: 20 }, { date: "2026-11-13", total: null }, { date: "2026-11-14", total: 0 }, { date: "2026-11-15", total: null }]]) test(`isolated chart observations stay visible (${points.length} dates)`, async ({ page }) => {
  await isolate(page);
  await page.route("**/api/dashboard/bootstrap", route => route.fulfill({ json: { ...bootstrap, salesTrend: { ...bootstrap.salesTrend, points } } }));
  await page.goto("/dashboard");
  const dots = page.locator(".recharts-area-dot");
  await expect(dots).toHaveCount(points.filter(point => point.total !== null).length);
  await expect(dots.first()).toHaveAttribute("fill-opacity", "1");
});

test("raw and log disclosures work with Enter and Space", async ({ page }) => {
  await isolate(page);
  await page.route("**/api/connectors", route => route.fulfill({ json: [{ source: "LIGHTSPEED", status: "FAILED", failure: { type: "Fixture", message: "Fixture failure", stackTrace: "Fixture stack evidence" }, lastSuccess: null }] }));
  await page.goto("/logs");
  const log = page.getByRole("button", { name: /Show .* log details/ });
  await log.focus(); await page.keyboard.press("Enter");
  await expect(log).toHaveAttribute("aria-expanded", "true");
  await expect(page.getByText("Fixture stack evidence", { exact: true })).toBeVisible();
  await page.keyboard.press("Space"); await expect(log).toHaveAttribute("aria-expanded", "false");
  await page.route("**/api/data/raw?*", route => route.fulfill({ json: { ...empty, total: 1, items: [{ id: "fixture-raw", sourceSystem: "CTB", fetcherName: "fixture", receivedAt: "2026-11-15T00:00:00Z", payload: { evidence: "Raw fixture evidence" } }] } }));
  await page.route("**/api/data/raw/fixture-raw", route => route.fulfill({ json: { payload: '{"evidence":"Raw fixture evidence"}', isJson: true, sha256: "fixture" } }));
  await page.goto("/data?layer=raw");
  const raw = page.getByRole("button", { name: "Raw record fixture-raw from CTB" });
  await raw.focus(); await page.keyboard.press("Space");
  await expect(raw).toHaveAttribute("aria-expanded", "true");
  await expect(page.locator('[id="raw-fixture-raw"]')).toBeVisible();
  await page.keyboard.press("Enter"); await expect(raw).toHaveAttribute("aria-expanded", "false");
});

test("reduced motion retains issue visibility, focus and high-contrast indicator", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await isolate(page); await page.goto("/dashboard");
  const trigger = page.getByRole("button", { name: "Net sales coverage: incomplete — view issue", exact: true });
  await trigger.focus(); await page.keyboard.press("Enter");
  const dialog = page.getByRole("dialog"); await expect(dialog).toBeVisible();
  expect(await dialog.evaluate(el => getComputedStyle(el).animationName)).toBe("none");
  await page.keyboard.press("Escape"); await expect(trigger).toBeFocused();
  expect(await trigger.evaluate(el => getComputedStyle(el).boxShadow)).not.toBe("none");
  await page.screenshot({ path: "/tmp/goldys-focus-reduced-motion.png" });
});

test("Sales detail restores the selected receipt and reporting scope through history and reload", async ({ page }) => {
  await isolate(page);
  const reads: URL[] = [];
  await page.route("**/api/payments?*", route => {
    reads.push(new URL(route.request().url()));
    return route.fulfill({ json: { ...empty, total: 1, items: [{ tradingDate: "2026-10-01", saleNumber: "123", amount: 20, tip: 0, tendered: 20, surcharge: 0, paymentCount: 1, reconciled: "YES", paymentTypeName: "Card" }] } });
  });
  for (const endpoint of ["sale-items", "deleted-sales"]) await page.route(`**/api/${endpoint}?*`, route => route.fulfill({ json: empty }));
  await page.route("**/api/sale-items/receipt-group?*", route => route.fulfill({ json: { association: "TRANSACTION_IDENTITY_UNVERIFIED", transactions: [] } }));
  await page.goto("/sales-detail?tab=payments&from=2026-10-01&to=2026-10-02");
  const trigger = page.getByRole("button", { name: "View sale 123", exact: true });
  await trigger.click();
  await expect(page.getByRole("dialog")).toContainText("2026-10-01 to 2026-10-02");
  await expect.poll(() => reads.some(url => url.searchParams.get("saleNumber") === "123" && url.searchParams.get("from") === "2026-10-01")).toBe(true);
  await page.goBack(); await expect(trigger).toBeFocused();
  await page.goForward(); await expect(page.getByRole("dialog")).toBeVisible();
  await page.reload(); await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape"); await expect(page).not.toHaveURL(/sale=/);
  await page.getByRole("tab", { name: "Sale items", exact: true }).click();
  await page.reload(); await expect(page.getByRole("tab", { name: "Sale items", exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(page).toHaveURL(/from=2026-10-01/);
});

for (const width of [1440, 390]) test(`weekly target spacing survives 200% text at ${width}px`, async ({ page }) => {
  await isolate(page); await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "true"));
  await page.setViewportSize({ width, height: 900 });
  await page.goto("/weekly-report?from=2026-10-19&weeks=2");
  const table = page.getByRole("table", { name: "Effective weekly targets", exact: true });
  await expect(table).toBeVisible();
  await page.addStyleTag({ content: "html { font-size: 200% !important; }" });
  const notes = table.getByRole("button", { name: /Source notes/ }).first();
  await notes.focus(); await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape"); await expect(notes).toBeFocused();
  await page.screenshot({ path: `/tmp/goldys-targets-200-${width}.png`, fullPage: true });
});

test("rules preserve usable main content when history is denied and product options fail", async ({ page }) => {
  await isolate(page);
  await page.route("**/api/reconciliation/rules/audit", route => route.fulfill({ status: 403, json: { code: "NOT_PERMITTED", message: "Denied" } }));
  await page.route("**/api/reconciliation/products", route => route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Offline" } }));
  await page.goto("/resolution-rules");
  await expect(page.getByRole("heading", { name: "You don't have access to Change history" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Retry Product options" })).toBeVisible();
  await expect(page.getByRole("button", { name: "New rule", exact: true })).toBeVisible();
  await expect(page.getByText("No changes yet", { exact: true })).toHaveCount(0);
});
