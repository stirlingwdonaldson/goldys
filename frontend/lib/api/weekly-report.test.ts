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
    expect(report.weeks[0].rows).toHaveLength(85);
    expect(new Set(report.weeks[0].rows.map(r => r.id)).size).toBe(85);
    expect(report.weeks[0].rows.find(r => r.id === "net_sales")?.days[1]).toBeNull();
    expect(report.weeks[0].rows.find(r => r.id === "food_sales")?.total).toBeGreaterThan(0);
    expect(report.weeks[0].rows.find(r => r.id === "total_wastage")?.status).toBe("QUALIFIED");
    expect(report.weeks[0].rows.find(r => r.id === "review_average")?.total).toBe(4.5);
    expect(report.weeks[0].rows.find(r => r.id === "food_known_theoretical_cost")?.total).toBeGreaterThan(0);
    expect(report.weeks[0].rows.find(r => r.id === "food_cost_coverage_lines")?.total).toBe(0.6);
    expect(report.weeks[0].rows.find(r => r.id === "food_cost_coverage_lines")?.days[1]).toBeNull();
    expect(report.weeks[0].rows.find(r => r.id === "food_theoretical_cogs")?.total).toBeNull();
    expect(report.weeks[0].rows.some(r => /labour|wages|happiness|prime cost/i.test(r.label))).toBe(false);
    for (const week of report.weeks) expect(Object.keys(week.assessments).sort()).toEqual(week.rows.map(r => r.id).sort());
    expect(report.weeks[0].assessments.net_sales).toEqual({ outcome: "WITHHELD", reason: "INCOMPLETE_EVIDENCE" });
    expect(report.weeks[0].assessments.sales_difference).toEqual(report.weeks[0].assessments.net_sales);
    expect(report.weeks[1].assessments.net_sales.outcome).toBe("BELOW_TARGET");
    expect(report.weeks[1].assessments.total_wastage_percent.reason).toBe("INCOMPLETE_EVIDENCE");
    expect(report.weeks[1].assessments.food_cogs_percent.outcome).toBe("UNAVAILABLE");
    expect(report.weeks[1].assessments.review_average.outcome).toBe("SOURCE_QUALIFIED");
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

  it("keeps demo assessments consistent after an effective zero-target edit", () => {
    saveDemoWeeklyTargets({ effectiveFrom: "2099-01-05", values: { sales_target: 0 } });
    const [partial, full] = demoWeeklyReport("2099-01-05", 2).weeks;
    expect(partial.assessments.net_sales.reason).toBe("INCOMPLETE_EVIDENCE");
    expect(full.assessments.net_sales.outcome).toBe("MEETS_TARGET");
    expect(full.assessments.sales_difference.outcome).toBe("MEETS_TARGET");
    expect(full.assessments.sales_difference_percent.outcome).toBe("UNAVAILABLE");
    expect(full.rows.find(r => r.id === "sales_difference_percent")?.total).toBeNull();
    const withoutTargets = demoWeeklyReport("2026-06-15", 2).weeks[1];
    expect(withoutTargets.assessments.net_sales.reason).toBe("NO_EFFECTIVE_TARGET");
    expect(withoutTargets.assessments.total_wastage_percent.reason).toBe("NO_EFFECTIVE_TARGET");
  });
});
