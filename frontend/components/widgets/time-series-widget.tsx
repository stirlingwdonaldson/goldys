"use client";

import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { flattenSeries } from "./flatten";
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
          <LineChart data={data} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
            <XAxis dataKey="x" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
            <YAxis tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={48} />
            <Tooltip />
            {widget.series.map((s, i) => (
              <Line
                key={s.key}
                type="monotone"
                dataKey={s.key}
                name={s.label}
                connectNulls={false}
                stroke={i === 0 ? "#2563eb" : "#16a34a"}
                strokeWidth={2}
                dot={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </WidgetShell>
  );
}
