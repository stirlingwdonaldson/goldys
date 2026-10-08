"use client";

import type { GenericRow } from "@/lib/api/types";

/** Renders generic {@link GenericRow}s as a table whose columns come from the first row's keys. */
export function GenericTable({ rows }: { rows: GenericRow[] }) {
  const columns = rows.length > 0 ? Object.keys(rows[0].columns) : [];
  return (
    <div className="overflow-x-auto rounded-lg border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/40 text-left text-xs text-muted-foreground">
            {columns.map((c) => (
              <th key={c} className="px-3 py-2 font-medium">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id} className="border-b last:border-0">
              {columns.map((c) => (
                <td key={c} className="whitespace-nowrap px-3 py-2 font-mono text-xs">
                  {row.columns[c] ?? ""}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
