"use client";

import type { StatWidget } from "./types";
import { formatValue } from "./format";
import { WidgetShell } from "./widget-shell";

export function StatWidgetView({ widget }: { widget: StatWidget }) {
  return (
    <WidgetShell title={widget.title} description={widget.description}>
      <p className="text-2xl font-semibold tabular-nums">{formatValue(widget.value, widget.format)}</p>
      {widget.hint ? <p className="mt-1 text-xs text-muted-foreground">{widget.hint}</p> : null}
    </WidgetShell>
  );
}
