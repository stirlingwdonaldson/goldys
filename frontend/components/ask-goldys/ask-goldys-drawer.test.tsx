import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AskGoldysDrawer } from "./ask-goldys-drawer";

describe("AskGoldysDrawer", () => {
  it("shows the Ask button for an Owner", () => {
    render(<AskGoldysDrawer seniority="OWNER" />);
    expect(screen.getByRole("button", { name: /ask/i })).toBeInTheDocument();
  });

  it("hides itself for a non-Owner", () => {
    render(<AskGoldysDrawer seniority="MANAGER" />);
    expect(screen.queryByRole("button", { name: /ask/i })).not.toBeInTheDocument();
  });

  it("fails closed when seniority is absent", () => {
    render(<AskGoldysDrawer />);
    expect(screen.queryByRole("button", { name: /ask/i })).not.toBeInTheDocument();
  });
});
