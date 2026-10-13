"use client";

import { useMemo } from "react";
import { BarChart3, Boxes, GitMerge, ListChecks, Package, Tags, type LucideIcon } from "lucide-react";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import type { ColumnGraph, FlowTone } from "@/components/flow/types";
import type { AssignmentRow, CategoryNode, DendrogramNode, ProductPoint } from "@/lib/api";

/** The pipeline's stages, left to right. The id is what a click reports back. */
export type PipelineStage = "products" | "embed" | "cluster" | "curate" | "assign" | "report";

/** One stage before layout: what it is called, and what it can show right now. */
interface StageSpec {
  id: PipelineStage;
  title: string;
  icon: LucideIcon;
  subtitle: string;
  detail: string;
  tone: FlowTone;
}

interface PipelineCounts {
  products: number;
  assigned: number;
  needsReview: number;
  unassigned: number;
  embedded: number;
  merges: number;
  categories: number;
}

/** Counts the pipeline stages summarise, from the four datasets the page already reads. */
export function pipelineCounts(
  categories: CategoryNode[],
  assignments: AssignmentRow[],
  points: ProductPoint[],
  dendrogram: DendrogramNode[],
): PipelineCounts {
  return {
    products: assignments.length,
    assigned: assignments.filter((a) => a.status === "ASSIGNED").length,
    needsReview: assignments.filter((a) => a.status === "NEEDS_REVIEW").length,
    unassigned: assignments.filter((a) => a.status === "UNASSIGNED").length,
    embedded: points.length,
    merges: dendrogram.filter((n) => !n.isLeaf).length,
    categories: categories.length,
  };
}

/**
 * products → embed → cluster → curate → assign → report, each stage carrying the count
 * and tone it can report. A stage with nothing to show yet is `missing` (dashed, "no
 * data"), and the assignment stage escalates to `warn` while products wait for review:
 * the tone vocabulary is the design system's, not a bespoke one.
 */
export function buildPipelineGraph(counts: PipelineCounts): ColumnGraph {
  const stages: StageSpec[] = [
    {
      id: "products",
      title: "Products",
      icon: Package,
      subtitle: `${counts.products} products`,
      detail: "distinct names across POS + CTB",
      tone: counts.products === 0 ? "missing" : "neutral",
    },
    {
      id: "embed",
      title: "Embed",
      icon: Boxes,
      subtitle: counts.embedded ? `${counts.embedded} embedded` : "no embeddings yet",
      detail: "MiniLM sentence embeddings",
      tone: counts.embedded > 0 ? "ok" : "missing",
    },
    {
      id: "cluster",
      title: "Cluster",
      icon: GitMerge,
      subtitle: counts.merges ? `${counts.merges} merges` : "no dendrogram yet",
      detail: "agglomerative, average linkage",
      tone: counts.merges > 0 ? "ok" : "missing",
    },
    {
      id: "curate",
      title: "Curate",
      icon: Tags,
      subtitle: `${counts.categories} categories`,
      detail: "staff-authored taxonomy",
      tone: counts.categories > 0 ? "ok" : "missing",
    },
    {
      id: "assign",
      title: "Assign",
      icon: ListChecks,
      subtitle:
        counts.products > 0 ? `${counts.assigned} of ${counts.products} assigned` : "nothing to assign yet",
      detail:
        counts.needsReview || counts.unassigned
          ? `${counts.needsReview} to review · ${counts.unassigned} unassigned`
          : "every product has a category",
      tone: counts.needsReview > 0 ? "warn" : counts.assigned === 0 ? "missing" : "ok",
    },
    {
      id: "report",
      title: "Report",
      icon: BarChart3,
      subtitle: "Sales by category",
      detail: "dashboards + Ask Goldy's",
      tone: counts.categories > 0 && counts.assigned > 0 ? "ok" : "missing",
    },
  ];

  return {
    columns: stages.map((stage) => [
      {
        id: stage.id,
        data: {
          title: stage.title,
          icon: stage.icon,
          subtitle: stage.subtitle,
          detail: stage.detail,
          tone: stage.tone,
          drill: stage.id,
        },
      },
    ]),
    // An edge takes the tone of the stage it points at, so the stage with nothing to show
    // yet is the one with a dashed arrow leading into it.
    edges: stages.slice(1).map((target, i) => ({
      source: stages[i].id,
      target: target.id,
      tone: target.tone,
    })),
  };
}

interface PipelineFlowProps {
  categories: CategoryNode[];
  assignments: AssignmentRow[];
  points: ProductPoint[];
  dendrogram: DendrogramNode[];
  /** Called with the stage that was clicked, so the page can drill into it. */
  onDrill?: (stage: PipelineStage) => void;
  height?: number;
}

/**
 * The categorisation pipeline as a read-only flow: where the products come from, what the
 * latest run did with them, and what is left for a curator to decide.
 */
export function PipelineFlow({
  categories,
  assignments,
  points,
  dendrogram,
  onDrill,
  height = 320,
}: PipelineFlowProps) {
  const graph = useMemo(
    () => buildPipelineGraph(pipelineCounts(categories, assignments, points, dendrogram)),
    [categories, assignments, points, dendrogram],
  );

  return (
    <FlowCanvas
      graph={graph}
      ariaLabel="Product categorisation pipeline: products through embed, cluster, curate, assign and report"
      // `drill` ids are the stage ids, so the id the canvas reports back is the stage.
      onDrill={(id) => onDrill?.(id as PipelineStage)}
      height={height}
      // Six stages at the standard gap overflow a wide canvas; tighter columns stay legible.
      columnGap={72}
    />
  );
}
