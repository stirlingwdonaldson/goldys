import { describe, expect, it } from "vitest";
import { buildWorkQueue, defaultFocus, filterByFocus, type WorkQueueInput } from "./work-queue";

const empty: WorkQueueInput = {
  dailyExceptions: [],
  productExceptions: [],
  connectors: [],
  invoiceFlags: [],
  recompute: { state: "idle", lastChangedAt: null },
};

describe("buildWorkQueue", () => {
  it("is empty when nothing needs attention", () => {
    expect(buildWorkQueue(empty)).toEqual([]);
  });

  it("orders urgent items first, then most recent", () => {
    const items = buildWorkQueue({
      ...empty,
      dailyExceptions: [
        {
          id: "2026-10-07:totalSales",
          recordId: "2026-10-07",
          entity: "Daily sales",
          field: "totalSales",
          sources: [
            { source: "LIGHTSPEED", value: "12410" },
            { source: "CTB", value: "12180" },
          ],
          status: "conflict",
        },
      ],
      invoiceFlags: [
        { flagType: "MISSING_PDF", invoiceNumber: "INV-1", pdfFilename: null, stockCode: null, detail: null, occurredAt: "2026-10-09T00:00:00Z" },
      ],
      connectors: [
        { source: "DEPUTY", connectorName: "deputy", lastRunAt: "2026-10-01T00:00:00Z", status: "failed", failureCount: 2, runnable: false },
        { source: "LIGHTSPEED", connectorName: "ls", lastRunAt: "2026-10-09T00:00:00Z", status: "success", failureCount: 0, runnable: false },
      ],
    });
    expect(items.map((i) => i.category)).toEqual(["decision", "source", "invoice"]);
    expect(items[0].href).toBe("/reconciliation?record=2026-10-07");
    // Healthy sources produce nothing.
    expect(items.filter((i) => i.category === "source")).toHaveLength(1);
  });

  it("links product decisions to the product tab with date and product", () => {
    const [item] = buildWorkQueue({
      ...empty,
      productExceptions: [
        { id: "2026-09-14:garlic aioli", recordId: "garlic aioli", entity: "garlic aioli", field: "garlic aioli", sources: [], status: "missing" },
      ],
    });
    expect(item.href).toBe("/reconciliation?tab=product&date=2026-09-14&product=garlic%20aioli");
    expect(item.severity).toBe("low");
  });

  it("tolerates signals that couldn't be read", () => {
    const items = buildWorkQueue({
      dailyExceptions: null,
      productExceptions: null,
      connectors: null,
      invoiceFlags: null,
      recompute: { state: "failed", lastChangedAt: "2026-10-09T01:00:00Z" },
    });
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({ category: "recompute", severity: "high" });
  });
});

describe("filterByFocus", () => {
  const items = buildWorkQueue({
    ...empty,
    invoiceFlags: [{ flagType: "PDF_ONLY_LINE", invoiceNumber: "INV-2", pdfFilename: null, stockCode: "CB-1", detail: null, occurredAt: "2026-10-08T00:00:00Z" }],
    connectors: [{ source: "OPENTABLE", connectorName: "ot", lastRunAt: null, status: "never_run", failureCount: 0, runnable: false }],
  });

  it("shows the whole business everything", () => {
    expect(filterByFocus(items, "business")).toHaveLength(2);
  });
  it("keeps invoices for the kitchen and bookings for front of house", () => {
    expect(filterByFocus(items, "kitchen").map((i) => i.category)).toEqual(["invoice"]);
    expect(filterByFocus(items, "foh").map((i) => i.category)).toEqual(["source"]);
  });
});

describe("defaultFocus", () => {
  it("starts from the profile", () => {
    expect(defaultFocus({ seniority: "OWNER", department: "ALL" })).toBe("business");
    expect(defaultFocus({ seniority: "MANAGER", department: "BOH" })).toBe("kitchen");
    expect(defaultFocus({ seniority: "MANAGER", department: "FOH" })).toBe("foh");
    expect(defaultFocus({ seniority: "MANAGER", department: "ALL" })).toBe("venue");
    expect(defaultFocus(null)).toBe("venue");
  });
});
