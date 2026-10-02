import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { LogsDrawer } from "./logs-drawer";
import type { ConnectorStatus } from "@/lib/api";

const failed: ConnectorStatus = {
  source: "CTB",
  connectorName: "ctb-revenue",
  lastRunAt: "2026-10-02T12:00:00Z",
  status: "failed",
  failureCount: 1,
  failure: { type: "AUTH_FAILED", message: "OAuth token rejected", at: "2026-10-02T12:00:05Z" },
};

vi.mock("@/lib/use-api-data", () => ({
  useApiData: () => ({ data: [failed], loading: false, error: null, reload: async () => {} }),
}));

describe("LogsDrawer", () => {
  beforeEach(() => {
    vi.stubGlobal("navigator", { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
  });

  it("opens and shows a connector's failure detail", () => {
    render(<LogsDrawer />);
    fireEvent.click(screen.getByRole("button", { name: /logs/i }));
    expect(screen.getByText("CTB")).toBeInTheDocument();
    expect(screen.getByText(/OAuth token rejected/)).toBeInTheDocument();
  });

  it("shows the trigger even when closed", () => {
    render(<LogsDrawer />);
    expect(screen.getByRole("button", { name: /logs/i })).toBeInTheDocument();
  });
});
