import { describe, it, expect } from "vitest";
import { Box } from "lucide-react";
import { layoutColumns } from "./layout";
import type { ColumnGraph, StepNodeData } from "./types";

const node = (id: string): { id: string; data: StepNodeData } => ({
  id,
  data: { title: id, icon: Box, tone: "neutral" },
});

describe("layoutColumns", () => {
  it("emits a heading node above each titled column and shifts the nodes down", () => {
    const g: ColumnGraph = {
      columns: [["a", "b"].map(node), [node("c")]],
      edges: [{ source: "a", target: "c" }],
      columnTitles: ["First", "Second"],
    };
    const laid = layoutColumns(g);
    const headings = laid.nodes.filter((n) => n.type === "columnHeading");
    expect(headings).toHaveLength(2);
    expect(headings.map((h) => h.data.title)).toEqual(["First", "Second"]);
    const first = laid.nodes.find((n) => n.id === "a")!;
    expect(headings[0].position.x).toBe(first.position.x);
    expect(headings[0].position.y).toBeLessThan(first.position.y);
  });

  it("leaves heading nodes non-interactive", () => {
    const g: ColumnGraph = {
      columns: [[node("a")]],
      edges: [],
      columnTitles: ["Only"],
    };
    const heading = layoutColumns(g).nodes.find((n) => n.type === "columnHeading")!;
    expect(heading.draggable).toBe(false);
    expect(heading.selectable).toBe(false);
  });

  it("scales edge stroke width with the transaction value", () => {
    const g: ColumnGraph = {
      columns: [[node("a")], [node("b"), node("c")]],
      edges: [
        { source: "a", target: "b", value: 100 },
        { source: "a", target: "c", value: 1000 },
      ],
    };
    const laid = layoutColumns(g);
    const width = (target: string) =>
      laid.edges.find((e) => e.target === target)?.style?.strokeWidth as number | undefined;
    expect(width("b")!).toBeLessThan(width("c")!);
  });

  it("keeps value-less edges thin at the neutral default", () => {
    const g: ColumnGraph = {
      columns: [[node("a")], [node("b")]],
      edges: [{ source: "a", target: "b" }],
    };
    expect(layoutColumns(g).edges[0].style?.strokeWidth).toBe(1.5);
  });

  it("skips headings entirely when no column has a title", () => {
    const g: ColumnGraph = {
      columns: [[node("a")]],
      edges: [],
    };
    const laid = layoutColumns(g);
    expect(laid.nodes.some((n) => n.type === "columnHeading")).toBe(false);
    expect(laid.nodes[0].position.y).toBe(0);
  });
});
