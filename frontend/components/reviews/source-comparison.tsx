"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { EmptyState } from "@/components/states/empty-state";
import { SourceTile } from "@/components/sources/source-tile";
import { formatNumber } from "@/lib/format";
import type { SourceSummary } from "@/lib/api";

/**
 * Human label for a review source code. The source registry (`lib/sources.ts`)
 * falls back to the raw code for sources it does not know (Google, TripAdvisor),
 * so review sources get their own friendly names here.
 */
export function reviewSourceLabel(source: string): string {
  const known: Record<string, string> = {
    OPENTABLE: "OpenTable",
    GOOGLE: "Google",
    TRIPADVISOR: "TripAdvisor",
  };
  return known[source] ?? source;
}

const columns: ColumnDef<SourceSummary>[] = [
  {
    accessorKey: "source",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Source" />,
    cell: ({ getValue }) => {
      const source = getValue<string>();
      return (
        <span className="inline-flex items-center gap-1.5">
          <SourceTile source={source} />
          {reviewSourceLabel(source)}
        </span>
      );
    },
    meta: { title: "Source" },
  },
  {
    accessorKey: "total",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Reviews" />,
    cell: ({ getValue }) => <span className="tabular-nums">{getValue<number>()}</span>,
    meta: { title: "Reviews", align: "right" },
  },
  {
    accessorKey: "averageRating",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Avg rating" />,
    cell: ({ getValue }) => formatNumber(getValue<number>(), 1),
    meta: { title: "Avg rating", align: "right" },
  },
  {
    accessorKey: "positive",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Positive" />,
    cell: ({ getValue }) => <span className="tabular-nums">{getValue<number>()}</span>,
    meta: { title: "Positive", align: "right" },
  },
  {
    accessorKey: "negative",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Negative" />,
    cell: ({ getValue }) => <span className="tabular-nums">{getValue<number>()}</span>,
    meta: { title: "Negative", align: "right" },
  },
];

/** OpenTable vs Google vs TripAdvisor: volume, average rating, and the sentiment split. */
export function SourceComparison({ sources }: { sources: SourceSummary[] }) {
  if (sources.length === 0) {
    return (
      <EmptyState
        title="No source data"
        description="Per-source figures appear once reviews are resolved."
      />
    );
  }
  return <DataTable columns={columns} data={sources} pageSize={false} getRowId={(r) => r.source} />;
}
