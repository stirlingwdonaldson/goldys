"use client";

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
          <li key={item.label} className="flex items-baseline justify-between gap-3 py-2">
            <span className="text-sm">
              <span className="text-muted-foreground">{i + 1}.</span> {item.label}
              {item.badge ? (
                <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-700">
                  {item.badge}
                </span>
              ) : null}
            </span>
            <span className="text-sm tabular-nums text-muted-foreground">{values(item)}</span>
          </li>
        ))}
      </ol>
    </WidgetShell>
  );
}
