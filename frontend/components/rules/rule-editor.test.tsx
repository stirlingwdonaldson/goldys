import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { RuleEditor } from "./rule-editor";
import type { ResolutionRule } from "@/lib/api";

const existing: ResolutionRule = {
  id: "rule-1",
  entityType: "daily_sales",
  fieldKey: "daily_sales",
  strategy: "priority",
  sourcePriority: ["Cooking the Books", "Lightspeed"],
  updatedAt: "2026-09-29T18:00:00Z",
  updatedBy: "Stirling Donaldson",
};

describe("RuleEditor", () => {
  it("does not mutate the initial rule when the user cancels", () => {
    const onCancel = vi.fn();
    render(
      <RuleEditor
        open
        onCancel={onCancel}
        onSave={() => {}}
        entities={["daily_sales"]}
        fieldsByEntity={{ daily_sales: ["daily_sales"] }}
        initial={existing}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /cancel/i }));
    expect(existing.strategy).toBe("priority");
    expect(existing.sourcePriority).toEqual(["Cooking the Books", "Lightspeed"]);
    expect(onCancel).toHaveBeenCalled();
  });

  it("submits a new rule with the chosen strategy", () => {
    const onSave = vi.fn();
    render(
      <RuleEditor
        open
        onCancel={() => {}}
        onSave={onSave}
        entities={["daily_sales"]}
        fieldsByEntity={{ daily_sales: ["daily_sales"] }}
        initial={null}
      />,
    );
    fireEvent.click(screen.getByRole("radio", { name: /manual override/i }));
    fireEvent.click(screen.getByRole("button", { name: /save rule/i }));
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({
        strategy: "manual",
        entityType: "daily_sales",
        fieldKey: "daily_sales",
      }),
    );
  });

  it("preserves a non-default source priority when editing a priority rule", () => {
    const onSave = vi.fn();
    const productRule: ResolutionRule = {
      id: "rule-2",
      entityType: "product_sales",
      fieldKey: "garlic aioli",
      strategy: "priority",
      sourcePriority: ["Lightspeed", "Cooking the Books"],
      updatedAt: "2026-09-28T09:30:00Z",
      updatedBy: "Stirling Donaldson",
    };
    render(
      <RuleEditor
        open
        onCancel={() => {}}
        onSave={onSave}
        entities={["product_sales"]}
        fieldsByEntity={{ product_sales: ["*", "garlic aioli"] }}
        initial={productRule}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /save rule/i }));
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({
        id: "rule-2",
        strategy: "priority",
        sourcePriority: ["Lightspeed", "Cooking the Books"],
      }),
    );
  });

  it("notes that the product list is demo data", () => {
    render(
      <RuleEditor
        open
        onCancel={() => {}}
        onSave={() => {}}
        entities={["product_sales"]}
        fieldsByEntity={{ product_sales: ["*", "garlic aioli"] }}
        initial={null}
      />,
    );
    expect(screen.getByText(/demo product list/i)).toBeInTheDocument();
  });
});
