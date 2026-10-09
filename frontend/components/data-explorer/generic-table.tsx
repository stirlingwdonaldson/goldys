"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { GenericRow } from "@/lib/api/types";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";

/**
 * Renders generic {@link GenericRow}s as a shadcn data table. Columns are the union of keys across
 * all rows so a column that happens to be null in the first row is not hidden when later rows carry
 * it. Paging is the server's (the explorer's Pager), so sorting here orders the current page only.
 */
export function GenericTable({ rows }: { rows: GenericRow[] }) {
  const columns = useMemo<ColumnDef<GenericRow>[]>(() => {
    const keys = rows.length > 0 ? Array.from(new Set(rows.flatMap((r) => Object.keys(r.columns)))) : [];
    return keys.map((key) => ({
      id: key,
      accessorFn: (r) => r.columns[key] ?? "",
      header: ({ column }) => <DataTableColumnHeader column={column} title={key} />,
      cell: ({ getValue }) => (
        <span className="whitespace-nowrap font-mono text-xs">{getValue() as string}</span>
      ),
      meta: { title: key },
    }));
  }, [rows]);

  return <DataTable columns={columns} data={rows} pageSize={false} getRowId={(r) => r.id} />;
}
