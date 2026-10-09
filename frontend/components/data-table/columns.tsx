"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Workflow } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SourceLabel } from "@/components/sources/source-tile";
import { sourceIdentity } from "@/lib/sources";
import { formatCurrency, formatDay, formatNumber, toNumber, type Numeric } from "@/lib/format";
import { DataTableColumnHeader } from "./data-table-column-header";

/**
 * Column factories for the recurring column types, so every table formats
 * dates, money and sources the same way (heuristic 4). Each factory sorts on
 * the raw value and only formats for display.
 */

/** An ISO date column: sorts on YYYY-MM-DD, shows "Sun 5 Oct". */
export function dateColumn<T>(key: keyof T & string, title = "Date"): ColumnDef<T> {
  return {
    id: key,
    accessorFn: (r) => r[key] as string,
    header: ({ column }) => <DataTableColumnHeader column={column} title={title} />,
    cell: ({ getValue }) => <span className="whitespace-nowrap">{formatDay(getValue() as string)}</span>,
    meta: { title },
    enableHiding: false,
  };
}

/** A source-system column: filters and sorts on the human label, shows the source tile. */
export function sourceColumn<T>(
  get: (row: T) => string | null | undefined,
  options: { id?: string; title?: string } = {},
): ColumnDef<T> {
  const title = options.title ?? "Source";
  return {
    id: options.id ?? "source",
    accessorFn: (r) => {
      const s = get(r);
      return s ? sourceIdentity(s).label : "";
    },
    header: title,
    cell: ({ row }) => {
      const s = get(row.original);
      return s ? <SourceLabel source={s} /> : <span className="text-muted-foreground">—</span>;
    },
    meta: { title },
  };
}

/** A right-aligned currency column. */
export function currencyColumn<T>(id: string, title: string, get: (row: T) => Numeric): ColumnDef<T> {
  return {
    id,
    accessorFn: (r) => toNumber(get(r)),
    header: ({ column }) => <DataTableColumnHeader column={column} title={title} />,
    cell: ({ row }) => <span className="tabular-nums">{formatCurrency(get(row.original))}</span>,
    meta: { title, align: "right" },
  };
}

/** A right-aligned number column, with up to `maxDecimals` fraction digits. */
export function numberColumn<T>(
  id: string,
  title: string,
  get: (row: T) => Numeric,
  maxDecimals = 0,
): ColumnDef<T> {
  return {
    id,
    accessorFn: (r) => toNumber(get(r)),
    header: ({ column }) => <DataTableColumnHeader column={column} title={title} />,
    cell: ({ row }) => <span className="tabular-nums">{formatNumber(get(row.original), maxDecimals)}</span>,
    meta: { title, align: "right" },
  };
}

/** The row action that opens a figure's provenance graph. */
export function traceColumn<T>(describe: (row: T) => string, onTrace: (row: T) => void): ColumnDef<T> {
  return {
    id: "trace",
    header: () => <span className="sr-only">Trace</span>,
    cell: ({ row }) => (
      <Button
        variant="ghost"
        size="sm"
        className="h-7"
        aria-label={`Trace ${describe(row.original)}`}
        onClick={() => onTrace(row.original)}
      >
        <Workflow aria-hidden="true" />
        Trace
      </Button>
    ),
    enableSorting: false,
    enableHiding: false,
    meta: { align: "right" },
  };
}
