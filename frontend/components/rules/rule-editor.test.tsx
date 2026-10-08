import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { RuleEditor } from "./rule-editor";
import type { ResolutionRule } from "@/lib/api";

const existing: ResolutionRule = {
  id: "rule-1",
  entityType: "daily_sales",
  fieldKey: "daily_sales",
  strategy: "priority",
  sourcePriority: ["CTB", "LIGHTSPEED"],
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
    expect(existing.sourcePriority).toEqual(["CTB", "LIGHTSPEED"]);
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
      sourcePriority: ["LIGHTSPEED", "CTB"],
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
        sourcePriority: ["LIGHTSPEED", "CTB"],
      }),
    );
  });

  it("reorders sources with the up/down controls and submits the new priority", () => {
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
    // Default order is Lightspeed first; move "Cooking the Books" up to the top.
    fireEvent.click(screen.getByRole("button", { name: /move cooking the books up/i }));
    fireEvent.click(screen.getByRole("button", { name: /save rule/i }));
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({
        strategy: "priority",
        sourcePriority: ["CTB", "LIGHTSPEED"],
      }),
    );
  });

  it("notes that the product list is demo data only in demo mode", () => {
    render(
      <RuleEditor
        open
        onCancel={() => {}}
        onSave={() => {}}
        entities={["product_sales"]}
        fieldsByEntity={{ product_sales: ["*", "garlic aioli"] }}
        demo
        initial={null}
      />,
    );
    expect(screen.getByText(/demo product list/i)).toBeInTheDocument();
  });

  it("does not flag the product list as demo in live mode", () => {
    render(
      <RuleEditor
        open
        onCancel={() => {}}
        onSave={() => {}}
        entities={["product_sales"]}
        fieldsByEntity={{ product_sales: ["*", "garlic aioli"] }}
        demo={false}
        initial={null}
      />,
    );
    expect(screen.queryByText(/demo product list/i)).not.toBeInTheDocument();
  });
});
