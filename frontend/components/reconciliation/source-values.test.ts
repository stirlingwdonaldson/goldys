import { describe, it, expect } from "vitest";
import { difference, parseAmount } from "./source-values";
import { filterExceptions } from "./exceptions-list";
import type { ReconciliationException } from "@/lib/api";

describe("parseAmount", () => {
  it("reads formatted and plain amounts", () => {
    expect(parseAmount("$14,980.50")).toBe(14980.5);
    expect(parseAmount("212")).toBe(212);
  });
  it("returns null for missing or non-numeric values", () => {
    expect(parseAmount(null)).toBeNull();
    expect(parseAmount("n/a")).toBeNull();
  });
});

describe("difference", () => {
  it("is the absolute gap between two numeric sources", () => {
    expect(
      difference([
        { source: "Lightspeed", value: "$248.50" },
        { source: "Cooking the Books", value: "$212.00" },
      ]),
    ).toBeCloseTo(36.5);
  });
  it("is null when a source has no data", () => {
    expect(
      difference([
        { source: "Lightspeed", value: "$248.50" },
        { source: "Cooking the Books", value: null },
      ]),
    ).toBeNull();
  });
});

describe("filterExceptions", () => {
  const ex = (id: string, status: "conflict" | "missing"): ReconciliationException => ({
    id: `${id}:amount`,
    recordId: id,
    entity: id,
    field: "amount",
    status,
    sources: [],
  });
  const all = [ex("2026-10-04", "conflict"), ex("2026-10-02", "missing")];

  it("filters by status", () => {
    expect(filterExceptions(all, "missing", "").map((e) => e.recordId)).toEqual(["2026-10-02"]);
  });
  it("matches the formatted day as well as the raw date", () => {
    expect(filterExceptions(all, "all", "4 oct").map((e) => e.recordId)).toEqual(["2026-10-04"]);
    expect(filterExceptions(all, "all", "2026-10-02").map((e) => e.recordId)).toEqual(["2026-10-02"]);
  });
});
