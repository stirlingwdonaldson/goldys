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

vi.mock("@tanstack/react-query", async (importOriginal) => ({
  ...await importOriginal<typeof import("@tanstack/react-query")>(),
  useQuery: () => ({ data: [failed], isPending: false, error: null, refetch: async () => {} }),
}));

describe("LogsPage", () => {
  it("renders a table row and reveals the stack trace when expanded", () => {
    render(<LogsPage />);
    expect(screen.getByText("Cooking the Books")).toBeInTheDocument();
    // stack trace hidden until the row is clicked
    expect(screen.queryByText(/RuntimeException: boom/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText("Cooking the Books"));
    expect(screen.getByText(/RuntimeException: boom/)).toBeInTheDocument();
  });
});

vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({}), useDemoMode: () => ({ demo: true }) }));
