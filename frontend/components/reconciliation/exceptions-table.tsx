"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Pagination } from "@/components/reconciliation/pagination";
import type { ExceptionStatus, ReconciliationException, SourceValue } from "@/lib/api";

const PAGE_SIZE = 25;

type StatusFilter = "all" | "conflict" | "missing";

interface ExceptionsTableProps {
  kind: "daily" | "product";
  exceptions: ReconciliationException[];
  onReview: (ex: ReconciliationException) => void;
}

function formatValue(kind: "daily" | "product", value: string | null): string {
  if (value == null) return "no data";
  if (kind === "daily") {
    const n = Number(value);
    if (Number.isFinite(n)) {
      return `$${n.toLocaleString("en-AU", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })}`;
    }
  }
  return value;
}

function StatusBadge({ status }: { status: ExceptionStatus }) {
  return status === "conflict" ? (
    <Badge className="border-transparent bg-status-warning text-status-warning-foreground">
      Conflict
    </Badge>
  ) : (
    <Badge className="border-transparent bg-status-missing text-status-missing-foreground">
      Missing data
    </Badge>
  );
}

/** The "why": each source's reported value, plus the reason this record needs a decision. */
function SourcesCell({
  kind,
  sources,
  status,
}: {
  kind: "daily" | "product";
  sources: SourceValue[];
  status: ExceptionStatus;
}) {
  return (
    <div className="flex flex-col gap-1">
      {sources.map((s) => (
        <div key={s.source} className="flex items-baseline gap-2">
          <span className="w-24 shrink-0 text-xs uppercase tracking-wide text-muted-foreground">
            {s.source}
          </span>
          <span
            className={
              s.value == null ? "text-sm text-muted-foreground" : "text-sm font-medium tabular-nums"
            }
          >
            {formatValue(kind, s.value)}
          </span>
        </div>
      ))}
      <p className="mt-1 text-xs text-muted-foreground">
        {status === "conflict"
          ? "These figures disagree — review and choose the authoritative one."
          : "Only one source has data — the other source is absent."}
      </p>
    </div>
  );
}

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
        active
          ? "border-primary bg-primary/10 text-primary"
          : "text-muted-foreground hover:bg-accent hover:text-foreground"
      }`}
    >
      {children}
    </button>
  );
}

/** A paginated table of reconciliation exceptions with status filtering. */
export function ExceptionsTable({ kind, exceptions, onReview }: ExceptionsTableProps) {
  const [status, setStatus] = useState<StatusFilter>("all");
  const [page, setPage] = useState(1);

  const filtered = status === "all" ? exceptions : exceptions.filter((e) => e.status === status);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount);
  const start = (safePage - 1) * PAGE_SIZE;
  const rows = filtered.slice(start, start + PAGE_SIZE);

  const conflictCount = exceptions.filter((e) => e.status === "conflict").length;
  const missingCount = exceptions.filter((e) => e.status === "missing").length;

  function changeStatus(next: StatusFilter) {
    setStatus(next);
    setPage(1);
  }

  if (exceptions.length === 0) {
    return (
      <div className="rounded-lg border bg-card p-6 text-center">
        <p className="text-sm font-medium">All reconciled</p>
        <p className="mt-1 text-sm text-muted-foreground">
          No {kind === "daily" ? "daily-sales" : "product-sales"} items need a decision right now.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border bg-card">
      <div className="flex flex-wrap items-center gap-2 border-b px-4 py-3">
        <span className="text-xs font-medium text-muted-foreground">Filter:</span>
        <FilterChip active={status === "all"} onClick={() => changeStatus("all")}>
          All ({exceptions.length})
        </FilterChip>
        <FilterChip active={status === "conflict"} onClick={() => changeStatus("conflict")}>
          Conflicts ({conflictCount})
        </FilterChip>
        <FilterChip active={status === "missing"} onClick={() => changeStatus("missing")}>
          Missing ({missingCount})
        </FilterChip>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              {kind === "product" && (
                <th scope="col" className="px-4 py-2 font-medium">
                  Date
                </th>
              )}
              <th scope="col" className="px-4 py-2 font-medium">
                {kind === "product" ? "Product" : "Trading date"}
              </th>
              <th scope="col" className="px-4 py-2 font-medium">
                Status
              </th>
              <th scope="col" className="px-4 py-2 font-medium">
                Sources
              </th>
              <th scope="col" className="px-4 py-2">
                <span className="sr-only">Action</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((ex) => (
              <tr key={ex.id} className="border-b last:border-0 hover:bg-accent/40">
                {kind === "product" && (
                  <td className="px-4 py-3 align-top tabular-nums text-muted-foreground">
                    {ex.id.split(":")[0]}
                  </td>
                )}
                <td className="px-4 py-3 align-top font-medium">{ex.recordId}</td>
                <td className="px-4 py-3 align-top">
                  <StatusBadge status={ex.status} />
                </td>
                <td className="px-4 py-3 align-top">
                  <SourcesCell kind={kind} sources={ex.sources} status={ex.status} />
                </td>
                <td className="px-4 py-3 align-top text-right">
                  <Button variant="outline" size="sm" onClick={() => onReview(ex)}>
                    Review
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination
        page={safePage}
        pageCount={pageCount}
        onPageChange={setPage}
        from={filtered.length === 0 ? 0 : start + 1}
        to={Math.min(start + PAGE_SIZE, filtered.length)}
        total={filtered.length}
      />
    </div>
  );
}
