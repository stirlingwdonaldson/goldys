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
import { EmptyState } from "@/components/states/empty-state";
import { AXIS_TICK, CHART_TOOLTIP_PROPS } from "@/components/widgets/chart-style";
import type { ForecastImportanceRow } from "@/lib/api/types";

/** A gain-based importance row with both members present (null rows are dropped). */
type PlottableImportance = ForecastImportanceRow & { feature: string; importance: number };

/**
 * Horizontal feature-importance bars, ordered as the model reports them. Rows whose feature or
 * importance is `null` are dropped rather than drawn as a zero-width bar.
 */
export function FeatureImportanceChart({ rows }: { rows: ForecastImportanceRow[] }) {
  const data: PlottableImportance[] = rows.filter(
    (r): r is PlottableImportance => r.feature != null && r.importance != null,
  );
  if (data.length === 0) {
    return (
      <EmptyState
        title="No feature importance"
        description="Importance is unavailable for this model."
      />
    );
  }
  // Grow with the feature count so long models don't squash the bars.
  const height = Math.max(220, data.length * 22);
  return (
    <div
      role="img"
      aria-label="Feature importance"
      data-testid="feature-importance-chart"
      className="w-full"
      style={{ height }}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 4 }}>
          <CartesianGrid horizontal={false} stroke="hsl(var(--border))" />
          <XAxis type="number" tick={AXIS_TICK} axisLine={false} tickLine={false} />
          <YAxis
            type="category"
            dataKey="feature"
            tick={{ ...AXIS_TICK, fontSize: 10 }}
            width={170}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            {...CHART_TOOLTIP_PROPS}
            formatter={(v: number) => Number(v).toFixed(4)}
          />
          <Bar dataKey="importance" fill="hsl(var(--chart-5))" radius={[0, 3, 3, 0]} maxBarSize={14} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
