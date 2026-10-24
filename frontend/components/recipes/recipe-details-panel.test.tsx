import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { demoRecipeComposition } from "@/lib/api/demo-recipe-composition";
import { demoRecipeDetails } from "@/lib/api/demo-recipe-details";
import { RecipeDetailsPanel } from "./recipe-details-panel";
const state = vi.hoisted(() => ({
  data: null as ReturnType<typeof demoRecipeDetails>,
  error: null as { message: string } | null,
  composition: null as ReturnType<typeof demoRecipeComposition>,
}));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({}) }));
vi.mock("@tanstack/react-query", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@tanstack/react-query")>()),
  useQuery: ({ queryKey }: { queryKey: string[] }) => ({
    ...state,
    data:
      queryKey[0] === "getRecipeComposition" ? state.composition : state.data,
    isPending: false,
    isLoadingError: !!state.error,
    refetch: async () => {},
  }),
}));
describe("RecipeDetailsPanel", () => {
  beforeEach(() => {
    state.composition = null;
    state.error = null;
  });
  it("retains list context through explicit child links", () => {
    state.data = demoRecipeDetails(3);
    state.composition = demoRecipeComposition(3);
    render(
      <RecipeDetailsPanel recipeId={3} listHref="/recipes?folder=Mains" />,
    );
    expect(screen.getByRole("link", { name: "Burger sauce" })).toHaveAttribute(
      "href",
      "/recipes/8?from=%2Frecipes%3Ffolder%3DMains",
    );
  });
  it("shows a known zero legacy cost in the primary view", () => {
    state.data = demoRecipeDetails(3)!;
    state.data.sections = [
      {
        ...state.data.sections[0],
        section: "LEGACY_COST",
        fields: [{ path: "costPerPortion", value: "0", type: "NUMBER" }],
      },
    ];
    render(<RecipeDetailsPanel recipeId={3} />);
    expect(screen.getByText("$0.00")).toBeVisible();
    expect(screen.getByText("Recorded CTB costs")).toBeVisible();
  });
  it("links child recipes, presents source times and distinguishes zero, empty and failure", () => {
    state.data = demoRecipeDetails(3);
    render(<RecipeDetailsPanel recipeId={3} />);
    expect(screen.getByRole("link", { name: /Burger sauce/ })).toHaveAttribute(
      "href",
      "/recipes/8",
    );
    expect(screen.getAllByText(/Mix thoroughly/)[0]).toHaveTextContent(
      "Rest for 10 minutes.",
    );
    expect(
      screen.getByText(/CTB reports zero for every recorded nutrient/),
    ).toBeInTheDocument();
    expect(screen.getByText("No entries recorded")).toBeInTheDocument();
    expect(
      screen.getByText(/Failed; previous successful values retained/),
    ).toBeInTheDocument();
    expect(screen.getAllByText(/Source: CTB/).length).toBeGreaterThan(0);
    expect(
      screen.getByText("Source details & capture history").closest("details"),
    ).not.toHaveAttribute("open");
  });
  it("links back to parent recipes", () => {
    state.data = demoRecipeDetails(8);
    render(<RecipeDetailsPanel recipeId={8} />);
    expect(screen.getByRole("link", { name: /Cheeseburger/ })).toHaveAttribute(
      "href",
      "/recipes/3",
    );
  });
  it("renders source strings as text rather than HTML", () => {
    state.data = demoRecipeDetails(3)!;
    state.data.sections[0].fields[0].value = '<img src=x onerror="alert(1)">';
    const { container } = render(<RecipeDetailsPanel recipeId={3} />);
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getAllByText(/<img src=x/)[0]).toBeInTheDocument();
  });
  it("links an authorised capture to its raw evidence and lists exact field paths", () => {
    state.data = demoRecipeDetails(3)!;
    state.data.sections[0].evidence = {
      ...state.data.sections[0].evidence,
      rawRecordId: "5a668b37-ab9a-48cf-b940-67cf9f77179a",
    };
    render(<RecipeDetailsPanel recipeId={3} />);
    expect(
      screen.getByRole("link", {
        name: "Inspect retained response",
        hidden: true,
      }),
    ).toHaveAttribute(
      "href",
      "/data?layer=raw&id=5a668b37-ab9a-48cf-b940-67cf9f77179a",
    );
    expect(screen.getByText("recipeDesc")).toBeInTheDocument();
  });
  it("shows permission errors", () => {
    state.error = { message: "Not permitted" };
    render(<RecipeDetailsPanel recipeId={3} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Not permitted");
    state.error = null;
  });
});
