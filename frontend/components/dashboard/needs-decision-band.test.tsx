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

  it("does not infer verification from zero open decisions", () => {
    render(<NeedsDecisionBand openCount={0} />);
    expect(screen.getByText("No open reconciliation decisions reported")).toBeInTheDocument();
    expect(screen.getByText(/does not establish complete source coverage/)).toBeInTheDocument();
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
