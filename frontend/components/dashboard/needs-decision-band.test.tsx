import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { NeedsDecisionBand } from "./needs-decision-band";

describe("NeedsDecisionBand", () => {
  it("shows the open-conflict state with a review link when conflicts exist", () => {
    render(<NeedsDecisionBand openCount={3} />);
    expect(screen.getByText("3 numbers need a decision")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /review in reconciliation/i }),
    ).toHaveAttribute("href", "/reconciliation");
  });

  it("uses singular copy for exactly one conflict", () => {
    render(<NeedsDecisionBand openCount={1} />);
    expect(screen.getByText("1 number needs a decision")).toBeInTheDocument();
  });

  it("shows the all-reconcile success state when there are no conflicts", () => {
    render(<NeedsDecisionBand openCount={0} />);
    expect(screen.getByText("All numbers reconcile")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /open reconciliation/i }),
    ).toHaveAttribute("href", "/reconciliation");
  });

  it("honors a custom href", () => {
    render(<NeedsDecisionBand openCount={2} href="/reconciliation?tab=open" />);
    expect(screen.getByRole("link", { name: /review in reconciliation/i })).toHaveAttribute(
      "href",
      "/reconciliation?tab=open",
    );
  });
});
