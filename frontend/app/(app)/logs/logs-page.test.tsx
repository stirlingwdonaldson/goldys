import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import LogsPage from "./page";
import type { ConnectorStatus } from "@/lib/api";

const failed: ConnectorStatus = {
  source: "CTB",
  connectorName: "ctb-revenue",
  lastRunAt: "2026-10-02T12:00:00Z",
  status: "failed",
  failureCount: 1,
  runnable: true,
  failure: {
    type: "AUTH_FAILED",
    message: "CTB login failed",
    at: "2026-10-02T12:00:05Z",
    stackTrace: "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.java:1)",
  },
};

vi.mock("@/lib/use-api-data", () => ({
  useApiData: () => ({ data: [failed], loading: false, error: null, reload: async () => {} }),
}));

describe("LogsPage", () => {
  it("renders a table row and reveals the stack trace when expanded", () => {
    render(<LogsPage />);
    expect(screen.getByText("CTB")).toBeInTheDocument();
    // stack trace hidden until the row is clicked
    expect(screen.queryByText(/RuntimeException: boom/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText("CTB"));
    expect(screen.getByText(/RuntimeException: boom/)).toBeInTheDocument();
  });
});
