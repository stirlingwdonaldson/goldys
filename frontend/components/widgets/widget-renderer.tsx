"use client";

import { TrustIndicator } from "@/components/trust/trust-indicator";
import type { TrustSummary } from "@/lib/api/types";
import { BarChartWidgetView } from "./bar-chart-widget";
import { RankedListWidgetView } from "./ranked-list-widget";
import { StatWidgetView } from "./stat-widget";
import { TableWidgetView } from "./table-widget";
import { TimeSeriesWidgetView } from "./time-series-widget";
import type { WidgetSpec } from "./types";

/**
 * A non-null representative value for the widget, used only to tell {@link TrustIndicator}
 * whether the widget has data (so it shows freshness) or is empty (so it shows the missing
 * reason). The value itself is never displayed.
 */
function widgetValue(widget: WidgetSpec): number | null {
  switch (widget.type) {
    case "stat":
      return widget.value;
    case "time-series":
    case "bar-chart":
      for (const series of widget.series) {
        for (const point of series.points) {
          if (point.y != null) return point.y;
        }
      }
      return null;
    case "table":
      return widget.rows.length > 0 ? 1 : null;
    case "ranked-list":
      return widget.items.length > 0 ? 1 : null;
  }
}

interface WidgetRendererProps {
  widget: WidgetSpec;
  /** Optional trust + freshness summary from the metric result, surfaced as a subtle badge. */
  trust?: TrustSummary | null;
}

/**
 * Renders a validated widget spec through exhaustive dispatch. Unknown types degrade to a
 * notice — never a crash, never arbitrary code execution. When a metric's {@link TrustSummary} is
 * provided, a subtle trust badge is shown above the widget.
 */
export function WidgetRenderer({ widget, trust = null }: WidgetRendererProps) {
  if (!trust) {
    return <>{renderWidget(widget)}</>;
  }
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex justify-end">
        <TrustIndicator trust={trust} value={widgetValue(widget)} />
      </div>
      {renderWidget(widget)}
    </div>
  );
}

function renderWidget(widget: WidgetSpec) {
  switch (widget.type) {
    case "stat": return <StatWidgetView widget={widget} />;
    case "time-series": return <TimeSeriesWidgetView widget={widget} />;
    case "bar-chart": return <BarChartWidgetView widget={widget} />;
    case "table": return <TableWidgetView widget={widget} />;
    case "ranked-list": return <RankedListWidgetView widget={widget} />;
    default: {
      // Exhaustive for validated data, with a visible fallback for untyped callers.
      const unsupported: never = widget;
      return <p className="text-sm text-muted-foreground">Unsupported widget type: {(unsupported as { type: string }).type}</p>;
    }
  }
}
