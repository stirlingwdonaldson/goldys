import { formatShortDay } from "@/lib/format";
import type { CSSProperties } from "react";

/**
 * Bar series where the second series is the "bad" counterpart of the first
 * (clean vs failed runs): green, then red, then source hues.
 */
export const STATUS_SERIES_COLORS = [
  "hsl(var(--chart-1))",
  "hsl(var(--chart-2))",
  "hsl(var(--chart-3))",
  "hsl(var(--chart-4))",
];

/** Line/area series: green (good) first, then source hues; red is kept for "bad" series. */
export const SERIES_COLORS = [
  "hsl(var(--chart-1))",
  "hsl(var(--chart-3))",
  "hsl(var(--chart-4))",
  "hsl(var(--chart-5))",
];

const tooltipContent: CSSProperties = {
  borderRadius: 10,
  border: "1px solid hsl(var(--border))",
  boxShadow: "0 4px 16px rgba(23,23,26,0.08)",
  fontSize: 12,
  padding: "8px 10px",
};

export const CHART_TOOLTIP_PROPS = {
  contentStyle: tooltipContent,
  labelStyle: { color: "hsl(var(--muted-foreground))", marginBottom: 2 },
  cursor: { stroke: "hsl(var(--border))" },
};

/** Short axis labels ("$12k") so the plot keeps its width. */
export function compactAxis(value: number, format?: string | null): string {
  const abs = Math.abs(value);
  const short =
    abs >= 1_000_000 ? `${+(value / 1_000_000).toFixed(1)}m` : abs >= 1_000 ? `${+(value / 1_000).toFixed(1)}k` : `${value}`;
  if (format === "currency") return `$${short}`;
  if (format === "percent") return `${short}%`;
  return short;
}

/** ISO dates on the x axis read as "4 Oct"; anything else passes through. */
export const formatXTick = formatShortDay;

/** Axis tick text, shared by every chart. */
export const AXIS_TICK = { fontSize: 11, fill: "hsl(var(--muted-foreground))" };
