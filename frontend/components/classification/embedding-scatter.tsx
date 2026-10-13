"use client";

import { useMemo } from "react";
import { CartesianGrid, ResponsiveContainer, Scatter, ScatterChart, Tooltip, XAxis, YAxis } from "recharts";
import { AXIS_TICK, CHART_TOOLTIP_PROPS } from "@/components/widgets/chart-style";
import { EmptyState } from "@/components/states/empty-state";
import { formatPercent } from "@/lib/format";
import { categoryColour } from "./category-colours";
import type { CategoryNode, ProductPoint } from "@/lib/api";

interface EmbeddingScatterProps {
  points: ProductPoint[];
  categories: CategoryNode[];
  /** Called with the point the user selected, so the page can move that product. */
  onSelect?: (point: ProductPoint) => void;
  height?: number;
}

interface ScatterSeries {
  key: string;
  name: string;
  colour: string;
  data: ProductPoint[];
}

/**
 * One series per category, so each keeps its own colour and legend entry. Products with
 * no category yet form a series of their own at the end, in the "no data" grey.
 */
function toSeries(points: ProductPoint[], categories: CategoryNode[]): ScatterSeries[] {
  const names = new Map(categories.map((category) => [category.id, category.name]));
  const groups = new Map<string | null, ProductPoint[]>();
  for (const point of points) {
    const bucket = groups.get(point.categoryId) ?? [];
    bucket.push(point);
    groups.set(point.categoryId, bucket);
  }
  return [...groups.entries()]
    .map(([categoryId, data]) => ({
      key: categoryId ?? "unassigned",
      name: categoryId ? names.get(categoryId) ?? categoryId : "Unassigned",
      colour: categoryColour(categoryId),
      data,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

interface DotProps {
  cx?: number;
  cy?: number;
  fill?: string;
  payload?: ProductPoint;
  onSelect?: (point: ProductPoint) => void;
}

/**
 * A scatter point. The `<title>` answers "what is this dot?" for every user agent;
 * Recharts' tooltip renders the same answer on top, with the category name.
 */
function ProductDot({ cx, cy, fill, payload, onSelect }: DotProps) {
  if (cx == null || cy == null) return null;
  return (
    <circle
      cx={cx}
      cy={cy}
      r={5}
      fill={fill}
      // Unassigned products are the ones a curator has to look at, so they read quieter.
      fillOpacity={payload?.categoryId == null ? 0.5 : 0.85}
      stroke="hsl(var(--background))"
      strokeWidth={1}
      className={onSelect ? "cursor-pointer" : undefined}
      onClick={payload && onSelect ? () => onSelect(payload) : undefined}
    >
      <title>
        {payload?.productNameKey ?? ""}
        {payload?.confidence == null ? " · no category yet" : ` · ${formatPercent(payload.confidence)} confidence`}
      </title>
    </circle>
  );
}

interface TooltipPayload {
  payload?: ProductPoint;
}

function ScatterTooltip({
  active,
  payload,
  names,
}: {
  active?: boolean;
  payload?: TooltipPayload[];
  names: Map<string, string>;
}) {
  const point = active ? payload?.[0]?.payload : undefined;
  if (!point) return null;
  return (
    <div className="rounded-lg border bg-popover px-2.5 py-1.5 text-xs text-popover-foreground shadow-md">
      <p className="font-medium">{point.productNameKey}</p>
      <p className="text-muted-foreground">
        {point.categoryId ? names.get(point.categoryId) ?? point.categoryId : "No category yet"}
        {" · "}
        {point.confidence == null ? "no confidence" : `${formatPercent(point.confidence)} confidence`}
      </p>
    </div>
  );
}

/**
 * The 2-D projection of the product embeddings, coloured by category: the picture behind
 * the latest clustering run. Selecting a point reports that product, so the page can
 * offer to move it.
 */
export function EmbeddingScatter({ points, categories, onSelect, height = 320 }: EmbeddingScatterProps) {
  const names = useMemo(() => new Map(categories.map((c) => [c.id, c.name])), [categories]);
  const series = useMemo(() => toSeries(points, categories), [points, categories]);

  if (points.length === 0) {
    return (
      <EmptyState
        title="No embedding space yet"
        description="Run a classification to project the products into two dimensions."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <div
        role="img"
        aria-label={`Embedding space: ${points.length} products, coloured by category`}
        className="w-full"
        style={{ height }}
      >
        <ResponsiveContainer width="100%" height="100%">
          <ScatterChart margin={{ top: 8, right: 16, bottom: 4, left: -8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
            <XAxis
              type="number"
              dataKey="x"
              name="Embedding x"
              tick={AXIS_TICK}
              tickLine={false}
              axisLine={false}
              tickFormatter={(value: number) => Number(value).toFixed(1)}
            />
            <YAxis
              type="number"
              dataKey="y"
              name="Embedding y"
              tick={AXIS_TICK}
              tickLine={false}
              axisLine={false}
              width={48}
              tickFormatter={(value: number) => Number(value).toFixed(1)}
            />
            <Tooltip
              {...CHART_TOOLTIP_PROPS}
              content={<ScatterTooltip names={names} />}
              cursor={{ stroke: "hsl(var(--border))" }}
            />
            {series.map((s) => (
              <Scatter
                key={s.key}
                name={s.name}
                data={s.data}
                fill={s.colour}
                shape={<ProductDot onSelect={onSelect} />}
                isAnimationActive={false}
              />
            ))}
          </ScatterChart>
        </ResponsiveContainer>
      </div>

      {/* The colours carry no meaning on their own, so every series is named and counted. */}
      <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
        {series.map((s) => (
          <li key={s.key} className="flex items-center gap-1.5">
            <span
              aria-hidden="true"
              className="inline-block h-2.5 w-2.5 rounded-full"
              style={{ backgroundColor: s.colour }}
            />
            {s.name}
            <span className="tabular-nums">({s.data.length})</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
