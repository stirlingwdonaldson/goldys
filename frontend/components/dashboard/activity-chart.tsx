"use client";

import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type { BarChartWidget } from "@/components/widgets/types";
import type { ActivityPoint } from "@/lib/api";

interface ActivityChartProps {
  points: ActivityPoint[];
  title?: string;
}

/** Connector-run activity, rendered through the shared widget runtime. */
export function ActivityChart({ points, title = "Connector activity" }: ActivityChartProps) {
  const widget: BarChartWidget = {
    schemaVersion: 2,
    id: "connector-activity",
    type: "bar-chart",
    title,
    description: "Clean and failed connector runs per day.",
    series: [
      { key: "clean", label: "Clean", points: points.map((p) => ({ x: p.date, y: p.clean })) },
      { key: "failed", label: "Failed", points: points.map((p) => ({ x: p.date, y: p.failed })) },
    ],
    stacked: true,
  };
  return <WidgetRenderer widget={widget} />;
}
