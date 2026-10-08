"use client";

import { TrustIndicator } from "@/components/trust/trust-indicator";
import type { TrustSummary } from "@/lib/api/types";
import { widgetRegistry } from "./registry";
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
 * Renders a widget spec through the shared registry. Unknown or malformed types degrade to a
 * notice — never a crash, never arbitrary code execution. When a metric's {@link TrustSummary} is
 * provided, a subtle trust badge is shown above the widget.
 */
export function WidgetRenderer({ widget, trust = null }: WidgetRendererProps) {
  const render = widgetRegistry[widget.type];
  if (!render) {
    return <p className="text-sm text-muted-foreground">Unsupported widget type: {widget.type}</p>;
  }
  if (!trust) {
    return <>{render(widget)}</>;
  }
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex justify-end">
        <TrustIndicator trust={trust} value={widgetValue(widget)} />
      </div>
      {render(widget)}
    </div>
  );
}
