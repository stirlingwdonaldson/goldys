/** WidgetSpec v2 is data, never executable component names, handlers or markup. */
import type { WidgetSpec } from "./parse";
export type { WidgetSpec } from "./parse";

export type WidgetFormat = "currency" | "number" | "percent" | "text";
export type WidgetType = WidgetSpec["type"];
export type StatWidget = Extract<WidgetSpec, { type: "stat" }>;
export type TimeSeriesWidget = Extract<WidgetSpec, { type: "time-series" }>;
export type BarChartWidget = Extract<WidgetSpec, { type: "bar-chart" }>;
export type TableWidget = Extract<WidgetSpec, { type: "table" }>;
export type RankedListWidget = Extract<WidgetSpec, { type: "ranked-list" }>;
export type Series = TimeSeriesWidget["series"][number];
export type SeriesPoint = Series["points"][number];
export type Column = TableWidget["columns"][number];
export type RankedItem = RankedListWidget["items"][number];
export type WidgetQuery = NonNullable<WidgetSpec["query"]>;
