"use client";

import { Activity } from "lucide-react";
import { StatCard } from "@/components/dashboard/stat-card";
import type { WidgetSpec } from "./types";

interface WidgetRendererProps {
  widget: WidgetSpec;
}

/** Renders a widget spec. Unknown types render a notice, never crash. */
export function WidgetRenderer({ widget }: WidgetRendererProps) {
  switch (widget.type) {
    case "line-chart":
      return <LineChartWidget widget={widget} />;
    case "bar-chart":
      return <BarChartWidget widget={widget} />;
    case "table":
      return <TableWidget title={widget.title} data={widget.data} />;
    case "stat":
      return (
        <StatCard
          label={widget.title}
          value={String(firstValue(widget.data) ?? "—")}
          icon={Activity}
        />
      );
    default:
      return (
        <p className="text-sm text-muted-foreground">Unsupported widget type: {widget.type}</p>
      );
  }
}

function LineChartWidget({ widget }: { widget: WidgetSpec }) {
  const { yKeys } = seriesKeys(widget.data);
  const values = numericValues(widget.data, yKeys[0]);
  const points = polylinePoints(values);
  return (
    <ChartShell title={widget.title}>
      <svg
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        className="h-full w-full"
        aria-hidden="true"
      >
        <polyline
          points={points}
          fill="none"
          stroke="#2563eb"
          strokeWidth="2"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
    </ChartShell>
  );
}

function BarChartWidget({ widget }: { widget: WidgetSpec }) {
  const { yKeys } = seriesKeys(widget.data);
  const values = numericValues(widget.data, yKeys[0]);
  const max = Math.max(1, ...values);
  const barWidth = 100 / Math.max(1, values.length);
  return (
    <ChartShell title={widget.title}>
      <svg
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        className="h-full w-full"
        aria-hidden="true"
      >
        {values.map((v, i) => {
          const h = (v / max) * 100;
          return (
            <rect key={i} x={i * barWidth} y={100 - h} width={barWidth * 0.8} height={h} fill="#2563eb" />
          );
        })}
      </svg>
    </ChartShell>
  );
}

function ChartShell({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-sm font-medium">{title}</p>
      <div role="img" aria-label={title} className="mt-3 h-48">
        {children}
      </div>
    </div>
  );
}

function TableWidget({ title, data }: { title: string; data: Record<string, unknown>[] }) {
  const columns = columnKeys(data);
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-sm font-medium">{title}</p>
      <div className="mt-3 overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c} className="border-b px-2 py-1 text-left font-medium">
                  {c}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((row, i) => (
              <tr key={i}>
                {columns.map((c) => (
                  <td key={c} className="border-b px-2 py-1">
                    {String(row[c] ?? "")}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function columnKeys(data: Record<string, unknown>[]): string[] {
  const set = new Set<string>();
  for (const row of data) for (const k of Object.keys(row)) set.add(k);
  return [...set];
}

/** The x-axis key (date when present) and the numeric series keys. */
function seriesKeys(data: Record<string, unknown>[]): { xKey: string; yKeys: string[] } {
  const all = columnKeys(data);
  const xKey = all.includes("date") ? "date" : (all[0] ?? "index");
  const yKeys = all.filter((k) => k !== xKey && data.some((r) => typeof r[k] === "number"));
  return { xKey, yKeys };
}

function numericValues(data: Record<string, unknown>[], key: string | undefined): number[] {
  if (!key) return [];
  return data.map((d) => Number(d[key])).filter((n) => Number.isFinite(n));
}

function polylinePoints(values: number[]): string {
  const max = Math.max(1, ...values);
  return values
    .map(
      (v, i) =>
        `${(i / Math.max(1, values.length - 1)) * 100},${100 - (v / max) * 100}`,
    )
    .join(" ");
}

function firstValue(data: Record<string, unknown>[]): unknown {
  const row = data[0];
  if (!row) return null;
  for (const v of Object.values(row)) if (typeof v === "number") return v;
  return Object.values(row)[0] ?? null;
}
