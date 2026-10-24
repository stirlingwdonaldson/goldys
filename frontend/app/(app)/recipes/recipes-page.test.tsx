import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { RecipeCosting } from "@/lib/api";
import RecipesPage from "./page";

vi.mock("@/components/recipes/recipe-folder-diagram", () => ({
  RecipeFolderDiagram: () => null,
}));

const state = vi.hoisted(() => ({
  recipes: [] as RecipeCosting[],
  params: new URLSearchParams(),
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => state.params,
  usePathname: () => "/recipes",
}));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({}) }));
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: state.recipes,
    isPending: false,
    isLoadingError: false,
  }),
}));
const recipe: RecipeCosting = {
  recipeId: 1,
  name: "Parma",
  folder: "Mains",
  portionSize: "1 serve",
  costPerPortion: 6,
  actualCost: null,
  costGap: null,
  menuPrice: 18.5,
  margin: null,
  gpPercent: null,
  menuState: "UNKNOWN",
  priceSource: "CURRENT",
  lastSoldPrice: null,
  lastSoldCost: null,
  lastSoldDate: null,
  coverage: "COMPLETE",
};

describe("RecipesPage", () => {
  it("shows six focused columns with one entry to a recipe", () => {
    state.params = new URLSearchParams();
    state.recipes = [recipe];
    render(<RecipesPage />);
    expect(screen.getAllByRole("columnheader")).toHaveLength(6);
    expect(screen.getByRole("link", { name: "Parma" })).toHaveAttribute(
      "href",
      "/recipes/1?from=%2Frecipes",
    );
    expect(screen.getByText("1 serve")).toBeInTheDocument();
    expect(screen.queryByText("Avg GP%")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Ingredients" }),
    ).not.toBeInTheDocument();
  });
  it("filters exact folders and retains filter context in the recipe link", () => {
    state.params = new URLSearchParams("folder=Mains");
    state.recipes = [
      recipe,
      { ...recipe, recipeId: 2, name: "Sauce", folder: "Prep" },
    ];
    render(<RecipesPage />);
    expect(
      screen.queryByRole("link", { name: "Sauce" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Parma" })).toHaveAttribute(
      "href",
      "/recipes/1?from=%2Frecipes%3Ffolder%3DMains",
    );
    fireEvent.change(screen.getByLabelText("Search recipes"), {
      target: { value: "Par" },
    });
    expect(window.location.search).toContain("search=Par");
  });
  it("keeps historical observations dated and missing prices explicit", () => {
    state.params = new URLSearchParams();
    state.recipes = [
      {
        ...recipe,
        priceSource: "HISTORICAL",
        lastSoldPrice: 17,
        lastSoldDate: "2026-09-14",
      },
      {
        ...recipe,
        recipeId: 2,
        name: null,
        priceSource: "NONE",
        menuPrice: null,
      },
    ];
    render(<RecipesPage />);
    expect(screen.getByText("$17.00")).toBeInTheDocument();
    expect(screen.getByText(/Last sold/)).toHaveTextContent("Sep");
    expect(screen.getByText("No price matched")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Unnamed recipe" }),
    ).toBeInTheDocument();
  });
});
