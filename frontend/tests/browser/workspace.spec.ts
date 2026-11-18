import { test, expect } from "@playwright/test";
import type { WorkspaceFollowUp, WorkspaceJob } from "../../lib/api/workspace";

test.skip(process.env.NEXT_PUBLIC_WORKSPACE_ENABLED !== "true" && process.env.NEXT_PUBLIC_WORKSPACE_PREVIEW !== "true", "Build with the opt-in workspace preview flag for this fixture flow.");

test("workspace survives reload, edits queued input, stops server work and hides revoked history", async ({ page }) => {
  let job: WorkspaceJob | null = null;
  const queue: WorkspaceFollowUp[] = [];
  const submissions: { threadId: string; requestId: string; prompt: string }[] = [];
  let stops = 0;
  let denied = false;
  let seniority = "OWNER";
  await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
  await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture Owner", department: "ALL", seniority } });
    if (path.startsWith("/api/workspace/") && denied) return route.fulfill({ status: 403, json: { code: "NOT_PERMITTED", message: "Access changed" } });
    if (path === "/api/workspace/threads") return route.fulfill({ json: { threads: job ? [{ id: job.threadId, createdAt: "2026-11-15T00:00:00Z", access: "AVAILABLE", latestJobId: job.id }] : [], nextCursor: null } });
    if (path === "/api/workspace/jobs" && method === "POST") {
      const input = route.request().postDataJSON();
      submissions.push(input);
      job = { id: `10000000-0000-4000-8000-00000000000${submissions.length}`, threadId: input.threadId, state: "RUNNING", prompt: input.prompt, revision: 1,
        messages: [{ id: "citation", role: "assistant", text: "Public fixture source retrieved 2026-11-16: https://www.bom.gov.au/", reporting: null }, { id: "answer", role: "assistant", text: "**Saved fixture finding**\n\n## Sales breakdown\n\n| Category | Total |\n| --- | ---: |\n| Food | $42 |\n\n" + "Supporting reporting detail. ".repeat(30), reporting: null }, { id: "widget", role: "tool", text: "", reporting: { widget: { schemaVersion: 2, type: "stat", id: "missing-sales", title: "Weekly sales", value: null, format: "currency" }, notices: ["Missing source coverage; no total is available."], provenance: [], relatedMetrics: [] } }] };
      return route.fulfill({ json: job });
    }
    if (job && path === `/api/workspace/jobs/${job.id}`) return route.fulfill({ json: job });
    if (job && path === `/api/workspace/jobs/${job.id}/stop`) {
      stops++;
      job.state = "CANCELLED";
      for (const item of queue) if (item.state === "QUEUED") item.state = "CANCELLED";
      return route.fulfill({ json: job });
    }
    if (path.endsWith("/follow-ups")) {
      if (method === "GET") return route.fulfill({ json: queue });
      const input = route.request().postDataJSON();
      const item: WorkspaceFollowUp = { id: input.requestId, prompt: input.prompt, state: "QUEUED" };
      queue.push(item);
      return route.fulfill({ json: item });
    }
    if (path.includes("/follow-ups/")) {
      const item = queue.find(item => item.id === path.split("/").at(-1))!;
      if (method === "PUT") item.prompt = route.request().postDataJSON().prompt;
      if (method === "DELETE") item.state = "CANCELLED";
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto("/settings?scope=weekly");
  if (process.env.NEXT_PUBLIC_WORKSPACE_PREVIEW !== "true") {
    await expect(page.getByText(/Isolated Pi preview/)).toHaveCount(0);
  }
  await expect(page.getByRole("link", { name: "Ask Goldy's (⌘J)" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Ask Goldy's (⌘J)", exact: true })).toHaveCount(1);
  await page.keyboard.press("Control+j");
  await expect(page).toHaveURL(/\/ask-goldys$/);
  await page.keyboard.press("Control+j");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.keyboard.press("Control+k");
  await page.getByRole("option", { name: /Ask Goldy's a question/ }).click();
  await expect(page.getByLabel("Reporting question", { exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("link", { name: "Ask Goldy's (⌘J)", exact: true }).click();
  await expect(page).toHaveURL(/\/ask-goldys$/);
  await page.screenshot({ path: "/tmp/goldys-ask-empty-desktop.png" });
  await page.getByLabel("Reporting question", { exact: true }).fill("Compare weekly sales");
  await page.getByRole("button", { name: "Send question", exact: true }).click();
  await expect(page.getByText("Saved fixture finding", { exact: true })).toBeVisible();
  if (process.env.NEXT_PUBLIC_WORKSPACE_FILES_ENABLED === "false") {
    await expect(page.getByLabel("Upload a file")).toHaveCount(0);
  }
  await expect(page.getByText("Missing source coverage; no total is available.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Sales breakdown" })).toHaveCount(0);
  await page.getByRole("button", { name: "Read full answer" }).focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: "Sales breakdown" })).toBeVisible();
  await expect(page.getByRole("table")).toContainText("$42");
  await page.getByLabel("Conversation", { exact: true }).evaluate(element => { element.scrollTop = 0; });
  await page.screenshot({ path: "/tmp/goldys-answer-expanded.png" });
  await page.getByRole("button", { name: "Show less" }).click();
  await page.getByLabel("Conversation", { exact: true }).evaluate(element => { element.scrollTop = 0; });
  await page.screenshot({ path: "/tmp/goldys-answer-compact.png" });
  await page.getByLabel("Queue a follow-up", { exact: true }).fill("Compare food sales");
  await page.getByRole("button", { name: "Queue message", exact: true }).click();
  await page.getByRole("button", { name: "Edit message", exact: true }).click();
  await page.getByLabel("Edit queued message", { exact: true }).fill("Compare beverage sales");
  await page.getByRole("button", { name: "Save message", exact: true }).click();
  await expect(page.getByText("Compare beverage sales", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Stop investigation", exact: true })).toBeInViewport();
  await page.screenshot({ path: "/tmp/goldys-workspace-desktop.png" });
  const deepLink = page.url();
  await page.goBack();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page).toHaveURL(/\/ask-goldys$/);
  expect(stops).toBe(0);
  await page.goto(deepLink);
  await expect(page.getByText("Compare beverage sales", { exact: true })).toBeVisible();
  expect(submissions).toHaveLength(1);
  await page.getByRole("button", { name: "Investigation activity (2)" }).click();
  await expect(page.getByRole("link", { name: "https://www.bom.gov.au/", exact: true })).toHaveAttribute("referrerpolicy", "no-referrer");
  await expect(page.getByText(/Public fixture source retrieved 2026-11-16/)).toBeVisible();
  await page.goto("/settings?scope=weekly");
  await expect(page).toHaveURL(/settings\?scope=weekly$/);
  expect(stops).toBe(0);
  await page.goto(deepLink);
  await expect(page.getByText("Compare beverage sales", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Remove message", exact: true }).click();
  await expect(page.getByText("Cancelled", { exact: true })).toBeVisible();
  await page.getByLabel("Queue a follow-up", { exact: true }).fill("One more question");
  await page.getByRole("button", { name: "Queue message", exact: true }).click();
  await expect(page.getByText("One more question", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Stop investigation", exact: true }).click();
  await expect(page.getByText("Stopped", { exact: true })).toBeVisible();
  expect(stops).toBe(1);
  expect(queue.every(item => item.state === "CANCELLED")).toBe(true);
  job!.state = "INTERRUPTED";
  await page.reload();
  await page.getByRole("button", { name: "Retry investigation", exact: true }).click();
  await expect.poll(() => submissions.length).toBe(2);
  expect(submissions[1].threadId).toBe(submissions[0].threadId);
  expect(submissions[1].requestId).not.toBe(submissions[0].requestId);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByText("Saved fixture finding", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Queue message", exact: true })).toBeInViewport({ ratio: 1 });
  await page.screenshot({ path: "/tmp/goldys-workspace-narrow.png" });
  await expect(page.getByLabel("Queue a follow-up", { exact: true })).toBeInViewport();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  denied = true;
  await page.reload();
  await expect(page.getByText("You don't have access to this investigation", { exact: true })).toBeVisible();
  await expect(page.getByText("Saved fixture finding", { exact: true })).toHaveCount(0);
  seniority = "MANAGER";
  await page.goto("/settings");
  await expect(page.getByRole("link", { name: "Ask Goldy's (⌘J)", exact: true })).toHaveCount(0);
  await page.goto("/ask-goldys");
  await expect(page.getByText("You don't have access to Ask Goldy's", { exact: true })).toBeVisible();
});
