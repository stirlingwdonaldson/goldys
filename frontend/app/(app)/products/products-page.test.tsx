import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, within } from "@testing-library/react";
import type { AssignmentRow, CategoryNode, DendrogramNode, ProductPoint } from "@/lib/api";
import ProductsPage from "./page";

const state = vi.hoisted(() => ({
  taxonomy: null as CategoryNode[] | null,
  assignments: null as AssignmentRow[] | null,
  points: null as ProductPoint[] | null,
  dendrogram: null as DendrogramNode[] | null,
  api: {
    renameCategory: vi.fn(async () => {}),
    createCategory: vi.fn(async () => ({ id: "cat-new", name: "New", groupName: null, productCount: 0 })),
    moveProduct: vi.fn(async () => {}),
    clearProductOverride: vi.fn(async () => {}),
  },
}));

// Same harness shape as the recipes page test, keyed on the Api method the fetcher calls
// (the page makes four unmemoised fetches, so a deps-length key would not tell them apart).
vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => unknown) => {
    const source = String(fetcher);
    const data = source.includes("getCategoryTaxonomy")
      ? state.taxonomy
      : source.includes("getCategoryAssignments")
        ? state.assignments
        : source.includes("getCategoryEmbeddingSpace")
          ? state.points
          : source.includes("getCategoryDendrogram")
            ? state.dendrogram
            : null;
    return { data, loading: false, error: null, reload: async () => {} };
  },
}));

vi.mock("@/lib/demo-mode", () => ({ useApi: () => state.api }));

// The taxonomy tree renders React Flow, which reads the router on mount.
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const categories: CategoryNode[] = [
  { id: "cat-beer", name: "Beer", groupName: null, productCount: 24 },
  { id: "cat-mains", name: "Mains", groupName: null, productCount: 41 },
  { id: "cat-red-wine", name: "Red Wine", groupName: "Wine", productCount: 18 },
  { id: "cat-white-wine", name: "White Wine", groupName: "Wine", productCount: 14 },
];

const rows: AssignmentRow[] = [
  {
    productNameKey: "chicken schnitzel",
    categoryId: "cat-mains",
    categoryName: "Mains",
    groupName: null,
    confidence: 0.9,
    source: "CLUSTERED",
    status: "ASSIGNED",
  },
  {
    productNameKey: "parma",
    categoryId: "cat-mains",
    categoryName: "Mains",
    groupName: null,
    confidence: 0.61,
    source: "CLUSTERED",
    status: "NEEDS_REVIEW",
  },
  {
    productNameKey: "garlic aioli",
    categoryId: null,
    categoryName: null,
    groupName: null,
    confidence: null,
    source: null,
    status: "UNASSIGNED",
  },
];

const dendrogram: DendrogramNode[] = [
  { id: "leaf-1", parentId: "merge-1", isLeaf: true, productNameKey: "pint carlton draught", mergeHeight: 0, size: 1 },
  { id: "leaf-2", parentId: "merge-1", isLeaf: true, productNameKey: "house red", mergeHeight: 0, size: 1 },
  { id: "merge-1", parentId: null, isLeaf: false, productNameKey: null, mergeHeight: 0.42, size: 2 },
];

const points: ProductPoint[] = [
  { productNameKey: "pint carlton draught", x: 0.82, y: 0.11, categoryId: "cat-beer", confidence: 0.94 },
  { productNameKey: "garlic aioli", x: 0.2, y: -0.3, categoryId: null, confidence: null },
];

function seed() {
  state.taxonomy = categories;
  state.assignments = rows;
  state.points = points;
  state.dendrogram = dendrogram;
}

/** The taxonomy canvas: category names also appear in the move selects, so scope to it. */
function taxonomyTree() {
  return within(screen.getByRole("region", { name: /taxonomy/i }));
}

/** A React Flow node's laid-out y, read from the node wrapper's transform. */
function nodeY(nodeTitle: HTMLElement): number {
  const node = nodeTitle.closest(".react-flow__node") as HTMLElement;
  const match = /translate\([^,]+,\s*(-?[\d.]+)px\)/.exec(node.style.transform);
  return Number(match?.[1] ?? Number.NaN);
}

describe("ProductsPage", () => {
  it("renders the taxonomy with grouped categories and flat (null-group) categories at the top level", () => {
    seed();
    render(<ProductsPage />);

    const tree = taxonomyTree();
    expect(screen.getByRole("heading", { name: "Products" })).toBeInTheDocument();
    expect(tree.getByText("Wine")).toBeInTheDocument();
    expect(tree.getByText("Red Wine")).toBeInTheDocument();
    expect(tree.getByText("Beer")).toBeInTheDocument();

    // A null-group category is a root: it shares the group nodes' row, and its grouped
    // siblings sit one level below it.
    expect(nodeY(tree.getByText("Beer"))).toBe(nodeY(tree.getByText("Wine")));
    expect(nodeY(tree.getByText("Red Wine"))).toBeGreaterThan(nodeY(tree.getByText("Wine")));
  });

  it("renders a NEEDS_REVIEW assignment with the warn (status-conflict) treatment", () => {
    seed();
    render(<ProductsPage />);

    expect(screen.getByText("parma")).toBeInTheDocument();
    // The pill lives in the assignment table; the stat card labels the same queue.
    expect(within(screen.getByRole("table")).getByText("Needs review")).toHaveClass("text-status-conflict");
    expect(within(screen.getByRole("table")).getByText("Unassigned")).toHaveClass("text-status-missing");
  });

  it("renders the empty state when there is no categorisation data at all", () => {
    state.taxonomy = [];
    state.assignments = [];
    state.points = [];
    state.dendrogram = [];
    render(<ProductsPage />);

    expect(screen.getByText("No classification run has produced data yet.")).toBeInTheDocument();
  });

  it("renders the three tabs with their visualisation views", () => {
    seed();
    render(<ProductsPage />);

    expect(screen.getByRole("tab", { name: "Categories" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Clusters" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Pipeline" })).toBeInTheDocument();

    // Radix Tabs activate on mouse-down, not click.
    fireEvent.mouseDown(screen.getByRole("tab", { name: "Clusters" }));
    expect(screen.getByText("Merge of 2")).toBeInTheDocument();

    fireEvent.mouseDown(screen.getByRole("tab", { name: "Pipeline" }));
    expect(screen.getByRole("region", { name: /pipeline/i })).toBeInTheDocument();
  });

  it("moves a product to another category from the assignment queue", () => {
    seed();
    render(<ProductsPage />);

    fireEvent.change(screen.getByLabelText("Move parma to a category"), {
      target: { value: "cat-beer" },
    });
    expect(state.api.moveProduct).toHaveBeenCalledWith("parma", "cat-beer");
  });

  it("drills into a category from the taxonomy tree and renames it", () => {
    seed();
    render(<ProductsPage />);

    fireEvent.click(taxonomyTree().getByText("Beer"));
    expect(screen.getByRole("heading", { name: "Beer" })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Category name"), { target: { value: "Ales & Lagers" } });
    fireEvent.click(screen.getByRole("button", { name: "Save name" }));
    expect(state.api.renameCategory).toHaveBeenCalledWith("cat-beer", { name: "Ales & Lagers" });
  });
});
