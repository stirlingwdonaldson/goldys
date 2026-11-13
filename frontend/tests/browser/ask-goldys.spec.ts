import { test, expect } from "@playwright/test";

const notices = [
  "Query 1: Incomplete subtotal: 5 observed days, 2 days without a resolved record; expected trading population is unverified.",
  "Query 1: Gross includes GST. Trading policy: 02:00 Australia/Melbourne every day (simplified).",
];

test("chat retains exchanges, collapses notes and starts a new thread only on request", async ({ page }) => {
  const requests: { threadId: string; message: string }[] = [];
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture Owner", department: "ALL", seniority: "OWNER" } });
    if (path === "/api/dashboard/summary") return route.fulfill({ json: { openConflicts: 0 } });
    return route.fulfill({ json: [] });
  });
  await page.route("**/api/conversational/chat", async route => {
    requests.push(route.request().postDataJSON());
    const number = requests.length;
    await new Promise(resolve => setTimeout(resolve, 400));
    await route.fulfill({ contentType: "text/event-stream", body:
      `event: text\ndata: ${JSON.stringify({ delta: `Reporting answer ${number}` })}\n\n` +
      `event: answer\ndata: ${JSON.stringify({ widgets: [{ schemaVersion: 2, id: `sales-${number}`, type: "stat", title: `Gross sales ${number}`, value: number === 1 ? 27650.66 : 4150.25, format: "currency" }], trace: [], asOf: "2026-11-12T00:00:00Z", notices })}\n\n`,
    });
  });
  await page.goto("/settings");
  await page.getByRole("button", { name: "Ask Goldy's (⌘J)" }).click();
  await expect(page.getByRole("button", { name: "What were sales last week?", exact: true })).toBeVisible();
  const input = page.getByLabel("Reporting question", { exact: true });
  await input.fill("Sales last week?");
  await input.press("Enter");
  await expect(page.getByRole("button", { name: "What were sales last week?", exact: true })).toHaveCount(0);
  await expect(page.getByText("Sales last week?", { exact: true })).toBeVisible();
  await expect(page.getByText("Reporting answer 1", { exact: true })).toBeVisible();
  const notes = page.getByRole("button", { name: "Query notes (2)" }).first();
  await expect(notes).toHaveAttribute("aria-expanded", "false");
  await notes.hover();
  await expect(page.getByRole("tooltip")).toContainText("Incomplete subtotal");
  await notes.focus();
  await notes.press("Enter");
  await expect(notes).toHaveAttribute("aria-expanded", "true");
  await expect(page.getByText(notices[1], { exact: true })).toBeVisible();
  await expect(page.getByRole("tooltip")).toHaveCount(0);
  await notes.press("Enter");
  await expect(page.getByText(notices[1], { exact: true })).toHaveCount(0);

  await input.fill("And yesterday?");
  await input.press("Enter");
  await expect(page.getByText("Reporting answer 1", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "What were sales last week?", exact: true })).toHaveCount(0);
  await expect(page.getByText("Reporting answer 2", { exact: true })).toBeVisible();
  expect(requests[1].threadId).toBe(requests[0].threadId);
  // Earlier answers remain mounted, preserving their widgets and disclosure state.
  await expect(page.locator('[data-slot="message"]')).toHaveCount(4);
  await expect(page.getByText("Gross sales 1", { exact: true })).toBeVisible();
  await expect(page.getByText("Gross sales 2", { exact: true })).toBeVisible();
  await page.getByRole("dialog").getByRole("region", { name: "Conversation" }).evaluate(element => { element.scrollTop = 0; });
  await expect(page.getByRole("dialog")).toBeInViewport({ ratio: 1 });
  await expect(page.getByRole("button", { name: "Scroll to end", exact: true })).toBeInViewport({ ratio: 1 });
  await page.screenshot({ path: "/tmp/goldys-chat-desktop.png", fullPage: true });
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Ask Goldy's (⌘J)" }).click();
  await expect(page.getByText("Reporting answer 2", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Query notes (2)" }).first()).toHaveAttribute("aria-expanded", "false");
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("dialog")).toBeInViewport({ ratio: 1 });
  await expect(input).toBeInViewport({ ratio: 1 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: "/tmp/goldys-chat-mobile.png", fullPage: true });

  await page.getByRole("button", { name: "New chat", exact: true }).click();
  await expect(page.getByText("Reporting answer 1", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "What were sales last week?", exact: true }).click();
  await expect(page.getByText("Reporting answer 3", { exact: true })).toBeVisible();
  expect(requests[2].threadId).not.toBe(requests[0].threadId);
});
