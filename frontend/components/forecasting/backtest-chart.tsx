"use client";

import {
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
import type { BacktestRow } from "@/lib/api/types";

/**
 * Backtest: fitted model vs actual over the history, with the seasonal-naive baseline behind
 * both. A `null` value gaps that line (Recharts leaves `connectNulls` off), so a missing
 * baseline reads as a gap rather than a fabricated zero.
 */
export function BacktestChart({ rows }: { rows: BacktestRow[] }) {
  if (rows.length === 0) {
    return (
      <EmptyState
        title="No backtest data"
        description="Backtest accuracy appears once the run succeeds."
      />
    );
  }
  const data = rows.map((r) => ({
    date: r.date ?? "",
    actualNet: r.actualNet,
    predictedNet: r.predictedNet,
    baselineNet: r.baselineNet,
  }));
  return (
    <div
      role="img"
      aria-label="Backtest: actual versus model versus baseline"
      data-testid="backtest-chart"
      className="h-80 w-full"
    >
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
          <XAxis
            dataKey="date"
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            minTickGap={28}
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
          <Line
            type="monotone"
            dataKey="actualNet"
            name="Actual"
            stroke="hsl(var(--chart-3))"
            strokeWidth={1.6}
            dot={false}
          />
          <Line
            type="monotone"
            dataKey="predictedNet"
            name="Model"
            stroke="hsl(var(--chart-1))"
            strokeWidth={1.6}
            dot={false}
          />
          <Line
            type="monotone"
            dataKey="baselineNet"
            name="Baseline"
            stroke="hsl(var(--chart-5))"
            strokeWidth={1.2}
            strokeDasharray="3 3"
            dot={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
