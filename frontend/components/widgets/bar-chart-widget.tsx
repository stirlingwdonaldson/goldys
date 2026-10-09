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
import { CHART_TOOLTIP_PROPS, formatXTick } from "./chart-style";

// Green first (good), red second (bad), e.g. clean vs failed runs.
const COLORS = ["hsl(var(--chart-1))", "hsl(var(--chart-2))", "hsl(var(--chart-3))", "hsl(var(--chart-4))"];

export function BarChartWidgetView({ widget }: { widget: BarChartWidget }) {
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
          <BarChart data={data} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
            <XAxis dataKey="x" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} minTickGap={16} tickFormatter={formatXTick} />
            <YAxis tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} width={40} />
            <Tooltip {...CHART_TOOLTIP_PROPS} labelFormatter={formatXTick} cursor={{ fill: "hsl(var(--muted))" }} />
            {widget.series.map((s, i) => (
              <Bar
                key={s.key}
                dataKey={s.key}
                name={s.label}
                stackId={widget.stacked ? "a" : undefined}
                fill={COLORS[i % COLORS.length]}
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
