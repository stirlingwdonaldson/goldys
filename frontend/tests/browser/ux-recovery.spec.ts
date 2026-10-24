import { test, expect } from "@playwright/test";

const queued = {
  id: "fc-recovery", status: "QUEUED", targetMetric: "sales.net", covariates: [],
  historyFrom: "2025-10-01", historyTo: "2026-10-01", horizonDays: 7,
  createdAt: "2026-10-01T01:00:00Z", series: null, diagnostics: null, notices: [], errorMessage: null,
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" } });
    if (path === "/api/dashboard/summary") return route.fulfill({ json: { openConflicts: 0 } });
    if (path === "/api/connectors") return route.fulfill({ json: [] });
    if (path === "/api/forecasting/metrics") return route.fulfill({ json: [{ metric: "sales.net", name: "Net sales", sourceDomain: "resolved_daily_sales", unit: "AUD" }] });
    if (path === "/api/forecasting/runs") return route.fulfill({ json: route.request().method() === "POST" ? queued : [] });
    return route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Temporarily unavailable in fixture" } });
  });
});

test("chat outage preserves the question and retries its original text", async ({ page }) => {
  const turns: { message: string; threadId: string }[] = [];
  await page.route("**/api/conversational/chat", route => {
    turns.push(route.request().postDataJSON());
    return turns.length === 1
      ? route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Reporting service temporarily unavailable" } })
      : route.fulfill({ contentType: "text/event-stream", body: 'event: text\ndata: {"delta":"Recovered reporting answer"}\n\n' });
  });
  await page.goto("/settings");
  await page.getByRole("button", { name: "Ask Goldy's (⌘J)" }).click();
  const input = page.getByLabel("Reporting question", { exact: true });
  await input.fill("Sales last week?");
  await page.getByRole("button", { name: "Ask", exact: true }).click();
  await expect(page.getByText("Couldn't answer your question", { exact: true })).toBeVisible();
  await expect(input).toHaveValue("Sales last week?");
  await expect(page.getByText("You don't have access to this data", { exact: true })).toHaveCount(0);
  await input.fill("A different draft");
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.getByText("Recovered reporting answer", { exact: true })).toBeVisible();
  expect(turns.map(turn => turn.message)).toEqual(["Sales last week?", "Sales last week?"]);
  expect(turns[1].threadId).toBe(turns[0].threadId);
});

for (const status of [401, 403]) {
  test(`chat ${status} keeps its correct recovery meaning`, async ({ page }) => {
    await page.route("**/api/conversational/chat", route => route.fulfill({ status, json: {
      code: status === 401 ? "AUTH_REQUIRED" : "NOT_PERMITTED", message: status === 401 ? "Session expired" : "This report is restricted",
    } }));
    await page.goto("/settings");
    await page.getByRole("button", { name: "Ask Goldy's (⌘J)" }).click();
    await page.getByLabel("Reporting question", { exact: true }).fill("Sales?");
    await page.getByRole("button", { name: "Ask", exact: true }).click();
    if (status === 401) await expect(page.getByRole("link", { name: "Sign in", exact: true })).toBeVisible();
    else await expect(page.getByText("You don't have access to this data", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Retry", exact: true })).toHaveCount(0);
  });
}

test("invoice detail opens with the keyboard and returns focus on Escape", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "true"));
  await page.goto("/invoices");
  const invoice = page.getByRole("button", { name: /^Open invoice / }).first();
  await invoice.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page).toHaveURL(/invoice/);
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(invoice).toBeFocused();
});

test("forecast polling failure is unknown and Retry checks the same run", async ({ page }) => {
  let reads = 0;
  let starts = 0;
  await page.route("**/api/forecasting/runs", route => {
    if (route.request().method() === "POST") { starts++; return route.fulfill({ json: queued }); }
    return route.fulfill({ json: [] });
  });
  await page.route("**/api/forecasting/runs/fc-recovery", route => {
    reads++;
    return reads === 1
      ? route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "Status check interrupted" } })
      : route.fulfill({ json: { ...queued, status: "FAILED", errorMessage: "Fixture completed after retry" } });
  });
  await page.goto("/forecasting");
  await page.getByRole("button", { name: "Run forecast", exact: true }).click();
  await expect(page.getByText(/Current status is unknown/)).toBeVisible();
  await expect(page.getByText(/Last known status; current status is unconfirmed/)).toBeVisible();
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.getByText("Fixture completed after retry", { exact: true })).toBeVisible();
  expect(starts).toBe(1);
  expect(reads).toBe(2);
});

test("shared chart values are available by keyboard in a labelled table", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "true"));
  await page.goto("/sales");
  const disclosure = page.getByText("View chart data", { exact: true });
  await disclosure.focus();
  await page.keyboard.press("Enter");
  const details = page.locator("details[open]");
  await expect(details.getByRole("table")).toBeVisible();
  await expect(details.getByRole("columnheader", { name: "Net sales (ex GST)", exact: true })).toBeVisible();
  await expect(details.getByText("Unavailable", { exact: true }).first()).toBeVisible();
});

for (const route of ["login", "signup"]) {
  test(`${route} keeps field labels visible after typing`, async ({ page }) => {
    await page.goto(`/${route}`);
    const email = page.getByLabel(route === "login" ? "Email" : "Work email", { exact: true });
    await email.fill("fixture@example.test");
    await expect(page.locator(`label[for="${await email.getAttribute("id")}"]`)).toBeVisible();
    const password = page.getByLabel("Password", { exact: true });
    await password.fill("example-only");
    await expect(page.locator(`label[for="${await password.getAttribute("id")}"]`)).toBeVisible();
  });
}
