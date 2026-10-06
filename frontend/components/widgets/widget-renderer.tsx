"use client";

import { widgetRegistry } from "./registry";
import type { WidgetSpec } from "./types";

/**
 * Renders a widget spec through the shared registry. Unknown or malformed types degrade to a
 * notice — never a crash, never arbitrary code execution.
 */
export function WidgetRenderer({ widget }: { widget: WidgetSpec }) {
  const render = widgetRegistry[widget.type];
  if (!render) {
    return <p className="text-sm text-muted-foreground">Unsupported widget type: {widget.type}</p>;
  }
  return <>{render(widget)}</>;
}
