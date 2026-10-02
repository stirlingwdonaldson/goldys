import { describe, it, expect } from "vitest";
import { logLine } from "./log-line";
import type { ConnectorStatus } from "@/lib/api";

const base: ConnectorStatus = {
  source: "CTB",
  connectorName: "ctb-revenue",
  lastRunAt: null,
  status: "failed",
  failureCount: 1,
};

describe("logLine", () => {
  it("formats a failed connector with its failure detail", () => {
    const line = logLine({
      ...base,
      failure: { type: "AUTH_FAILED", message: "OAuth token rejected", at: "2026-10-02T12:00:05Z" },
    });
    expect(line).toContain("CTB");
    expect(line).toContain("AUTH_FAILED");
    expect(line).toContain("OAuth token rejected");
  });

  it("formats a connector with no failure", () => {
    const line = logLine({ ...base, status: "success", failure: null });
    expect(line).toContain("CTB");
    expect(line).not.toContain("failure");
  });

  it("formats a failure with a null message", () => {
    const line = logLine({
      ...base,
      failure: { type: "UNEXPECTED", message: null, at: "2026-10-02T12:00:05Z" },
    });
    expect(line).toContain("UNEXPECTED");
  });
});
