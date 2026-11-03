import { beforeEach, describe, expect, it, vi } from "vitest";
import { demoWeeklyReport, demoWeeklyTargets, saveDemoWeeklyTargets } from "./weekly-report-demo";
import { liveApi } from "./live";

describe("weekly reporting adapters", () => {
  beforeEach(() => vi.restoreAllMocks());

  it("uses a bounded live GET and forwards cancellation", async () => {
    const report = demoWeeklyReport("2026-10-26", 1);
    const fetcher = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify(report), { headers: { "content-type": "application/json" } }));
    const abort = new AbortController();
    expect(await liveApi.getWeeklyReport("2026-10-26", 1, { signal: abort.signal })).toEqual(report);
    expect(fetcher).toHaveBeenCalledWith("/api/reports/weekly?weeks=1&from=2026-10-26", expect.objectContaining({ signal: abort.signal, credentials: "same-origin" }));
  });

  it("keeps demo fields, missing values, ratios and qualifications consistent", () => {
    const report = demoWeeklyReport("2026-10-26", 2);
    expect(report.weeks).toHaveLength(2);
    expect(report.weeks[0].rows).toHaveLength(61);
    expect(new Set(report.weeks[0].rows.map(r => r.id)).size).toBe(61);
    expect(report.weeks[0].rows.find(r => r.id === "net_sales")?.days[1]).toBeNull();
    expect(report.weeks[0].rows.find(r => r.id === "food_sales")?.total).toBeNull();
    expect(report.weeks[0].rows.find(r => r.id === "total_wastage")?.status).toBe("QUALIFIED");
    expect(report.weeks[0].rows.find(r => r.id === "review_average")?.total).toBe(4.5);
    expect(report.weeks[0].rows.some(r => /labour|wages|happiness|prime cost/i.test(r.label))).toBe(false);
  });

  it("rejects invalid dates and unbounded requests", () => {
    for (const count of [0, 14, 1.5]) expect(() => demoWeeklyReport("2026-10-26", count)).toThrow();
    for (const date of ["2026-10-27", "invalid", "2026-02-30"]) expect(() => demoWeeklyReport(date, 1)).toThrow();
  });

  it("saves effective-week demo targets without rewriting earlier weeks", () => {
    const previous = demoWeeklyTargets("2026-10-26").find(t => t.id === "sales_target")!.value;
    saveDemoWeeklyTargets({ effectiveFrom: "2026-11-02", values: { sales_target: 130000 } });
    expect(demoWeeklyTargets("2026-10-26").find(t => t.id === "sales_target")?.value).toBe(previous);
    expect(demoWeeklyTargets("2026-11-09").find(t => t.id === "sales_target")?.value).toBe(130000);
    expect(demoWeeklyReport("2026-11-09", 1).weeks[0].rows.find(r => r.id === "sales_target")?.total).toBe(130000);
    expect(() => saveDemoWeeklyTargets({ effectiveFrom: "2026-11-02", values: { food_variance_target_min: 0.02 } })).toThrow();
  });

  it("uses the live target PUT with its effective week", async () => {
    const input = { effectiveFrom: "2026-10-26", values: { sales_target: 120000 } };
    const fetcher = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("[]", { headers: { "content-type": "application/json" } }));
    await liveApi.saveWeeklyTargets(input);
    expect(fetcher).toHaveBeenCalledWith("/api/reports/weekly/targets", expect.objectContaining({ method: "PUT", body: JSON.stringify(input), credentials: "same-origin" }));
  });
});
