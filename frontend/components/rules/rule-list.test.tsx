import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { RuleList } from "./rule-list";
import type { RuleRow } from "@/lib/rule-logic";

function row(entity: string, field: string, rule: RuleRow["rule"]): RuleRow {
  return { entity, field, rule };
}

const rows: RuleRow[] = [
  row("Sales", "net_amount", null),
  row("Sales", "quantity_sold", {
    id: "rule-1",
    entity: "Sales",
    field: "quantity_sold",
    strategy: "priority",
    sourcePriority: ["Cooking the Books", "Lightspeed"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
  }),
];

describe("RuleList", () => {
  it("renders each field with its effect or an unresolved badge", () => {
    render(<RuleList rows={rows} onEdit={() => {}} onDelete={() => {}} onNew={() => {}} />);
    expect(screen.getByText("Cooking the Books wins")).toBeInTheDocument();
    expect(screen.getByText("net_amount")).toBeInTheDocument();
    expect(screen.getByText(/unresolved/i)).toBeInTheDocument();
  });

  it("calls onEdit with the rule id when an edit action is clicked", () => {
    const onEdit = vi.fn();
    render(<RuleList rows={rows} onEdit={onEdit} onDelete={() => {}} onNew={() => {}} />);
    screen.getByRole("button", { name: /edit quantity_sold/i }).click();
    expect(onEdit).toHaveBeenCalledWith("rule-1");
  });

  it("renders the new-rule button", () => {
    const onNew = vi.fn();
    render(<RuleList rows={rows} onEdit={() => {}} onDelete={() => {}} onNew={onNew} />);
    screen.getByRole("button", { name: /new rule/i }).click();
    expect(onNew).toHaveBeenCalled();
  });
});
