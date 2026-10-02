import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import ResolutionRulesPage from "./page";

vi.mock("@/lib/demo-mode", () => ({
  useDemoMode: () => ({ demo: false, setDemo: () => {} }),
  useApi: () => {
    throw new Error("useApi must not be called in live mode");
  },
}));

describe("ResolutionRulesPage (live mode)", () => {
  it("shows a not-available state instead of fetching live endpoints", () => {
    render(<ResolutionRulesPage />);

    expect(screen.getByText(/not available with live data/i)).toBeInTheDocument();
    // The demo-only content (rule list + editor) must not render in live mode.
    expect(screen.queryByText(/rules by entity/i)).not.toBeInTheDocument();
  });
});
