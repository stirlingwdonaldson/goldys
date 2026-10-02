import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RuleAudit } from "./rule-audit";
import type { RuleAuditEntry } from "@/lib/api";

const entries: RuleAuditEntry[] = [
  {
    ruleId: "rule-1",
    entityType: "daily_sales",
    fieldKey: "daily_sales",
    change: "created",
    at: "2026-09-29T18:00:00Z",
    by: "Stirling Donaldson",
  },
  {
    ruleId: null,
    entityType: "product_sales",
    fieldKey: "garlic aioli",
    change: "deleted",
    at: "2026-09-30T08:00:00Z",
    by: "Stirling Donaldson",
  },
];

describe("RuleAudit", () => {
  it("renders each entry with its change, entity/field, and actor", () => {
    render(<RuleAudit entries={entries} />);
    expect(screen.getByText(/daily sales total/i)).toBeInTheDocument();
    expect(screen.getByText(/garlic aioli/i)).toBeInTheDocument();
    expect(screen.getByText(/created/i)).toBeInTheDocument();
    expect(screen.getByText(/deleted/i)).toBeInTheDocument();
    expect(screen.getAllByText(/stirling donaldson/i)).toHaveLength(2);
  });

  it("shows an empty note when there is no history", () => {
    render(<RuleAudit entries={[]} />);
    expect(screen.getByText(/no changes yet/i)).toBeInTheDocument();
  });
});
