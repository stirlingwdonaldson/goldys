"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
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
 * The food/beverage split of the forecast, stacked so each day's bar height is the total net
 * revenue. A `null` food or bev segment simply doesn't draw for that day (never shown as zero).
 */
export function FoodBevSplitChart({ rows }: { rows: ForecastDayRow[] }) {
  if (rows.length === 0) {
    return (
      <EmptyState
        title="No split data"
        description="The food/beverage split appears once the run succeeds."
      />
    );
  }
  return (
    <div
      role="img"
      aria-label="Food and beverage split"
      data-testid="food-bev-split-chart"
      className="h-56 w-full"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
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
          <Bar dataKey="food" name="Food" stackId="a" fill="hsl(var(--chart-1))" maxBarSize={38} />
          <Bar
            dataKey="bev"
            name="Beverage"
            stackId="a"
            fill="hsl(var(--chart-5))"
            radius={[4, 4, 0, 0]}
            maxBarSize={38}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
