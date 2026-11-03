import { afterEach, expect, it, vi } from "vitest";
import { liveApi } from "./live";
import { demoApi } from "./demo";
import type { ReportingMapping } from "./types";
afterEach(() => vi.restoreAllMocks());
it("forwards bounded exact live queue parameters and preserves missing values", async () => {
  const response = await demoApi.getReportingReviewQueue(
    "SALES_BASIS",
    "2026-11-02",
    "2026-11-03",
    0,
  );
  const fetcher = vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(JSON.stringify(response), {
      headers: { "content-type": "application/json" },
    }),
  );
  expect(
    await liveApi.getReportingReviewQueue(
      "SALES_BASIS",
      "2026-11-02",
      "2026-11-03",
      2,
    ),
  ).toEqual(response);
  expect(fetcher).toHaveBeenCalledWith(
    "/api/products/categories/reporting-mappings/queue?domain=SALES_BASIS&from=2026-11-02&to=2026-11-03&page=2&size=50",
    expect.objectContaining({ credentials: "same-origin" }),
  );
  expect(response.items[0]).toMatchObject({
    knownExposure: null,
    missingBasisGross: 110,
    missingBasis: 1,
  });
});
it("keeps missing-cost wastage and missing-tax sales visible separately from classification", async () => {
  const result = await demoApi.getReportingReviewQueue(
    "WASTAGE",
    "2026-11-02",
    "2026-11-03",
    0,
  );
  expect(result.items[0]).toMatchObject({
    scope: "WASTE_LINE",
    recipeId: 388,
    knownExposure: null,
    missingBasis: 1,
  });
  expect(result.wastage).toMatchObject({
    reasonLines: 0,
    missingCost: 1,
    knownValue: null,
  });
  expect(
    (
      await demoApi.getReportingReviewQueue(
        "SALES",
        "2026-11-02",
        "2026-11-03",
        1,
      )
    ).items,
  ).toEqual([]);
  await expect(
    demoApi.getReportingReviewQueue("SALES", "2026-01-01", "2026-11-03", 0),
  ).rejects.toThrow(/92 days/);
  await expect(
    demoApi.getReportingReviewQueue("SALES", "invalid", "invalid", 0),
  ).rejects.toThrow();
});

it("honours effective dates and same-day UNKNOWN withdrawals in demo without hiding missing costs", async () => {
  const mapping: ReportingMapping = {
    scope: "CTB_PRODUCT",
    sourceSystem: "CTB",
    entityKey: "site:0:code:8:20161229",
    effectiveFrom: "2026-11-02",
    salesNature: "FOOD",
    stockBucket: "UNKNOWN",
    itemRole: "UNKNOWN",
    businessArea: "UNKNOWN",
    lifecycle: "UNKNOWN",
    reason: "Explicit demo review",
  };
  await demoApi.saveReportingMapping(mapping);
  expect(
    (
      await demoApi.getReportingReviewQueue(
        "SALES",
        "2026-11-02",
        "2026-11-03",
        0,
      )
    ).items,
  ).toEqual([]);
  expect(
    (
      await demoApi.getReportingReviewQueue(
        "SALES",
        "2026-11-01",
        "2026-11-03",
        0,
      )
    ).items,
  ).toHaveLength(1);
  await demoApi.saveReportingMapping({
    ...mapping,
    salesNature: "UNKNOWN",
    reason: "Withdraw demo decision",
  });
  expect(
    (
      await demoApi.getReportingReviewQueue(
        "SALES",
        "2026-11-02",
        "2026-11-03",
        0,
      )
    ).items[0].policies,
  ).toContain("UNKNOWN");
  await demoApi.saveReportingMapping({
    ...mapping,
    scope: "WASTE_LINE",
    entityKey: "wastage:1:1",
    salesNature: "BEVERAGE",
  });
  const waste = await demoApi.getReportingReviewQueue(
    "WASTAGE",
    "2026-11-02",
    "2026-11-03",
    0,
  );
  expect(waste.wastage.classifiedLines).toBe(1);
  expect(waste.items[0].missingBasis).toBe(1);
  expect(waste.items[0].knownExposure).toBeNull();
});
