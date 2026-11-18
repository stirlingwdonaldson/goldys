"use client";

import {
  Area, Bar, CartesianGrid, Cell, ComposedChart, Line, Pie, PieChart, PolarAngleAxis,
  PolarGrid, PolarRadiusAxis, Radar, RadarChart, RadialBar, RadialBarChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import type { ChartWidget } from "./types";
import { flattenSeries } from "./flatten";
import { WidgetShell } from "./widget-shell";
import { ChartDataTable } from "./chart-data-table";
import { AXIS_TICK, CHART_TOOLTIP_PROPS, SERIES_COLORS, compactAxis, formatXTick } from "./chart-style";
import { formatValue } from "./format";

const color = (index: number) => SERIES_COLORS[index % SERIES_COLORS.length];

/** The bounded shadcn/Recharts families share tokens, accessible data and deterministic marks. */
export function AnalysisChart({ widget }: { widget: ChartWidget }) {
  const categories = ["PIE", "DONUT", "RADIAL"].includes(widget.kind);
  const legend = categories
    ? widget.series[0].points.map((point, index) => ({ key: String(index), label: `${point.x}: ${formatValue(point.y, widget.yFormat)}` }))
    : widget.series;
  return <WidgetShell title={widget.title} description={widget.description}>
    <div role="img" aria-label={`${widget.title} — ${widget.kind.toLowerCase()} chart`} className="h-64 min-w-0 w-full">
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        {plot(widget)}
      </ResponsiveContainer>
    </div>
    <ul aria-label="Chart legend" className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-muted-foreground">
      {legend.map((item, index) => <li key={item.key} className="inline-flex min-w-0 items-center gap-2">
        <span aria-hidden="true" className="h-0.5 w-4 shrink-0" style={{ backgroundColor: color(index) }} />
        <span className="min-w-0 break-words">{item.label}</span>
      </li>)}
    </ul>
    <ChartDataTable widget={widget} />
  </WidgetShell>;
}

function plot(widget: ChartWidget) {
  const data = flattenSeries(widget.series);
  const tick = (value: number) => compactAxis(widget.yFormat === "percent" ? value * 100 : value, widget.yFormat);
  const tooltip = <Tooltip {...CHART_TOOLTIP_PROPS} formatter={(value: number) => formatValue(value, widget.yFormat)} />;
  if (widget.kind === "PIE" || widget.kind === "DONUT") {
    return <PieChart accessibilityLayer>
      {tooltip}
      <Pie data={widget.series[0].points} dataKey="y" nameKey="x" innerRadius={widget.kind === "DONUT" ? "55%" : 0} outerRadius="85%" isAnimationActive={false}>
        {widget.series[0].points.map((point, index) => <Cell key={point.x} fill={color(index)} />)}
      </Pie>
    </PieChart>;
  }
  if (widget.kind === "RADIAL") {
    const points = widget.series[0].points.map((point, index) => ({ ...point, name: point.x, fill: color(index) }));
    const maximum = Math.max(...points.map(point => point.y ?? 0));
    return <RadialBarChart data={points} innerRadius="20%" outerRadius="90%" startAngle={90} endAngle={-270}>
      <PolarAngleAxis type="number" domain={[0, maximum]} tick={false} />
      <RadialBar dataKey="y" background isAnimationActive={false} />
      {tooltip}
    </RadialBarChart>;
  }
  if (widget.kind === "RADAR") {
    return <RadarChart data={data} outerRadius="70%">
      <PolarGrid />
      <PolarAngleAxis dataKey="x" tick={AXIS_TICK} tickFormatter={categoryTick} />
      <PolarRadiusAxis angle={90} tickCount={3} axisLine={false} tick={AXIS_TICK} tickFormatter={tick} />
      {tooltip}
      {widget.series.map((series, index) => <Radar key={series.key} dataKey={series.key} name={series.label} stroke={color(index)} fill={color(index)} fillOpacity={0.08} isAnimationActive={false} />)}
    </RadarChart>;
  }
  return <ComposedChart accessibilityLayer data={data} layout={widget.horizontal ? "vertical" : "horizontal"} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
    <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
    <XAxis dataKey={widget.horizontal ? undefined : "x"} type={widget.horizontal ? "number" : "category"} tick={AXIS_TICK} tickLine={false} axisLine={false} tickFormatter={widget.horizontal ? tick : categoryTick} />
    <YAxis dataKey={widget.horizontal ? "x" : undefined} type={widget.horizontal ? "category" : "number"} width={widget.horizontal ? 100 : 60} tick={AXIS_TICK} tickLine={false} axisLine={false} tickFormatter={widget.horizontal ? categoryTick : tick} />
    {tooltip}
    {widget.series.map((series, index) => {
      const props = { dataKey: series.key, name: series.label, stroke: color(index), fill: color(index), isAnimationActive: false };
      if (widget.kind === "BAR") return <Bar key={series.key} {...props} stackId={widget.stacked ? "values" : undefined} maxBarSize={32} />;
      if (widget.kind === "AREA") return <Area key={series.key} {...props} type="linear" stackId={widget.stacked ? "values" : undefined} fillOpacity={0.15} connectNulls={false} />;
      return <Line key={series.key} {...props} type="linear" strokeWidth={2} strokeDasharray={index >= SERIES_COLORS.length ? "6 3" : undefined} connectNulls={false} dot={{ r: 2 }} />;
    })}
  </ComposedChart>;
}

function categoryTick(value: string) {
  const label = formatXTick(value);
  return label.length > 18 ? `${label.slice(0, 17)}…` : label;
}
