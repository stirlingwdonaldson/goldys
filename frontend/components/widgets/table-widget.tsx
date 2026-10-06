"use client";

import type { TableWidget } from "./types";
import { WidgetShell } from "./widget-shell";

export function TableWidgetView({ widget }: { widget: TableWidget }) {
  if (widget.columns.length === 0 || widget.rows.length === 0) {
    return (
      <WidgetShell title={widget.title} description={widget.description}>
        <p className="text-sm text-muted-foreground">No data yet.</p>
      </WidgetShell>
    );
  }
  return (
    <WidgetShell title={widget.title} description={widget.description}>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr>
              {widget.columns.map((c) => (
                <th key={c.key} className="border-b px-2 py-1 text-left font-medium">
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {widget.rows.map((row, i) => (
              <tr key={i}>
                {widget.columns.map((c) => (
                  <td key={c.key} className="border-b px-2 py-1">
                    {String(row[c.key] ?? "")}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </WidgetShell>
  );
}
