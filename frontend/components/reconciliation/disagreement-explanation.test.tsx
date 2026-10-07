import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DisagreementExplanation } from "./disagreement-explanation";
import type { ReconciliationField, ResolutionRule } from "@/lib/api";

function field(overrides: Partial<ReconciliationField> = {}): ReconciliationField {
  return {
    name: "daily_sales",
    label: "Daily sales",
    sources: [
      { source: "Lightspeed", value: "$27650.66" },
      { source: "Cooking the Books", value: "$20990.83" },
    ],
    overridden: false,
    ...overrides,
  };
}

const priorityRule: ResolutionRule = {
  id: "rule-1",
  entityType: "daily_sales",
  fieldKey: "daily_sales",
  strategy: "priority",
  sourcePriority: ["Cooking the Books", "Lightspeed"],
  updatedAt: "2026-09-29T18:00:00Z",
  updatedBy: "Stirling Donaldson",
};

describe("DisagreementExplanation", () => {
  it("shows which sources differ, why nothing is chosen yet, and which rule applies", () => {
    render(<DisagreementExplanation field={field()} rule={priorityRule} />);

    const sources = screen.getByText(/sources disagree on daily sales/i);
    expect(sources).toHaveTextContent("Lightspeed");
    expect(sources).toHaveTextContent("Cooking the Books");

    expect(screen.getByText(/no result chosen yet/i)).toBeInTheDocument();
    expect(screen.getByText(/rule applied: cooking the books wins/i)).toBeInTheDocument();
  });

  it("explains a manual override with its reason and actor, and notes no rule applies", () => {
    render(
      <DisagreementExplanation
        field={field({
          overridden: true,
          authoritativeSource: "Lightspeed",
          overrideReason: "Till matched the bank.",
          overrideActor: "Stirling Donaldson",
        })}
        rule={null}
      />,
    );

    const chosen = screen.getByText(/resolved to lightspeed/i);
    expect(chosen).toHaveTextContent("by Stirling Donaldson");
    expect(chosen).toHaveTextContent("Till matched the bank.");
    expect(screen.getByText(/no automatic rule applies/i)).toBeInTheDocument();
  });
});
