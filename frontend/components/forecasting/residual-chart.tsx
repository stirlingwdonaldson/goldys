"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ReferenceLine,
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

/** Positive residual (model over-predicts) vs negative (under-predicts). */
const POSITIVE = "hsl(var(--chart-5))";
const NEGATIVE = "hsl(var(--chart-2))";

/**
 * Residuals over time — the honest view of where the model drifts. Each bar's colour is its sign
 * (blue for over-prediction, red for under-prediction); a `null` error skips the bar.
 */
export function ResidualChart({ rows }: { rows: BacktestRow[] }) {
  if (rows.length === 0) {
    return (
      <EmptyState
        title="No residual data"
        description="Residuals appear once the run succeeds."
      />
    );
  }
  const data = rows.map((r) => ({ date: r.date ?? "", error: r.error }));
  return (
    <div
      role="img"
      aria-label="Residuals over time"
      data-testid="residual-chart"
      className="h-52 w-full"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
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
          <ReferenceLine y={0} stroke="hsl(var(--border))" />
          <Bar dataKey="error" name="Predicted − actual">
            {data.map((row, index) => (
              <Cell key={index} fill={row.error == null || row.error >= 0 ? POSITIVE : NEGATIVE} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
