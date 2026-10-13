import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { AssignmentRow, DendrogramNode } from "@/lib/api";
import { DendrogramView } from "./dendrogram-view";

// React Flow reads the router on mount.
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const nodes: DendrogramNode[] = [
  { id: "leaf-1", parentId: "merge-1", isLeaf: true, productNameKey: "pint carlton draught", mergeHeight: 0, size: 1 },
  { id: "leaf-2", parentId: "merge-1", isLeaf: true, productNameKey: "house red", mergeHeight: 0, size: 1 },
  { id: "merge-1", parentId: null, isLeaf: false, productNameKey: null, mergeHeight: 0.42, size: 2 },
];

const assignments: AssignmentRow[] = [
  {
    productNameKey: "pint carlton draught",
    categoryId: "cat-beer",
    categoryName: "Beer",
    groupName: null,
    confidence: 0.94,
    source: "CLUSTERED",
    status: "ASSIGNED",
  },
  {
    productNameKey: "house red",
    categoryId: "cat-beer",
    categoryName: "Beer",
    groupName: null,
    confidence: 0.91,
    source: "CLUSTERED",
    status: "ASSIGNED",
  },
];

describe("DendrogramView", () => {
  it("renders the merge tree, with an accessible region label", () => {
    render(<DendrogramView nodes={nodes} assignments={assignments} onCurate={() => {}} />);

    expect(screen.getByRole("region", { name: /dendrogram/i })).toBeInTheDocument();
    expect(screen.getByText("pint carlton draught")).toBeInTheDocument();
    expect(screen.getByText("house red")).toBeInTheDocument();
    expect(screen.getByText("Merge of 2")).toBeInTheDocument();
  });

  it("selects a node and offers curation of the cluster it covers", () => {
    const onCurate = vi.fn();
    render(<DendrogramView nodes={nodes} assignments={assignments} onCurate={onCurate} />);

    fireEvent.click(screen.getByText("Merge of 2"));

    // The action bar names what the selected cluster covers: two products, one shared category.
    expect(screen.getByText("Beer")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /merge/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /split/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /merge/i }));
    expect(onCurate).toHaveBeenCalledWith(
      "merge",
      expect.objectContaining({
        node: expect.objectContaining({ id: "merge-1" }),
        categoryId: "cat-beer",
      }),
    );
  });

  it("renames through an inline form when the cluster already has a category", () => {
    const onCurate = vi.fn();
    render(<DendrogramView nodes={nodes} assignments={assignments} onCurate={onCurate} />);

    fireEvent.click(screen.getByText("Merge of 2"));
    fireEvent.click(screen.getByRole("button", { name: /rename/i }));
    fireEvent.change(screen.getByLabelText("New name"), { target: { value: "Draught beer" } });
    fireEvent.click(screen.getByRole("button", { name: "Save name" }));

    expect(onCurate).toHaveBeenCalledWith(
      "rename",
      expect.objectContaining({ node: expect.objectContaining({ id: "merge-1" }) }),
      "Draught beer",
    );
  });

  it("renders an empty state when no clustering run exists", () => {
    render(<DendrogramView nodes={[]} assignments={[]} onCurate={() => {}} />);
    expect(screen.getByText(/no clustering run/i)).toBeInTheDocument();
  });
});
