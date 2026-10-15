"use client";

import {
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  Tooltip,
  XAxis,
  YAxis,
  ZAxis,
} from "recharts";
import { EmptyState } from "@/components/states/empty-state";
import {
  AXIS_TICK,
  CHART_TOOLTIP_PROPS,
  compactAxis,
} from "@/components/widgets/chart-style";
import { formatNumber } from "@/lib/format";
import type { BacktestRow } from "@/lib/api/types";

/** A backtest point with both coordinates present (null coordinates are dropped). */
type PlottablePoint = BacktestRow & { actualNet: number; predictedNet: number };

/**
 * Predicted vs actual scatter with the y = x line: points above the line are over-predictions.
 * Rows with a `null` actual or predicted coordinate are dropped (they cannot be placed).
 */
export function PredictedVsActualChart({ rows }: { rows: BacktestRow[] }) {
  const points: PlottablePoint[] = rows.filter(
    (r): r is PlottablePoint => r.actualNet != null && r.predictedNet != null,
  );
  if (points.length === 0) {
    return (
      <EmptyState
        title="No comparison points"
        description="Predicted vs actual appears once actuals resolve."
      />
    );
  }
  const values = points.flatMap((r) => [r.actualNet, r.predictedNet]);
  const max = Math.max(...values, 0);
  return (
    <div
      role="img"
      aria-label="Predicted versus actual"
      data-testid="predicted-vs-actual-chart"
      className="h-72 w-full"
    >
      <ResponsiveContainer width="100%" height="100%">
        <ScatterChart margin={{ top: 8, right: 12, left: -8, bottom: 8 }}>
          <CartesianGrid stroke="hsl(var(--border))" />
          <XAxis
            type="number"
            dataKey="actualNet"
            name="Actual"
            domain={[0, max]}
            tick={AXIS_TICK}
            tickFormatter={(v: number) => compactAxis(v, "currency")}
            axisLine={{ stroke: "hsl(var(--border))" }}
            tickLine={false}
          />
          <YAxis
            type="number"
            dataKey="predictedNet"
            name="Predicted"
            domain={[0, max]}
            tick={AXIS_TICK}
            tickFormatter={(v: number) => compactAxis(v, "currency")}
            axisLine={false}
            tickLine={false}
            width={52}
          />
          <ZAxis range={[28, 28]} />
          <Tooltip {...CHART_TOOLTIP_PROPS} formatter={(v: number) => formatNumber(v)} />
          <ReferenceLine
            segment={[
              { x: 0, y: 0 },
              { x: max, y: max },
            ]}
            stroke="hsl(var(--border))"
            strokeDasharray="4 4"
          />
          <Scatter data={points} fill="hsl(var(--chart-1))" fillOpacity={0.55} />
        </ScatterChart>
      </ResponsiveContainer>
    </div>
  );
}
