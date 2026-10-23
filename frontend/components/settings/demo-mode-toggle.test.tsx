import { describe, it, expect } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { DemoModeToggle } from "./demo-mode-toggle";

describe("DemoModeToggle", () => {
  it("renders as a switch and toggles demo mode", () => {
    render(
      <DemoModeProvider>
        <DemoModeToggle />
      </DemoModeProvider>,
    );

    const toggle = screen.getByRole("switch", { name: "Demo data" });
    expect(toggle).toHaveAttribute("aria-checked", "true");

    fireEvent.click(toggle);
    expect(screen.getByRole("switch", { name: "Demo data" })).toHaveAttribute("aria-checked", "false");
  });
});
