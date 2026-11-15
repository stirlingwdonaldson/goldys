"use client";

import { useMemo } from "react";
import type { ColumnDef, SortingState, OnChangeFn } from "@tanstack/react-table";
import type { GenericRow } from "@/lib/api/types";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";

/**
 * Renders generic {@link GenericRow}s as a shadcn data table. Columns are the union of keys across
 * all rows so a column that happens to be null in the first row is not hidden when later rows carry
 * it. The server orders the entire authorised dataset before paging.
 */
export function GenericTable({ rows, sorting, onSortingChange, pending }: { rows: GenericRow[]; sorting: SortingState; onSortingChange: OnChangeFn<SortingState>; pending?: boolean }) {
  const columns = useMemo<ColumnDef<GenericRow>[]>(() => {
    const keys = rows.length > 0 ? Array.from(new Set(rows.flatMap((r) => Object.keys(r.columns)))) : [];
    return keys.map((key) => ({
      id: key,
      accessorFn: (r) => r.columns[key],
      header: ({ column }) => <DataTableColumnHeader column={column} title={key} />,
      cell: ({ getValue }) => (
        <span className="whitespace-nowrap font-mono text-xs">{getValue() === undefined || getValue() === null ? <span className="text-muted-foreground">Not recorded</span> : getValue() as string}</span>
      ),
      meta: { title: key },
    }));
  }, [rows]);

  return <div className="space-y-2"><p className="text-xs text-muted-foreground">Columns reflect fields present in these rows.</p><DataTable sorting={sorting} onSortingChange={onSortingChange} manualSorting sortingPending={pending} columns={columns} data={rows} pageSize={false} getRowId={(r) => r.id} /></div>;
}
