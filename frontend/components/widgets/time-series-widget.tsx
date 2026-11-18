"use client";

import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ChartDataTable } from "./chart-data-table";
import { flattenSeries } from "./flatten";
import { formatValue } from "./format";
import { CHART_TOOLTIP_PROPS, SERIES_COLORS, compactAxis, formatXTick, AXIS_TICK } from "./chart-style";
import type { TimeSeriesWidget } from "./types";
import { WidgetShell } from "./widget-shell";

export function TimeSeriesWidgetView({ widget }: { widget: TimeSeriesWidget }) {
  const data = flattenSeries(widget.series);
  if (widget.series.length === 0 || data.length === 0) {
    return (
      <WidgetShell title={widget.title} description={widget.description}>
        <p className="text-sm text-muted-foreground">No data yet.</p>
      </WidgetShell>
    );
  }
  return (
    <WidgetShell title={widget.title} description={widget.description}>
      <div role="img" aria-label={widget.title} className="h-48 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 4, right: 8, left: -8, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
            <XAxis dataKey="x" tick={AXIS_TICK} tickLine={false} axisLine={false} minTickGap={16} tickFormatter={formatXTick} />
            <YAxis
              tick={AXIS_TICK}
              tickLine={false}
              axisLine={false}
              width={52}
              tickFormatter={(v: number) => compactAxis(v, widget.yFormat)}
            />
            <Tooltip
              {...CHART_TOOLTIP_PROPS}
              labelFormatter={formatXTick}
              formatter={(v: number) => formatValue(v, widget.yFormat)}
            />
            {widget.series.map((s, i) => {
              const color = SERIES_COLORS[i % SERIES_COLORS.length];
              return (
                <Area
                  key={s.key}
                  // Straight segments: daily figures are discrete, and a smoothed curve
                  // implies values between days (and can overshoot) that never happened.
                  type="linear"
                  dataKey={s.key}
                  name={s.label}
                  connectNulls={false}
                  stroke={color}
                  fill={color}
                  fillOpacity={widget.series.length === 1 ? 0.07 : 0}
                  strokeWidth={2}
                  dot={{ r: 3, strokeWidth: 0, fill: color, fillOpacity: 1 }}
                  activeDot={{ r: 4, strokeWidth: 0 }}
                />
              );
            })}
          </AreaChart>
        </ResponsiveContainer>
      </div>
      {widget.series.length > 1 ? <ul aria-label="Chart legend" className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
        {widget.series.map((series, index) => <li key={series.key} className="inline-flex items-center gap-2">
          <span aria-hidden="true" className="h-0.5 w-4" style={{ backgroundColor: SERIES_COLORS[index % SERIES_COLORS.length] }} />
          {series.label}
        </li>)}
      </ul> : null}
      <ChartDataTable widget={widget} />
    </WidgetShell>
  );
}
