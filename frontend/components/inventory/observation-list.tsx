"use client";
import type { Api, InventoryObservation } from "@/lib/api";
import { useState } from "react";
import { useApiData } from "@/lib/use-api-data";
import { DataQualifications } from "@/components/data-display/data-qualifications";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { EmptyState } from "@/components/states/empty-state";
import { Button } from "@/components/ui/button";

export function ObservationList({ from: initialFrom, to: initialTo, apiOverride }: { from: string; to: string; apiOverride?: Api }) {
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [kind, setKind] = useState<"ALL" | "COUNT" | "WASTE">("ALL");
  const [page, setPage] = useState(0);
  const result = useApiData(api => api.getInventoryObservations(from, to, kind, page, 50), [from, to, kind, page], apiOverride);
  return <div className="flex flex-col gap-3">
    <p className="text-sm text-muted-foreground">Counts and waste retain source units and dates. Quantities are not added across units. Source values have an unverified currency, tax and valuation basis.</p>
    <div className="flex flex-wrap gap-3">
      <label>From <input aria-label="Observation from" type="date" value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} /></label>
      <label>To <input aria-label="Observation to" type="date" value={to} onChange={e => { setTo(e.target.value); setPage(0); }} /></label>
      <label>Kind <select aria-label="Observation kind" value={kind} onChange={e => { setKind(e.target.value as typeof kind); setPage(0); }}><option value="ALL">All</option><option value="COUNT">Counts</option><option value="WASTE">Waste</option></select></label>
    </div>
    {result.loading ? <LoadingState rows={3} /> : result.error ? result.error.code === "NOT_PERMITTED"
      ? <PermissionDenied subject="stock and waste observations" />
      : <ErrorState title="Couldn't load observations" message={result.error.message} onRetry={result.reload} />
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

function ObservationRow({ row }: { row: InventoryObservation }) {
  return <tr>
    <td className="p-2">{row.date}</td><td>{row.kind}</td>
    <td>{row.productName}{row.stockCode ? ` / ${row.stockCode}` : ""}</td>
    <td>{decimal(row.quantity, 4)} {row.unit ?? "Unknown unit"}{row.unitSize ? ` (${row.unitSize})` : ""}</td>
    <td>{decimal(row.unitCost)}</td><td>{decimal(row.sourceValue)}</td>
    <td>{row.area ?? "Unknown"}</td><td>{stocktakeState(row)}</td>
    <td>{row.sourceSystem} {row.sourceRecordRef}</td>
  </tr>;
}

function ObservationTable({ rows, page }: { rows: InventoryObservation[]; page: number }) {
  const headings = ["Date", "Kind", "Product / code", "Quantity / unit", "Unit cost", "Source value", "Area", "Stocktake state", "Source reference"];
  return <div className="overflow-x-auto"><table className="w-full text-sm">
    <caption className="text-left">Dated source observations — page {page + 1}</caption>
    <thead><tr>{headings.map(label => <th key={label} scope="col" className="p-2 text-left">{label}</th>)}</tr></thead>
    <tbody>{rows.map(row => <ObservationRow key={`${row.kind}:${row.sourceSystem}:${row.sourceRecordRef}`} row={row} />)}</tbody>
  </table></div>;
}
