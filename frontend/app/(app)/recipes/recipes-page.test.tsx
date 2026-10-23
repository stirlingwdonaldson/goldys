import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { RecipeCosting, RecipeIngredient } from "@/lib/api";
import RecipesPage from "./page";

vi.mock("@/components/recipes/recipe-composition-panel", () => ({ RecipeCompositionPanel: () => null }));

const state = vi.hoisted(() => ({
  recipes: null as RecipeCosting[] | null,
  ingredients: null as RecipeIngredient[] | null,
}));

// The recipes fetch passes no deps; the ingredient-panel fetch passes `[recipeId]`.
vi.mock("@tanstack/react-query", async (importOriginal) => ({
  ...await importOriginal<typeof import("@tanstack/react-query")>(),
  useQuery: ({ queryKey }: { queryKey: unknown[] }) =>
    queryKey.length > 1
      ? { data: state.ingredients, isPending: false, error: null, refetch: async () => {} }
      : { data: state.recipes, isPending: false, error: null, refetch: async () => {} },
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
  menuState: "ON_MENU",
  priceSource: "CURRENT",
  lastSoldPrice: null,
  lastSoldCost: null,
  lastSoldDate: null,
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

  it("renders the last-sold note and the off-menu label", () => {
    state.recipes = [
      {
        recipeId: 5,
        name: "Eggplant Parmigiana",
        folder: "Mains",
        portionSize: "1",
        costPerPortion: 4.2,
        actualCost: null,
        costGap: 0.6,
        menuPrice: null,
        margin: 12.8,
        gpPercent: 0.7529,
        menuState: "OFF_MENU",
        priceSource: "HISTORICAL",
        lastSoldPrice: 17.0,
        lastSoldCost: 4.8,
        lastSoldDate: "2026-09-14",
      },
      {
        recipeId: 6,
        name: "Chef's Special",
        folder: "Mains",
        portionSize: "1",
        costPerPortion: 3.0,
        actualCost: null,
        costGap: null,
        menuPrice: null,
        margin: null,
        gpPercent: null,
        menuState: "OFF_MENU",
        priceSource: "NONE",
        lastSoldPrice: null,
        lastSoldCost: null,
        lastSoldDate: null,
      },
    ];
    render(<RecipesPage />);
    expect(screen.getAllByText(/Last sold/)).toHaveLength(2);
    expect(screen.getByText("$17.00")).toBeInTheDocument();
    expect(screen.getByText("$4.80")).toBeInTheDocument();
    expect(screen.getByText("No current catalogue match")).toBeInTheDocument();
  });

  it("renders empty state when there are no recipes", () => {
    state.recipes = [];
    render(<RecipesPage />);
    expect(screen.getByText("Awaiting Cooking the Books recipe data.")).toBeInTheDocument();
  });

  it("labels a recipe with no name instead of leaving the cell blank", () => {
    state.recipes = [
      {
        recipeId: 7,
        name: "",
        folder: "",
        portionSize: "1",
        costPerPortion: 0,
        actualCost: null,
        costGap: null,
        menuPrice: null,
        margin: null,
        gpPercent: null,
        menuState: "UNKNOWN",
        priceSource: "NONE",
        lastSoldPrice: null,
        lastSoldCost: null,
        lastSoldDate: null,
      },
    ];
    render(<RecipesPage />);
    expect(screen.getByText("Unnamed recipe")).toBeInTheDocument();
    expect(screen.getByText("Availability unknown")).toBeInTheDocument();
  });

  it("gives a negative margin the amber needs-attention tone", () => {
    state.recipes = [{ ...parma, recipeId: 9, margin: -14.24, gpPercent: -1.999 }];
    render(<RecipesPage />);
    expect(screen.getByText("-$14.24").className).toContain("text-status-conflict");
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
        stockUomShortname: "kg", stockUsable: 0, stockWastage: null, isBrokenLink: true,
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
        stockUomShortname: "kg", stockUsable: 0, stockWastage: null, isBrokenLink: true,
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

vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({}), useDemoMode: () => ({ demo: true }) }));
