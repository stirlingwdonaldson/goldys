import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import type { ConnectorStatus } from "@/lib/api";
import { FreshnessChip } from "./freshness-chip";

const { status } = vi.hoisted(() => ({ status: { connectors: [] as ConnectorStatus[] } }));
vi.mock("./shell-status", () => ({
  useShellStatus: () => status,
  isFailing: (source: ConnectorStatus) => source.status === "failed",
}));

it("counts only recorded successful/quiet runs and names every other collection state", () => {
  status.connectors = ["success", "no_new_data", "partial", "never_run", "running", "failed"].map((state, i) => ({
    source: `SOURCE_${i}`, connectorName: `source-${i}`, status: state as ConnectorStatus["status"],
    lastRunAt: state === "never_run" ? null : "2026-10-24T04:00:00Z", failureCount: 0, runnable: true,
  }));
  render(<FreshnessChip />);
  const link = screen.getByRole("link");
  expect(link).toHaveAccessibleName(/Last collection run/);
  expect(link).toHaveAccessibleName(/2 of 6 latest runs completed without errors/);
  for (const state of ["partial", "never run", "running", "failed"]) expect(link).toHaveAccessibleName(new RegExp(state));
  expect(link).toHaveAccessibleName(/Evidence freshness and completeness are separate/);
  expect(screen.queryByText(/healthy|Data as of/i)).not.toBeInTheDocument();
  expect(screen.getByText("2/6 runs completed")).toBeInTheDocument();
});

it("does not count an unrecorded success as a completed run", () => {
  status.connectors = [{ source: "CTB", connectorName: "ctb", status: "success", lastRunAt: null, failureCount: 0, runnable: true }];
  render(<FreshnessChip />);
  expect(screen.getByRole("link")).toHaveAccessibleName(/No collection runs recorded. 0 of 1/);
});
