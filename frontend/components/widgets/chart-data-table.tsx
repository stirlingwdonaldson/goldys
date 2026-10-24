import { flattenSeries } from "./flatten";
import { formatValue } from "./format";
import { TableWidgetView } from "./table-widget";
import type { BarChartWidget, TimeSeriesWidget } from "./types";

export function ChartDataTable({ widget }: { widget: BarChartWidget | TimeSeriesWidget }) {
  return (
    <details className="mt-3">
      <summary className="cursor-pointer text-sm underline">View chart data</summary>
      <p className="my-2 text-xs text-muted-foreground">Unavailable values are not zero.</p>
      <TableWidgetView widget={{
        schemaVersion: 2, id: `${widget.id}-data`, type: "table", title: `${widget.title} — data`,
        columns: [{ key: "x", label: "Date / category" }, ...widget.series.map((series) => ({ key: series.key, label: series.label }))],
        rows: flattenSeries(widget.series).map((row) => ({
          x: row.x,
          ...Object.fromEntries(widget.series.map((series) => [series.key,
            row[series.key] == null ? "Unavailable" : formatValue(Number(row[series.key]), widget.yFormat),
          ])),
        })),
      }} />
    </details>
  );
}
