import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { RecipeCosting, RecipeIngredient } from "@/lib/api";
import RecipesPage from "./page";

const state = vi.hoisted(() => ({
  recipes: null as RecipeCosting[] | null,
  ingredients: null as RecipeIngredient[] | null,
}));

// The recipes fetch passes no deps; the ingredient-panel fetch passes `[recipeId]`.
vi.mock("@/lib/use-api-data", () => ({
  useApiData: (_fetcher: unknown, deps: unknown[] = []) =>
    deps.length > 0
      ? { data: state.ingredients, loading: false, error: null, reload: async () => {} }
      : { data: state.recipes, loading: false, error: null, reload: async () => {} },
}));

const parma: RecipeCosting = {
  recipeId: 1,
  name: "Parma",
  folder: "Mains",
  portionSize: "1 serve",
  costPerPortion: 6.0,
  actualCost: 7.2,
  costGap: 1.2,
  menuPrice: 18.5,
  margin: 12.5,
  gpPercent: 0.6757,
};

describe("RecipesPage", () => {
  it("renders recipe costing rows", () => {
    state.recipes = [parma];
    render(<RecipesPage />);
    expect(screen.getByText("Parma")).toBeInTheDocument();
    expect(screen.getByText("Mains")).toBeInTheDocument();
    expect(screen.getByText("1 serve")).toBeInTheDocument();
    expect(screen.getByText("Recipe costing")).toBeInTheDocument();
  });

  it("renders empty state when there are no recipes", () => {
    state.recipes = [];
    render(<RecipesPage />);
    expect(screen.getByText("Awaiting Cooking the Books recipe data.")).toBeInTheDocument();
  });

  it("renders the ingredient breakdown with sub-recipe grouping", () => {
    state.recipes = [parma];
    state.ingredients = [
      {
        parentRecipeId: 1,
        lineRecipeId: 1,
        lineRecipeName: "Parma",
        isSubRecipe: false,
        recipeDetailId: 101,
        stockId: 42,
        stockCode: "CHICKEN",
        stockDescription: "Chicken schnitzel",
        portionWeight: 200,
        unitOfMeasurement: "grams",
        costPerQty: 3.2,
        costPerUnit: 0.016,
        supplierName: "Butcher",
      },
      {
        parentRecipeId: 1,
        lineRecipeId: 900,
        lineRecipeName: "Aioli",
        isSubRecipe: true,
        recipeDetailId: 201,
        stockId: 7,
        stockCode: "GARLIC",
        stockDescription: "Garlic",
        portionWeight: 5,
        unitOfMeasurement: "grams",
        costPerQty: 0.1,
        costPerUnit: 0.02,
        supplierName: "Greengrocer",
      },
    ];
    render(<RecipesPage />);

    fireEvent.click(screen.getByRole("button", { name: "Show ingredients for Parma" }));

    expect(screen.getByText("Parma — ingredients")).toBeInTheDocument();
    expect(screen.getByText("Chicken schnitzel")).toBeInTheDocument();
    expect(screen.getByText("Sub-recipe: Aioli")).toBeInTheDocument();
    expect(screen.getByText("Garlic")).toBeInTheDocument();
  });

  it("renders an empty state for a recipe with no ingredient lines", () => {
    state.recipes = [parma];
    state.ingredients = [];
    render(<RecipesPage />);

    fireEvent.click(screen.getByRole("button", { name: "Show ingredients for Parma" }));

    expect(screen.getByText("No ingredients recorded")).toBeInTheDocument();
  });
});
