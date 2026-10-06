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

const COLORS = ["#16a34a", "#dc2626", "#2563eb", "#ca8a04"];

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
            <CartesianGrid strokeDasharray="3 3" vertical={false} className="stroke-muted" />
            <XAxis dataKey="x" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
            <YAxis tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={40} />
            <Tooltip />
            {widget.series.map((s, i) => (
              <Bar
                key={s.key}
                dataKey={s.key}
                name={s.label}
                stackId={widget.stacked ? "a" : undefined}
                fill={COLORS[i % COLORS.length]}
                radius={widget.stacked && i === widget.series.length - 1 ? [3, 3, 0, 0] : [3, 3, 0, 0]}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </WidgetShell>
  );
}
