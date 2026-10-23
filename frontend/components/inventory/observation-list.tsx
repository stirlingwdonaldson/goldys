"use client";
import type { Api, InventoryObservation } from "@/lib/api";
import { useState } from "react";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import { DataQualifications } from "@/components/data-display/data-qualifications";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { EmptyState } from "@/components/states/empty-state";
import type { ColumnDef } from "@tanstack/react-table";
import { DataTable } from "@/components/data-table/data-table";
import { DatePicker } from "@/components/ui/date-picker";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";

export function ObservationList({ from: initialFrom, to: initialTo, apiOverride }: { from: string; to: string; apiOverride?: Api }) {
  const queryApi = useApi();
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [kind, setKind] = useState<"ALL" | "COUNT" | "WASTE">("ALL");
  const [page, setPage] = useState(0);
  const result = useQuery({ queryKey: ["getInventoryObservations", from, to, kind, page, 50], queryFn: ({ signal }) => (apiOverride ?? queryApi).getInventoryObservations(from, to, kind, page, 50, { signal }) });
  return <div className="flex flex-col gap-3">
    <p className="text-sm text-muted-foreground">Counts and waste retain source units and dates. Quantities are not added across units. Source values have an unverified currency, tax and valuation basis.</p>
    <div className="flex flex-wrap gap-3">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="observation-from">From</Label>
        <DatePicker id="observation-from" aria-label="Observation from" value={from} max={to}
          onChange={value => { setFrom(value); setPage(0); }} />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="observation-to">To</Label>
        <DatePicker id="observation-to" aria-label="Observation to" value={to} min={from}
          onChange={value => { setTo(value); setPage(0); }} />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="observation-kind">Kind</Label>
        <Select value={kind} onValueChange={value => { setKind(value as typeof kind); setPage(0); }}>
          <SelectTrigger id="observation-kind" aria-label="Observation kind" className="w-36"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All</SelectItem>
            <SelectItem value="COUNT">Counts</SelectItem>
            <SelectItem value="WASTE">Waste</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
    {result.isPending ? <LoadingState rows={3} /> : result.error ? result.error.code === "NOT_PERMITTED"
      ? <PermissionDenied subject="stock and waste observations" />
      : <ErrorState title="Couldn't load observations" message={result.error.message} onRetry={result.refetch} />
      : result.data ? <>
        <DataQualifications notices={result.data.notices} />
        {result.data.observations.length === 0 ? <EmptyState title="No recorded observations" description="No observations match this range and kind; this does not establish zero stock or waste." /> :
          <ObservationTable rows={result.data.observations} page={page} />}
        <div className="flex gap-2"><Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous observations</Button><Button variant="outline" disabled={!result.data.hasMore} onClick={() => setPage(page + 1)}>Next observations</Button></div>
      </> : null}
  </div>;
}


function decimal(value: number | null, places = 2) {
  return value === null ? "Unknown" : value.toLocaleString("en-AU", { maximumFractionDigits: places });
}

function stocktakeState(row: InventoryObservation) {
  if (row.kind === "WASTE") return "Not applicable";
  const partial = row.stocktakePartial === null ? "Partial unknown" : row.stocktakePartial ? "Partial" : "Full as reported";
  const locked = row.stocktakeLocked === null ? "lock unknown" : row.stocktakeLocked ? "locked" : "unlocked";
  return `${partial}; ${locked}`;
}

const observationColumns: ColumnDef<InventoryObservation>[] = [
  { accessorKey: "date", header: "Date" },
  { accessorKey: "kind", header: "Kind" },
  { id: "product", header: "Product / code", accessorFn: row => `${row.productName}${row.stockCode ? ` / ${row.stockCode}` : ""}` },
  { id: "quantity", header: "Quantity / unit", cell: ({ row: { original: row } }) =>
    `${decimal(row.quantity, 4)} ${row.unit ?? "Unknown unit"}${row.unitSize ? ` (${row.unitSize})` : ""}` },
  { accessorKey: "unitCost", header: "Unit cost", cell: ({ row }) => decimal(row.original.unitCost), meta: { align: "right", title: "Unit cost" } },
  { accessorKey: "sourceValue", header: "Source value", cell: ({ row }) => decimal(row.original.sourceValue), meta: { align: "right", title: "Source value" } },
  { accessorKey: "area", header: "Area", cell: ({ row }) => row.original.area ?? "Unknown" },
  { id: "stocktake", header: "Stocktake state", accessorFn: row => stocktakeState(row) },
  { id: "source", header: "Source reference", accessorFn: row => `${row.sourceSystem} ${row.sourceRecordRef}` },
];

function ObservationTable({ rows, page }: { rows: InventoryObservation[]; page: number }) {
  return <div role="region" aria-label="Dated source observations" className="flex flex-col gap-3">
    <p className="text-sm text-muted-foreground">Dated source observations — page {page + 1}</p>
    <DataTable columns={observationColumns} data={rows} pageSize={false}
      getRowId={row => `${row.kind}:${row.sourceSystem}:${row.sourceRecordRef}`} />
  </div>;
}
