import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ReconciliationAudit } from "./reconciliation-audit";
import type { ReconciliationAuditEntry } from "@/lib/api";

const entries: ReconciliationAuditEntry[] = [
  {
    kind: "rule",
    change: "created",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    source: null,
    reason: null,
    by: "Stirling Donaldson",
    at: "2026-09-29T18:00:00Z",
  },
  {
    kind: "override",
    change: "set",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    source: "Lightspeed",
    reason: "Till matched the bank.",
    by: "Stirling Donaldson",
    at: "2026-09-30T08:15:00Z",
  },
  {
    kind: "override",
    change: "removed",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    source: "Cooking the Books",
    reason: null,
    by: "Stirling Donaldson",
    at: "2026-09-30T09:00:00Z",
  },
];

describe("ReconciliationAudit", () => {
  it("renders rule and override changes in venue language", () => {
    render(<ReconciliationAudit entries={entries} />);

    expect(screen.getByText(/rule created/i)).toBeInTheDocument();
    expect(screen.getByText(/override set/i)).toBeInTheDocument();
    expect(screen.getByText(/override removed/i)).toBeInTheDocument();

    expect(screen.getByText(/lightspeed chosen/i)).toBeInTheDocument();
    expect(screen.getByText(/cooking the books no longer authoritative/i)).toBeInTheDocument();
    expect(screen.getByText(/till matched the bank/i)).toBeInTheDocument();
  });

  it("shows an empty note when there is no history", () => {
    render(<ReconciliationAudit entries={[]} />);
    expect(screen.getByText(/no changes yet/i)).toBeInTheDocument();
  });
});
