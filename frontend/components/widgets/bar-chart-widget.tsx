"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { flattenSeries } from "./flatten";
import type { BarChartWidget } from "./types";
import { WidgetShell } from "./widget-shell";
import { formatValue } from "./format";
import { CHART_TOOLTIP_PROPS, compactAxis, formatXTick, AXIS_TICK, STATUS_SERIES_COLORS } from "./chart-style";

export function BarChartWidgetView({ widget }: { widget: BarChartWidget }) {
  const data = flattenSeries(widget.series);
  if (widget.series.length === 0 || data.length === 0) {
    return (
      <WidgetShell title={widget.title} description={widget.description}>
        <p className="text-sm text-muted-foreground">No data yet.</p>
      </WidgetShell>
    );
  }
  // Whole-number data (counts) gets whole-number ticks: a "0.75 orders" gridline is noise.
  const wholeNumbers = data.every((row) =>
    widget.series.every((s) => row[s.key] == null || Number.isInteger(row[s.key])),
  );
  return (
    <WidgetShell title={widget.title} description={widget.description}>
      <div role="img" aria-label={widget.title} className="h-48 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
            <XAxis dataKey="x" tick={AXIS_TICK} tickLine={false} axisLine={false} minTickGap={16} tickFormatter={formatXTick} />
            <YAxis
              tick={AXIS_TICK}
              tickLine={false}
              axisLine={false}
              width={40}
              allowDecimals={!wholeNumbers}
              tickFormatter={(v: number) => compactAxis(v, widget.yFormat)}
            />
            <Tooltip
              {...CHART_TOOLTIP_PROPS}
              labelFormatter={formatXTick}
              formatter={(v: number) => formatValue(v, widget.yFormat)}
              cursor={{ fill: "hsl(var(--muted))" }}
            />
            {widget.series.map((s, i) => (
              <Bar
                key={s.key}
                dataKey={s.key}
                name={s.label}
                stackId={widget.stacked ? "a" : undefined}
                fill={STATUS_SERIES_COLORS[i % STATUS_SERIES_COLORS.length]}
                maxBarSize={22}
                radius={!widget.stacked || i === widget.series.length - 1 ? [4, 4, 0, 0] : [0, 0, 0, 0]}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </WidgetShell>
  );
}
