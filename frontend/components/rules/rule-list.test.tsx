import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { RuleList } from "./rule-list";
import type { RuleRow } from "@/lib/rule-logic";

function row(entityType: string, fieldKey: string, rule: RuleRow["rule"]): RuleRow {
  return { entityType, fieldKey, rule };
}

const rows: RuleRow[] = [
  row("daily_sales", "daily_sales", null),
  row("product_sales", "garlic aioli", {
    id: "rule-1",
    entityType: "product_sales",
    fieldKey: "garlic aioli",
    strategy: "priority",
    sourcePriority: ["Lightspeed", "Cooking the Books"],
    updatedAt: "2026-09-29T18:00:00Z",
    updatedBy: "Stirling Donaldson",
  }),
];

describe("RuleList", () => {
  it("renders each field with its effect or an unresolved badge", () => {
    render(<RuleList rows={rows} onEdit={() => {}} onDelete={() => {}} onNew={() => {}} />);
    expect(screen.getByText("Lightspeed wins")).toBeInTheDocument();
    expect(screen.getByText("Daily sales total")).toBeInTheDocument();
    expect(screen.getByText(/unresolved/i)).toBeInTheDocument();
  });

  it("calls onEdit with the rule id when an edit action is clicked", () => {
    const onEdit = vi.fn();
    render(<RuleList rows={rows} onEdit={onEdit} onDelete={() => {}} onNew={() => {}} />);
    screen.getByRole("button", { name: /edit garlic aioli/i }).click();
    expect(onEdit).toHaveBeenCalledWith("rule-1");
  });

  it("renders the new-rule button", () => {
    const onNew = vi.fn();
    render(<RuleList rows={rows} onEdit={() => {}} onDelete={() => {}} onNew={onNew} />);
    screen.getByRole("button", { name: /new rule/i }).click();
    expect(onNew).toHaveBeenCalled();
  });
});
