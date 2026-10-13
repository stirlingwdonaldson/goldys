import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, within, act } from "@testing-library/react";
import type {
  AssignmentRow,
  CategoryNode,
  ClassificationRun,
  DendrogramNode,
  ProductPoint,
} from "@/lib/api";
import ProductsPage from "./page";

const state = vi.hoisted(() => {
  const store = {
    taxonomy: null as CategoryNode[] | null,
    assignments: null as AssignmentRow[] | null,
    points: null as ProductPoint[] | null,
    dendrogram: null as DendrogramNode[] | null,
    runs: null as ClassificationRun[] | null,
    api: {
      renameCategory: vi.fn(async () => {}),
      createCategory: vi.fn(async () => ({ id: "cat-new", name: "New", groupName: null, productCount: 0 })),
      moveProduct: vi.fn(async () => {}),
      clearProductOverride: vi.fn(async () => {}),
      runClassification: vi.fn(async () => {}),
      listClassificationRuns: vi.fn(async () => store.runs ?? []),
    },
  };
  // The read side routes by invoking the fetcher, not by matching its source text. Each
  // method returns its own dataset's sentinel, read at call time from `store` (which is the
  // object the tests mutate — `Object.assign` returns it), so a renamed method throws, and a
  // wrong-but-existing method returns the wrong dataset's sentinel: both fail loudly below.
  return Object.assign(store, {
    readApi: {
      getCategoryTaxonomy: () => store.taxonomy,
      getCategoryAssignments: () => store.assignments,
      getCategoryEmbeddingSpace: () => store.points,
      getCategoryDendrogram: () => store.dendrogram,
      listClassificationRuns: () => store.runs,
    },
  });
});

vi.mock("@/lib/use-api-data", () => ({
  useApiData: (fetcher: (api: unknown) => unknown) => {
    // No `String(fetcher)` substring here: the fetcher itself does the routing, so a
    // misrouted fetch cannot silently resolve to `null` → `[]`.
    const data = fetcher(state.readApi);
    return { data, loading: false, error: null, reload: async () => {} };
  },
}));

vi.mock("@/lib/demo-mode", () => ({ useApi: () => state.api }));

// The taxonomy tree renders React Flow, which reads the router on mount.
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

// Distinct sentinel fixtures, one per Api method. Each carries a marker (a category name or
// product key) that exists only in its own dataset, so the tests assert that the right
// dataset reached the right view rather than degrading to an empty state.
const taxonomy: CategoryNode[] = [
  { id: "cat-beer", name: "Beer", groupName: null, productCount: 24 },
  { id: "cat-mains", name: "Mains", groupName: null, productCount: 41 },
  { id: "cat-red-wine", name: "Red Wine", groupName: "Wine", productCount: 18 },
  { id: "cat-white-wine", name: "White Wine", groupName: "Wine", productCount: 14 },
];

const assignments: AssignmentRow[] = [
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
  { id: "leaf-1", parentId: "merge-1", isLeaf: true, productNameKey: "pale ale", mergeHeight: 0, size: 1 },
  { id: "leaf-2", parentId: "merge-1", isLeaf: true, productNameKey: "pinot noir", mergeHeight: 0, size: 1 },
  { id: "merge-1", parentId: null, isLeaf: false, productNameKey: null, mergeHeight: 0.42, size: 2 },
];

const points: ProductPoint[] = [
  { productNameKey: "pint carlton draught", x: 0.82, y: 0.11, categoryId: "cat-beer", confidence: 0.94 },
  { productNameKey: "house red", x: 0.71, y: 0.05, categoryId: "cat-red-wine", confidence: 0.88 },
];

const succeededRun: ClassificationRun = {
  id: "cls-1",
  kind: "FULL",
  status: "SUCCEEDED",
  productCount: 3,
  startedAt: "2026-10-13T00:00:00Z",
  finishedAt: "2026-10-13T00:01:00Z",
  errorMessage: null,
};

