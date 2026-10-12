import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import type { RecipeCosting } from "@/lib/api";
import RecipesPage from "./page";

const state = vi.hoisted(() => ({ recipes: null as RecipeCosting[] | null }));

vi.mock("@/lib/use-api-data", () => ({
  useApiData: () => ({
    data: state.recipes,
    loading: false,
    error: null,
    reload: async () => {},
  }),
}));

describe("RecipesPage", () => {
  it("renders recipe costing rows", () => {
    state.recipes = [
      {
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
      },
    ];
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
});
