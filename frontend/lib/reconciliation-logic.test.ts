import { describe, it, expect } from "vitest";
import { deriveExceptions, needsDecision } from "./reconciliation-logic";
import type { ReconciliationField } from "@/lib/api";

function field(overrides: Partial<ReconciliationField> = {}): ReconciliationField {
  return {
    name: "amount",
    label: "Net amount",
    sources: [],
    overridden: false,
    ...overrides,
  };
}

describe("needsDecision", () => {
  it("returns false for an overridden field", () => {
    expect(needsDecision(field({ overridden: true }))).toBe(false);
  });

  it("returns false when all sources agree", () => {
    expect(
      needsDecision(
        field({
          sources: [
            { source: "Lightspeed", value: "3" },
            { source: "Cooking the Books", value: "3" },
          ],
        }),
      ),
    ).toBe(false);
  });

  it("returns true when sources disagree", () => {
    expect(
      needsDecision(
        field({
          sources: [
            { source: "Lightspeed", value: "14" },
            { source: "Cooking the Books", value: "12" },
          ],
        }),
      ),
    ).toBe(true);
  });

  it("returns true when a source has no data", () => {
    expect(
      needsDecision(
        field({
          sources: [
            { source: "Lightspeed", value: "$260.00" },
            { source: "Cooking the Books", value: null },
          ],
        }),
      ),
    ).toBe(true);
  });

  it("returns false for a single source (nothing to compare)", () => {
    expect(needsDecision(field({ sources: [{ source: "Lightspeed", value: "5" }] }))).toBe(false);
  });
});

describe("deriveExceptions", () => {
  it("emits conflict and missing exceptions and skips agreeing fields", () => {
    const exceptions = deriveExceptions({
      "sale-1": {
        id: "sale-1",
        entity: "Sale 1",
        entityType: "sale",
        fields: [
          field({
            name: "qty",
            sources: [
              { source: "Lightspeed", value: "14" },
              { source: "Cooking the Books", value: "12" },
            ],
          }),
          field({
            name: "gross",
            sources: [
              { source: "Lightspeed", value: "$260.00" },
              { source: "Cooking the Books", value: null },
            ],
          }),
          field({
            name: "note",
            sources: [
              { source: "Lightspeed", value: "x" },
              { source: "Cooking the Books", value: "x" },
            ],
          }),
        ],
      },
    });

    expect(exceptions).toHaveLength(2);
    expect(exceptions[0]).toMatchObject({ recordId: "sale-1", field: "qty", status: "conflict" });
    expect(exceptions[1]).toMatchObject({ recordId: "sale-1", field: "gross", status: "missing" });
  });

  it("drops an exception once the field is overridden", () => {
    const records = {
      "sale-1": {
        id: "sale-1",
        entity: "Sale 1",
        entityType: "sale",
        fields: [
          field({
            name: "qty",
            sources: [
              { source: "Lightspeed", value: "14" },
              { source: "Cooking the Books", value: "12" },
            ],
          }),
        ],
      },
    };

    expect(deriveExceptions(records)).toHaveLength(1);

    // Simulate saveOverride: mark the field overridden.
    records["sale-1"].fields[0].overridden = true;
    records["sale-1"].fields[0].authoritativeSource = "Lightspeed";

    expect(deriveExceptions(records)).toHaveLength(0);
  });
});
