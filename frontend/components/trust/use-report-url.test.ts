import { describe, expect, it } from "vitest";
import { reportTarget } from "./use-report-url";
import { listPage, listUrl } from "@/lib/list-url";

describe("report evidence URLs", () => {
  it("keeps the parent page, size and source filter when selecting and closing evidence", () => {
    const parent = "page=3&size=25&source=CTB";
    const opened = listUrl(new URLSearchParams(parent), { metric: "sales.gross", trace: "2026-10-24" });
    expect(reportTarget(new URLSearchParams(opened), "sales.gross", "Gross sales")).toEqual({ metricId: "sales.gross", metricLabel: "Gross sales", date: "2026-10-24" });
    expect(listUrl(new URLSearchParams(opened), { metric: null, trace: null })).toBe(parent);
    expect(listPage(new URLSearchParams(opened))).toEqual({ page: 3, pageSize: 25 });
  });
  it("rejects other metrics and impossible or malformed dates", () => {
    for (const trace of ["2026-02-30", "2026-13-01", "bad", "2026-1-01", "2026-10-24/extra"]) {
      expect(reportTarget(new URLSearchParams({ metric: "sales.gross", trace }), "sales.gross", "Gross sales")).toBeNull();
    }
    expect(reportTarget(new URLSearchParams("metric=labour.cost&trace=2026-10-24"), "sales.gross", "Gross sales")).toBeNull();
  });
});
