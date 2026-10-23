"use client";

import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { EmptyState } from "@/components/states/empty-state";
import {
  AXIS_TICK,
  CHART_TOOLTIP_PROPS,
  compactAxis,
  formatXTick,
} from "@/components/widgets/chart-style";
import { formatNumber } from "@/lib/format";
import type { ForecastDayRow } from "@/lib/api/types";

/**
 * Forecast forward: the model's median net revenue as bars with the seasonal-naive baseline
 * behind it as a dashed line. A `null` median skips the bar; a `null` baseline gaps the line.
 */
export function ForecastVsBaselineChart({ rows }: { rows: ForecastDayRow[] }) {
  if (rows.length === 0) {
    return (
      <EmptyState
        title="No forecast data"
        description="The forecast series appears once the run succeeds."
      />
    );
  }
  return (
    <div
      role="img"
      aria-label="Forecast versus baseline"
      data-testid="forecast-vs-baseline-chart"
      className="h-64 w-full"
    >
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={rows} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
          <XAxis
            dataKey="date"
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            minTickGap={16}
            tickFormatter={formatXTick}
          />
          <YAxis
            tick={AXIS_TICK}
            tickFormatter={(v: number) => compactAxis(v, "currency")}
            tickLine={false}
            axisLine={false}
            width={52}
          />
          <Tooltip
            {...CHART_TOOLTIP_PROPS}
            labelFormatter={formatXTick}
            formatter={(v: number) => formatNumber(v)}
          />
          <Legend />
          <Bar
            dataKey="median"
            name="Forecast net"
            fill="hsl(var(--chart-1))"
            radius={[4, 4, 0, 0]}
            maxBarSize={38}
          />
          <Line
            type="monotone"
            dataKey="baseline"
            name="Seasonal-naive baseline"
            stroke="hsl(var(--chart-5))"
            strokeWidth={2}
            strokeDasharray="4 3"
            dot={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
