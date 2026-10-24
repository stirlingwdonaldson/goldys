import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { demoRecipeComposition } from "@/lib/api/demo-recipe-composition";
import { RecipeCompositionPanel } from "./recipe-composition-panel";
vi.mock("./recipe-ingredient-diagram", () => ({
  RecipeIngredientDiagram: () => null,
}));

describe("RecipeCompositionPanel", () => {
  it("keeps tax bases separate and links an explicitly measured child", () => {
    const data = demoRecipeComposition(3)!;
    data.calculatedTotal = null;
    data.issues.push("MISSING_SUB_RECIPE:8");
    render(<RecipeCompositionPanel data={data} />);
    expect(screen.getByText("CTB selling price incl. GST")).toBeInTheDocument();
    expect(screen.getByText("CTB revenue excl. GST")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "A complete cost estimate is unavailable",
    );
    expect(screen.getByRole("link", { name: "Burger sauce" })).toHaveAttribute(
      "href",
      "/recipes/8",
    );
    expect(screen.getByText("60 ml")).toBeInTheDocument();
    expect(screen.getByText("From 2 litres yield")).toBeInTheDocument();
  });
  it("retains negative adjustments and does not call child-only compositions empty", () => {
    const data = demoRecipeComposition(3)!;
    data.definition.stocks = [];
    data.definition.usages[0].quantity = -15;
    data.definition.usages[0].unit = "litres";
    render(<RecipeCompositionPanel data={data} />);
    expect(screen.getByText("Adjustment")).toBeInTheDocument();
    expect(screen.getByText(/-15 litres/)).toBeInTheDocument();
    expect(
      screen.queryByText(/No ingredient or sub-recipe lines/),
    ).not.toBeInTheDocument();
  });
  it("labels a captured empty composition and stock export without inventing a method", () => {
    const data = demoRecipeComposition(3)!;
    data.definition.stocks = [];
    data.definition.usages = [];
    data.definition.header.description =
      "This recipe is exported from a stock item";
    render(<RecipeCompositionPanel data={data} />);
    expect(
      screen.getByText(/No ingredient or sub-recipe lines/),
    ).toBeInTheDocument();
    expect(screen.getByText("Stock-derived recipe")).toBeInTheDocument();
    expect(screen.getByText(/no cooking method supplied/)).toBeInTheDocument();
  });
});
