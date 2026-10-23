import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { demoRecipeComposition } from "@/lib/api/demo-recipe-composition";
import type { RecipeComposition } from "@/lib/api/types";
import { RecipeCompositionPanel } from "./recipe-composition-panel";

const state = vi.hoisted(() => ({ data: null as RecipeComposition | null, error: null as { message: string } | null }));
vi.mock("@/lib/use-api-data", () => ({
  useApiData: () => ({ ...state, loading: false, reload: async () => {} }),
}));

describe("RecipeCompositionPanel", () => {
  beforeEach(() => { state.data = null; state.error = null; });

  it("shows missing details without fabricating an empty recipe", () => {
    render(<RecipeCompositionPanel recipeId={3} />);
    expect(screen.getByText(/Complete recipe details have not been received/)).toBeInTheDocument();
  });

  it("keeps CTB tax bases separate and exposes stale and incomplete estimates", () => {
    state.data = demoRecipeComposition(3);
    render(<RecipeCompositionPanel recipeId={3} />);
    expect(screen.getByText("CTB selling price incl. GST")).toBeInTheDocument();
    expect(screen.getByText("CTB revenue excl. GST")).toBeInTheDocument();
    expect(screen.getByText(/Refresh overdue/)).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("A complete cost estimate is unavailable");
    expect(screen.getByText(/Burger sauce: 60 ml from a 2 litres yield/)).toBeInTheDocument();
  });

  it("shows an explicit permission error", () => {
    state.error = { message: "Not permitted" };
    render(<RecipeCompositionPanel recipeId={3} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Not permitted");
  });
});
