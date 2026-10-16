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
import { AXIS_TICK, CHART_TOOLTIP_PROPS, compactAxis } from "@/components/widgets/chart-style";
import type { WeekdayErrorRow } from "@/lib/api/types";

/** A weekday error row with both members present (null rows are dropped). */
type PlottableWeekday = WeekdayErrorRow & { day: string; mape: number };

/**
 * Error by weekday — where the model is systematically weak. Rows whose day or MAPE is `null`
 * are dropped rather than drawn as a zero bar.
 */
export function WeekdayErrorChart({ rows }: { rows: WeekdayErrorRow[] }) {
  const data: PlottableWeekday[] = rows.filter(
    (r): r is PlottableWeekday => r.day != null && r.mape != null,
  );
  if (data.length === 0) {
    return (
      <EmptyState
        title="No weekday error data"
        description="Weekday accuracy appears once the run succeeds."
      />
    );
  }
  return (
    <div
      role="img"
      aria-label="Error by weekday"
      data-testid="weekday-error-chart"
      className="h-56 w-full"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
          <XAxis
            dataKey="day"
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            tickFormatter={(v: string) => v.slice(0, 3)}
          />
          <YAxis
            tick={AXIS_TICK}
            tickFormatter={(v: number) => compactAxis(v, "percent")}
            tickLine={false}
            axisLine={false}
            width={44}
          />
          <Tooltip
            {...CHART_TOOLTIP_PROPS}
            formatter={(v: number) => `${Number(v).toFixed(0)}%`}
          />
          <Bar
            dataKey="mape"
            name="MAPE"
            fill="hsl(var(--chart-3))"
            radius={[4, 4, 0, 0]}
            maxBarSize={40}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