function seed() {
  state.taxonomy = taxonomy;
  state.assignments = assignments;
  state.points = points;
  state.dendrogram = dendrogram;
  state.runs = [succeededRun];
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

    // Sentinels unique to the taxonomy dataset must reach the tree: a fetch misrouted to
    // any other dataset would leave these names absent and this assertion would throw.
    expect(tree.getByText("Beer")).toBeInTheDocument();
    expect(tree.getByText("Wine")).toBeInTheDocument();
    expect(tree.getByText("Red Wine")).toBeInTheDocument();

    // A null-group category is a root: it shares the group nodes' row, and its grouped
    // siblings sit one level below it.
    expect(nodeY(tree.getByText("Beer"))).toBe(nodeY(tree.getByText("Wine")));
    expect(nodeY(tree.getByText("Red Wine"))).toBeGreaterThan(nodeY(tree.getByText("Wine")));
  });

  it("renders a NEEDS_REVIEW assignment with the warn (status-conflict) treatment", () => {
    seed();
    render(<ProductsPage />);

    // `parma` is the assignment dataset's sentinel: it reaches the queue, not the tree.
    expect(screen.getByText("parma")).toBeInTheDocument();
    // The pill lives in the assignment table; the stat card labels the same queue.
    expect(within(screen.getByRole("table")).getByText("Needs review")).toHaveClass("text-status-conflict");
    expect(within(screen.getByRole("table")).getByText("Unassigned")).toHaveClass("text-status-missing");
  });

  it("renders the taxonomy empty state when there are no categories yet", () => {
    // Only the taxonomy is empty here; the other datasets keep their sentinels, so the
    // empty state comes from the taxonomy itself rather than a wholesale absence of data.
    state.taxonomy = [];
    state.assignments = assignments;
    state.points = points;
    state.dendrogram = dendrogram;
    render(<ProductsPage />);

    expect(screen.getByText("No categories yet")).toBeInTheDocument();
    // The rest of the page still works off the other datasets.
    expect(screen.getByText("parma")).toBeInTheDocument();
  });

  it("renders the three tabs with their visualisation views", () => {
    seed();
    render(<ProductsPage />);

    expect(screen.getByRole("tab", { name: "Categories" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Clusters" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Pipeline" })).toBeInTheDocument();

    // Radix Tabs activate on mouse-down, not click.
    fireEvent.mouseDown(screen.getByRole("tab", { name: "Clusters" }));
    // The dendrogram region is the Clusters view's sentinel: it reaches that tab.
    expect(screen.getByRole("region", { name: /dendrogram/i })).toBeInTheDocument();

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

  it("shows a Run classification button that submits a FULL run", async () => {
    seed();
    render(<ProductsPage />);

    const button = screen.getByRole("button", { name: "Run classification" });
    expect(button).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(button);
    });

    expect(state.api.runClassification).toHaveBeenCalledWith("FULL");
  });

  it("keeps the Run classification button visible in the empty state", () => {
    state.taxonomy = [];
    state.assignments = [];
    state.points = [];
    state.dendrogram = [];
    state.runs = [];
    render(<ProductsPage />);

    expect(screen.getByRole("button", { name: "Run classification" })).toBeInTheDocument();
    expect(screen.getByText(/No classification run has produced data yet/)).toBeInTheDocument();
  });

  it("shows the latest run status without opening the Pipeline tab", () => {
    seed();
    render(<ProductsPage />);

    // The status line is always visible, not gated behind the Pipeline tab, so a run that is
    // submitted from any tab still reports its outcome.
    const status = within(screen.getByTestId("latest-run-status"));
    expect(status.getByText("SUCCEEDED")).toBeInTheDocument();
    expect(status.getByText(/3 products/)).toBeInTheDocument();
  });

  it("shows a failed run's error message regardless of tab", () => {
    seed();
    state.runs = [
      {
        id: "cls-2",
        kind: "FULL",
        status: "FAILED",
        productCount: null,
        startedAt: "2026-10-13T00:00:00Z",
        finishedAt: "2026-10-13T00:01:00Z",
        errorMessage: "Product embedding classification is disabled",
      },
    ];
    render(<ProductsPage />);

    expect(screen.getByText("Product embedding classification is disabled")).toBeInTheDocument();
  });

  it("shows Running… and disables the trigger while a run is in flight", () => {
    seed();
    state.runs = [
      {
        id: "cls-3",
        kind: "FULL",
        status: "RUNNING",
        productCount: null,
        startedAt: "2026-10-13T00:00:00Z",
        finishedAt: null,
        errorMessage: null,
      },
    ];
    render(<ProductsPage />);

    expect(screen.getByRole("button", { name: "Running…" })).toBeDisabled();
    expect(screen.getByText("Running — this can take a moment.")).toBeInTheDocument();
  });
});
