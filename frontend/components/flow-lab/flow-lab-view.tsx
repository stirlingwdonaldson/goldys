"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, ArrowRight, ChevronLeft, ChevronRight, ExternalLink, MousePointerClick } from "lucide-react";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { bestDirection } from "@/components/flow/layout";
import type { FlowTone } from "@/components/flow/types";

const CANVAS_HEIGHT = 560;
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { IconTile } from "@/components/ui/icon-tile";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  DIAGRAMS,
  describeNode,
  toColumnGraph,
  type ConceptDiagram,
  type DiagramMode,
} from "./diagrams";

const MODE_TONE: Record<DiagramMode, "info" | "brand" | "neutral"> = {
  Explore: "info",
  Configure: "brand",
  Inspect: "neutral",
};

/** How each node tone reads in the inspector. Neutral nodes get no status pill. */
const TONE_BADGE: Record<FlowTone, { label: string; variant: "success" | "conflict" | "failed" | "missing" | "info" } | null> = {
  ok: { label: "Healthy", variant: "success" },
  warn: { label: "Needs attention", variant: "conflict" },
  fail: { label: "Failing or blocking", variant: "failed" },
  missing: { label: "No data yet", variant: "missing" },
  info: { label: "Derived or resolved", variant: "info" },
  neutral: null,
};

interface FlowLabViewProps {
  diagram: ConceptDiagram;
  onSelect: (id: string) => void;
}

/** Picker and prev/next on top; the canvas gets the full width so wide graphs stay legible. */
export function FlowLabView({ diagram, onSelect }: FlowLabViewProps) {
  const index = DIAGRAMS.findIndex((d) => d.id === diagram.id);
  const prev = DIAGRAMS[(index - 1 + DIAGRAMS.length) % DIAGRAMS.length];
  const next = DIAGRAMS[(index + 1) % DIAGRAMS.length];
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <Select value={diagram.id} onValueChange={onSelect}>
          <SelectTrigger aria-label="Choose a diagram" className="w-full sm:w-80">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {DIAGRAMS.map((d, i) => (
              <SelectItem key={d.id} value={d.id}>
                {String(i + 1).padStart(2, "0")} · {d.title}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button variant="outline" size="sm" onClick={() => onSelect(prev.id)} aria-label={`Previous: ${prev.title}`}>
          <ChevronLeft aria-hidden="true" />
          Previous
        </Button>
        <Button variant="outline" size="sm" onClick={() => onSelect(next.id)} aria-label={`Next: ${next.title}`}>
          Next
          <ChevronRight aria-hidden="true" />
        </Button>
      </div>

      {/* Keyed so the canvas and inspector reset cleanly when the diagram changes. */}
      <DiagramPanel key={diagram.id} diagram={diagram} index={index} />
    </div>
  );
}

function DiagramPanel({ diagram, index }: { diagram: ConceptDiagram; index: number }) {
  const graph = useMemo(() => toColumnGraph(diagram), [diagram]);
  // Orientation is chosen per diagram for the typical desktop canvas size.
  const direction = useMemo(() => bestDirection(graph, { width: 1100, height: CANVAS_HEIGHT }), [graph]);
  const [selected, setSelected] = useState<string | null>(null);

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={MODE_TONE[diagram.mode]}>{diagram.mode}</Badge>
          {diagram.audience.map((a) => (
            <Badge key={a} variant="outline">
              {a}
            </Badge>
          ))}
        </div>
        <h2 className="text-lg font-semibold tracking-tight">
          {String(index + 1).padStart(2, "0")} · {diagram.title}
        </h2>
        <p className="text-sm font-medium">&ldquo;{diagram.question}&rdquo;</p>
        <p className="max-w-3xl text-sm text-muted-foreground">{diagram.summary}</p>
      </div>

      <FlowCanvas
        graph={graph}
        ariaLabel={`${diagram.title} diagram`}
        height={CANVAS_HEIGHT}
        columnGap={72}
        direction={direction}
        onDrill={setSelected}
      />

      <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
        <NodeInspector diagram={diagram} selected={selected} onSelect={setSelected} />
        {diagram.live ? (
          <Button asChild variant="outline" size="sm">
            <Link href={diagram.live.href}>
              <ExternalLink aria-hidden="true" />
              Working version: {diagram.live.label}
            </Link>
          </Button>
        ) : null}
      </div>
    </div>
  );
}

function NodeInspector({
  diagram,
  selected,
  onSelect,
}: {
  diagram: ConceptDiagram;
  selected: string | null;
  onSelect: (id: string) => void;
}) {
  const info = selected ? describeNode(diagram, selected) : null;
  if (!info) {
    return (
      <aside className="flex items-center gap-2 rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
        <MousePointerClick className="size-5" aria-hidden="true" />
        Click a node to see what it is and what it connects to.
      </aside>
    );
  }
  const { node, incoming, outgoing } = info;
  const status = TONE_BADGE[node.tone];
  return (
    <aside aria-label="Node details" className="grid gap-4 rounded-lg border bg-card p-4 sm:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1fr)]">
      <div className="flex flex-col gap-3">
      <div className="flex items-start gap-3">
        <IconTile size="md">
          <node.icon />
        </IconTile>
        <div className="min-w-0">
          <p className="text-sm font-semibold">{node.title}</p>
          {node.subtitle ? <p className="text-xs text-muted-foreground">{node.subtitle}</p> : null}
        </div>
      </div>
      {status ? (
        <Badge variant={status.variant} className="self-start">
          {status.label}
        </Badge>
      ) : null}
      {node.note ? <p className="text-sm">{node.note}</p> : null}
      </div>
      <Connections title="Comes from" icon="in" items={incoming} onSelect={onSelect} />
      <Connections title="Feeds into" icon="out" items={outgoing} onSelect={onSelect} />
    </aside>
  );
}

function Connections({
  title,
  icon,
  items,
  onSelect,
}: {
  title: string;
  icon: "in" | "out";
  items: { node: { id: string; title: string } | undefined; label?: string }[];
  onSelect: (id: string) => void;
}) {
  const Arrow = icon === "in" ? ArrowLeft : ArrowRight;
  return (
    <div className="flex flex-col gap-1">
      <p className="text-xs font-medium text-muted-foreground">{title}</p>
      {items.length === 0 ? (
        <p className="px-1.5 text-sm text-muted-foreground">
          {icon === "in" ? "Nothing upstream; this is where it starts." : "Nothing downstream; this is where it ends."}
        </p>
      ) : null}
      <ul className="flex flex-col">
        {items.map(({ node, label }) =>
          node ? (
            <li key={node.id}>
              <button
                type="button"
                onClick={() => onSelect(node.id)}
                className="flex w-full items-center gap-2 rounded-md px-1.5 py-1 text-left text-sm hover:bg-muted"
              >
                <Arrow className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className="truncate">{node.title}</span>
                {label ? <span className="ml-auto shrink-0 text-xs text-muted-foreground">{label}</span> : null}
              </button>
            </li>
          ) : null,
        )}
      </ul>
    </div>
  );
}
