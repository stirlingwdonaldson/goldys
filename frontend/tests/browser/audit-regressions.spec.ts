import { test, expect, type Page } from "@playwright/test";

const summary = { ingestionCompleteness: 100, openConflicts: 0, timeToDetectFailure: "Unknown", overrideUsage: { count: 0, period: "this week" } };
const dashboard = {
  id: "d1", schemaVersion: 2, title: "Browser fixture", description: null, layout: "grid", widgets: [],
  filters: { dateRange: null, comparison: null, dimensions: [] }, visibility: "PRIVATE", pinned: false,
  createdBy: "Owner", createdAt: "2026-10-23T00:00:00Z", updatedAt: "2026-10-23T00:00:00Z",
};

async function isolate(page: Page) {
  await page.addInitScript(() => window.localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    let body: unknown = [];
    if (url.pathname === "/api/me") body = { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" };
    else if (url.pathname === "/api/dashboard/summary") body = summary;
    else if (url.pathname === "/api/dashboards") body = [{ ...dashboard }];
    else if (url.pathname === "/api/dashboards/d1") body = dashboard;
    else if (url.pathname === "/api/data/raw") body = { items: [], total: 0, page: Number(url.searchParams.get("page") ?? 0), size: Number(url.searchParams.get("size") ?? 10) };
    // Every API call is fulfilled locally: browser checks can never reach the production backend.
    await route.fulfill({ json: body });
  });
}

test("editor retains later edits while saving and confirms dirty exit", async ({ page }) => {
  await isolate(page);
  await page.route("**/api/dashboards/d1", async route => {
    if (route.request().method() !== "PUT") return route.fulfill({ json: dashboard });
    const input = route.request().postDataJSON();
    await new Promise(resolve => setTimeout(resolve, 500));
    await route.fulfill({ json: { ...dashboard, ...input } });
  });
  await page.goto("/dashboards");
  await page.getByRole("button", { name: "Browser fixture" }).click();
  await page.getByLabel("Title", { exact: true }).fill("Submitted");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await page.getByLabel("Title", { exact: true }).fill("Later unsaved edit");
  await expect(page.getByText("Saved.", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Later unsaved edit");
  page.once("dialog", dialog => dialog.dismiss());
  await page.getByRole("button", { name: "Library", exact: true }).click();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Later unsaved edit");
});

test("explorer applies filters once and replays them through browser history", async ({ page }) => {
  await isolate(page);
  const requests: string[] = [];
  page.on("request", request => { if (new URL(request.url()).pathname === "/api/data/raw") requests.push(request.url()); });
  await page.goto("/data?layer=raw");
  await expect(page.getByText("No raw records", { exact: true })).toBeVisible();
  const initial = requests.length;
  await page.getByLabel("Source", { exact: true }).fill("CTB");
  expect(requests.length).toBe(initial);
  await page.getByRole("button", { name: "Apply filters" }).click();
  await expect(page).toHaveURL(/source=CTB/);
  await expect.poll(() => requests.length).toBe(initial + 1);
  await page.goBack();
  await expect(page.getByLabel("Source", { exact: true })).toHaveValue("");
});

test("shared shell and data-health reads issue one request per resource", async ({ page }) => {
  await isolate(page);
  const counts: Record<string, number> = {};
  page.on("request", request => {
    const path = new URL(request.url()).pathname;
    if (["/api/connectors", "/api/dashboard/summary"].includes(path)) counts[path] = (counts[path] ?? 0) + 1;
  });
  await page.goto("/data-health");
  await expect(page.getByText("No connector data", { exact: true })).toBeVisible();
  await expect.poll(() => counts["/api/connectors"]).toBe(1);
  expect(counts["/api/dashboard/summary"]).toBe(1);
});

test("closing Ask Goldy's cancels a pending turn and prevents stale output", async ({ page }) => {
  await isolate(page);
  await page.route("**/api/conversational/chat", async route => {
    await new Promise(resolve => setTimeout(resolve, 800));
    await route.fulfill({ contentType: "text/event-stream", body: 'event: text\r\ndata: {"delta":"OLD RESPONSE"}\r\n\r\n' }).catch(() => {});
  });
  await page.goto("/dashboards");
  await page.getByRole("button", { name: "Ask Goldy's (⌘J)" }).click();
  await page.getByPlaceholder("e.g. What were sales last week?").fill("Pending question");
  await page.getByRole("button", { name: "Ask", exact: true }).click();
  await expect(page.getByText("Working…", { exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Ask Goldy's (⌘J)" }).click();
  await page.waitForTimeout(1000);
  await expect(page.getByText("OLD RESPONSE")).toHaveCount(0);
  await expect(page.getByText("Working…", { exact: true })).toHaveCount(0);
});
