import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AskGoldysDrawer } from "./ask-goldys-drawer";
import { DemoModeProvider } from "@/lib/demo-mode";

function renderDrawer(seniority?: string) {
  return render(
    <DemoModeProvider>
      <AskGoldysDrawer seniority={seniority} />
    </DemoModeProvider>,
  );
}

describe("AskGoldysDrawer", () => {
  it("shows the Ask button for an Owner", () => {
    renderDrawer("OWNER");
    expect(screen.getByRole("button", { name: /ask/i })).toBeInTheDocument();
  });

  it("hides itself for a non-Owner", () => {
    renderDrawer("MANAGER");
    expect(screen.queryByRole("button", { name: /ask/i })).not.toBeInTheDocument();
  });

  it("fails closed when seniority is absent", () => {
    renderDrawer();
    expect(screen.queryByRole("button", { name: /ask/i })).not.toBeInTheDocument();
  });
});
