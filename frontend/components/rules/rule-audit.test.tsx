import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RuleAudit } from "./rule-audit";
import type { RuleAuditEntry } from "@/lib/api";

const entries: RuleAuditEntry[] = [
  { id: "a1", ruleId: "rule-1", field: "quantity_sold", change: "created", at: "2026-09-29T18:00:00Z", by: "Stirling Donaldson" },
  { id: "a2", ruleId: null, field: "net_amount", change: "deleted", at: "2026-09-30T08:00:00Z", by: "Stirling Donaldson" },
];

describe("RuleAudit", () => {
  it("renders each entry with its change, field, and actor", () => {
    render(<RuleAudit entries={entries} />);
    expect(screen.getByText(/quantity_sold/i)).toBeInTheDocument();
    expect(screen.getByText(/created/i)).toBeInTheDocument();
    expect(screen.getByText(/deleted/i)).toBeInTheDocument();
    expect(screen.getAllByText(/stirling donaldson/i)).toHaveLength(2);
  });

  it("shows an empty note when there is no history", () => {
    render(<RuleAudit entries={[]} />);
    expect(screen.getByText(/no changes yet/i)).toBeInTheDocument();
  });
});
