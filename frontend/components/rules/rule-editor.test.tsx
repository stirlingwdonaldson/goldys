import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { RuleEditor } from "./rule-editor";
import type { ResolutionRule } from "@/lib/api";

const existing: ResolutionRule = {
  id: "rule-1",
  entity: "Sales",
  field: "quantity_sold",
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
        entities={["Sales"]}
        fieldsByEntity={{ Sales: ["quantity_sold", "net_amount"] }}
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
        entities={["Sales"]}
        fieldsByEntity={{ Sales: ["quantity_sold", "net_amount"] }}
        initial={null}
      />,
    );
    fireEvent.click(screen.getByRole("radio", { name: /manual override/i }));
    fireEvent.click(screen.getByRole("button", { name: /save rule/i }));
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ strategy: "manual", entity: "Sales", field: "quantity_sold" }),
    );
  });

  it("preserves a non-default source priority when editing a priority rule", () => {
    const onSave = vi.fn();
    const shiftsRule: ResolutionRule = {
      id: "rule-2",
      entity: "Shifts",
      field: "hours_worked",
      strategy: "priority",
      sourcePriority: ["Deputy", "Lightspeed"],
      updatedAt: "2026-09-28T09:30:00Z",
      updatedBy: "Stirling Donaldson",
    };
    render(
      <RuleEditor
        open
        onCancel={() => {}}
        onSave={onSave}
        entities={["Shifts"]}
        fieldsByEntity={{ Shifts: ["hours_worked"] }}
        initial={shiftsRule}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /save rule/i }));
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({
        id: "rule-2",
        strategy: "priority",
        sourcePriority: ["Deputy", "Lightspeed"],
      }),
    );
  });
});
