import { test, expect } from "@playwright/test";
import type { WorkspaceJob } from "../../lib/api/workspace";
import type { ChartWidget } from "../../components/widgets/types";

test.skip(process.env.NEXT_PUBLIC_WORKSPACE_ENABLED !== "true" && process.env.NEXT_PUBLIC_WORKSPACE_PREVIEW !== "true", "Requires workspace build.");

const marks = { LINE: ".recharts-line-curve", AREA: ".recharts-area-area", BAR: ".recharts-bar-rectangle", PIE: ".recharts-pie-sector", DONUT: ".recharts-pie-sector", RADAR: ".recharts-radar-polygon", RADIAL: ".recharts-radial-bar-sector" };
for (const kind of Object.keys(marks) as ChartWidget["kind"][]) {
  test(`${kind} supports answers inline, reload and narrow screens`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 1000 });
    const job: WorkspaceJob = { id: "10000000-0000-4000-8000-000000000001", threadId: "10000000-0000-4000-8000-000000000002", state: "COMPLETED", prompt: "How did sales vary this week?", revision: 1,
      messages: [
        { id: "visual", role: "tool", text: "", reporting: { widget: { schemaVersion: 2, type: "chart", id: kind, title: "Gross sales", description: "Fixture data. Values in AUD; dates retained in legend.", kind, stacked: false, horizontal: kind === "BAR", yFormat: "currency",
          series: [{ key: "gross", label: "2–5 Nov 2026", points: [{ x: "Mon", y: 100 }, { x: "Tue", y: 0 }, { x: "Wed", y: 200 }, { x: "Thu", y: 150 }] }] }, notices: ["Fixture source qualification"], provenance: [], relatedMetrics: [] } },
        { id: "answer", role: "assistant", text: "Wednesday had the highest sales.", reporting: null },
      ] };
    await page.addInitScript(() => localStorage.setItem("goldys-demo-mode", "false"));
    await page.route("**/monitoring**", route => route.fulfill({ status: 204 }));
    await page.route("**/api/**", route => {
      const path = new URL(route.request().url()).pathname;
      if (path === "/api/me") return route.fulfill({ json: { displayName: "Fixture owner", department: "ALL", seniority: "OWNER" } });
      if (path === "/api/workspace/threads") return route.fulfill({ json: { threads: [{ id: job.threadId, createdAt: "2026-11-18T00:00:00Z", access: "AVAILABLE", latestJobId: job.id }], nextCursor: null } });
      if (path === `/api/workspace/jobs/${job.id}`) return route.fulfill({ json: job });
      return route.fulfill({ json: [] });
    });
    await page.goto(`/ask-goldys?investigation=${job.id}`);
    const chart = page.getByRole("img", { name: `Gross sales — ${kind.toLowerCase()} chart` });
    await expect(chart).toBeVisible();
    await expect.poll(() => chart.locator(marks[kind]).count()).toBeGreaterThan(0);
    await expect(page.getByRole("button", { name: "Save dashboard" })).toHaveCount(0);
    await expect(page.getByText("Fixture source qualification")).toHaveCount(0);
    await chart.evaluate(element => element.scrollIntoView({ block: "center" }));
    await page.screenshot({ path: `/tmp/goldys-chart-${kind.toLowerCase()}.png` });
    await page.getByText("View chart data", { exact: true }).click();
    await expect(page.getByRole("table")).toContainText("$0.00");
    await expect(page.getByRole("table")).toContainText("$200.00");
    await page.reload();
    await expect(chart).toBeVisible();
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(chart).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}
