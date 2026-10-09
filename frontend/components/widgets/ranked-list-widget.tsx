"use client";

import { Badge } from "@/components/ui/badge";
import type { RankedListWidget } from "./types";
import { WidgetShell } from "./widget-shell";

export function RankedListWidgetView({ widget }: { widget: RankedListWidget }) {
  if (widget.items.length === 0) {
    return (
      <WidgetShell title={widget.title} description={widget.description}>
        <p className="text-sm text-muted-foreground">No data yet.</p>
      </WidgetShell>
    );
  }
  const values = (item: (typeof widget.items)[number]) =>
    [item.primary, item.secondary].filter((v): v is string => !!v).join(" × ");

  return (
    <WidgetShell title={widget.title} description={widget.description}>
      <ol className="divide-y">
        {widget.items.map((item, i) => (
          <li key={item.label} className="flex items-center justify-between gap-3 py-2.5">
            <span className="flex min-w-0 items-center gap-2.5 text-sm">
              <span className="w-4 text-right text-xs tabular-nums text-muted-foreground">{i + 1}</span>
              <span className="truncate">{item.label}</span>
              {item.badge ? <Badge variant="conflict">{item.badge}</Badge> : null}
            </span>
            <span className="text-sm tabular-nums text-muted-foreground">{values(item)}</span>
          </li>
        ))}
      </ol>
    </WidgetShell>
  );
}
