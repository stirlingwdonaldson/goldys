/**
 * WidgetSpec v2 — a versioned, discriminated union. This is the only contract the frontend
 * renders: both built-in dashboards and AI answers resolve to one of these variants. It is data,
 * never code: the AI cannot choose component names, CSS, handlers, or markup.
 */

export type WidgetFormat = "currency" | "number" | "percent" | "text";

export type WidgetType = "stat" | "time-series" | "bar-chart" | "table" | "ranked-list";

export interface SeriesPoint {
  x: string;
  y: number | null;
}

export interface Series {
  key: string;
  label: string;
  points: SeriesPoint[];
}

export interface Column {
  key: string;
  label: string;
  format?: string | null;
}

export interface RankedItem {
  label: string;
  primary?: string | null;
  secondary?: string | null;
  badge?: string | null;
}

/** The bounded semantic query behind a widget, enabling save + re-run. */
export interface WidgetQuery {
  tool: string;
  input: Record<string, unknown>;
}

interface WidgetBase {
  schemaVersion: 2;
  id: string;
  type: WidgetType;
  title: string;
  description?: string | null;
  query?: WidgetQuery | null;
}

export interface StatWidget extends WidgetBase {
  type: "stat";
  value: number | null;
  format?: string | null;
  hint?: string | null;
}

export interface TimeSeriesWidget extends WidgetBase {
  type: "time-series";
  series: Series[];
  yFormat?: string | null;
}

export interface BarChartWidget extends WidgetBase {
  type: "bar-chart";
  series: Series[];
  yFormat?: string | null;
  stacked?: boolean;
}

export interface TableWidget extends WidgetBase {
  type: "table";
  columns: Column[];
  rows: Record<string, unknown>[];
}

export interface RankedListWidget extends WidgetBase {
  type: "ranked-list";
  items: RankedItem[];
}

export type WidgetSpec =
  | StatWidget
  | TimeSeriesWidget
  | BarChartWidget
  | TableWidget
  | RankedListWidget;

export const WIDGET_TYPES: readonly WidgetType[] = [
  "stat",
  "time-series",
  "bar-chart",
  "table",
  "ranked-list",
];
