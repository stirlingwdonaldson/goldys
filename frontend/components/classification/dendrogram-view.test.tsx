import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { AssignmentRow, DendrogramNode } from "@/lib/api";
import { DendrogramView } from "./dendrogram-view";

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
  it("renders the merge tree with an accessible region and selectable nodes", () => {
    render(<DendrogramView nodes={nodes} assignments={assignments} onCurate={() => {}} />);

    expect(screen.getByRole("region", { name: /dendrogram/i })).toBeInTheDocument();
    // Product leaves are drawn as visible (rotated) labels and are selectable buttons.
    expect(screen.getByRole("button", { name: "pint carlton draught" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "house red" })).toBeInTheDocument();
    // The internal merge node is a keyboard-reachable button named by its leaf count.
    expect(screen.getByRole("button", { name: /merge of 2 products/i })).toBeInTheDocument();
  });

  it("selects a node and offers curation of the cluster it covers", () => {
    const onCurate = vi.fn();
    render(<DendrogramView nodes={nodes} assignments={assignments} onCurate={onCurate} />);

    fireEvent.click(screen.getByRole("button", { name: /merge of 2 products/i }));

    // The action bar names the selected cluster's category (also in the colour legend).
    expect(screen.getAllByText("Beer").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /merge into one category/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /split/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /merge into one category/i }));
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

    fireEvent.click(screen.getByRole("button", { name: /merge of 2 products/i }));
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
