import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { NetworkExplorer } from "./network-explorer";
import { NetworkList } from "./network-list";
import type { NetworkGraph, NetworkKind } from "./types";

// The canvas needs a real 2D context; the list beside it carries the same data.
vi.mock("./network-canvas", () => ({
  NetworkCanvas: ({ graph }: { graph: NetworkGraph }) => (
    <div data-testid="canvas">{graph.nodes.length} nodes</div>
  ),
}));
vi.mock("@/components/hooks/use-mobile", () => ({ useIsMobile: () => false }));

const graph: NetworkGraph = {
  nodes: [
    { id: "source:CTB", kind: "source", label: "Cooking the Books", tone: "fail", size: 6 },
    { id: "domain:sales", kind: "domain", label: "Daily sales", tone: "fail", size: 5 },
    { id: "metric:gross", kind: "metric", label: "Gross sales", tone: "fail", size: 3, href: "/data-health?metric=sales.gross" },
    { id: "metric:covers", kind: "metric", label: "Covers", tone: "ok", size: 3 },
    { id: "permission:sales", kind: "permission", label: "reconciliation.sales", tone: "neutral", size: 2 },
  ],
  edges: [
    { source: "source:CTB", target: "domain:sales", weight: 1 },
    { source: "domain:sales", target: "metric:gross", weight: 0.4, label: "weak" },
    { source: "metric:gross", target: "permission:sales", weight: 1 },
    { source: "metric:covers", target: "metric:gross", weight: 0.9, label: "strong" },
  ],
};

const kinds: { kind: NetworkKind; label: string }[] = [
  { kind: "source", label: "Sources" },
  { kind: "domain", label: "Domains" },
  { kind: "metric", label: "Metrics" },
  { kind: "permission", label: "Permissions" },
];

function renderExplorer(props: Partial<Parameters<typeof NetworkExplorer>[0]> = {}) {
  return render(
    <NetworkExplorer
      graph={graph}
      graphKey="test"
      ariaLabel="Test graph"
      kinds={kinds}
      colourFor={() => "#999"}
      {...props}
    />,
  );
}

describe("NetworkList", () => {
  it("groups nodes by kind and announces problem tones", () => {
    const onFocus = vi.fn();
    render(<NetworkList graph={graph} kinds={kinds} onFocus={onFocus} />);
    const sources = screen.getByRole("region", { name: "Sources" });
    expect(within(sources).getByText("Cooking the Books")).toBeInTheDocument();
    expect(within(sources).getByText("(Failing)")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Covers/ }));
    expect(onFocus).toHaveBeenCalledWith("metric:covers");
  });
});

describe("NetworkExplorer", () => {
  it("hides a kind when its toggle is turned off", () => {
    renderExplorer();
    expect(screen.getByText("reconciliation.sales")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Permissions/, pressed: true }));
    expect(screen.queryByText("reconciliation.sales")).not.toBeInTheDocument();
    expect(screen.getByTestId("canvas")).toHaveTextContent("4 nodes");
  });

  it("respects kinds hidden by default", () => {
    renderExplorer({ hiddenByDefault: ["permission"] });
    expect(screen.queryByText("reconciliation.sales")).not.toBeInTheDocument();
  });

  it("focuses the first search match and lists its neighbours strongest first", () => {
    renderExplorer();
    fireEvent.change(screen.getByLabelText("Find a node"), { target: { value: "gross" } });
    fireEvent.submit(screen.getByRole("search"));
    expect(screen.getByRole("link", { name: "Open" })).toHaveAttribute("href", "/data-health?metric=sales.gross");
    const connected = screen.getByText(/Connected \(3\)/).parentElement!;
    const labels = within(connected)
      .getAllByRole("button")
      .map((b) => b.textContent);
    // Weights: permission 1, covers 0.9, daily sales 0.4.
    expect(labels).toEqual(["reconciliation.sales", "Coversstrong", "Daily salesweak"]);
  });

  it("shrinks to the local graph around the focused node", () => {
    renderExplorer({ initialFocusId: "source:CTB" });
    fireEvent.click(screen.getByLabelText("Local graph"));
    expect(screen.getByTestId("canvas")).toHaveTextContent("2 nodes");
    fireEvent.change(screen.getByLabelText("Local graph depth"), { target: { value: "2" } });
    expect(screen.getByTestId("canvas")).toHaveTextContent("3 nodes");
  });

  it("asks for a selection when local mode has no focus", () => {
    renderExplorer({ initialLocal: true });
    expect(screen.getByText("Select a node to see its local graph.")).toBeInTheDocument();
  });
});
