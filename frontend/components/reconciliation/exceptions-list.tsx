"use client";

import { useEffect, useRef, useState } from "react";
import { CheckCircle2, ChevronRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Pagination } from "@/components/reconciliation/pagination";
import { SourceLabel } from "@/components/sources/source-tile";
import { formatCurrency, formatDay, formatNumber, humanizeKey } from "@/lib/format";
import { Kbd } from "@/components/ui/kbd";
import { cn } from "@/lib/utils";
import type { ExceptionStatus, ReconciliationException } from "@/lib/api";
import { difference, parseAmount } from "./source-values";

const PAGE_SIZE = 25;

export type StatusFilter = "all" | "conflict" | "missing";

interface ExceptionsListProps {
  kind: "daily" | "product";
  exceptions: ReconciliationException[];
  status: StatusFilter;
  query: string;
  selectedId: string | null;
  onReview: (ex: ReconciliationException) => void;
  /** J/K/Enter only act while no sheet or dialog is open. */
  keyboardEnabled: boolean;
}

/** Counts (quantity sold, covers) are not money, so only other numeric fields get "$". */
function isMoneyField(field: string): boolean {
  return !/quantity|qty|count|covers/i.test(field);
}

function formatValue(kind: "daily" | "product", field: string, value: string | null): string {
  if (value == null) return "No data";
  const n = kind === "daily" ? parseAmount(value) : null;
  if (n == null) return value;
  return isMoneyField(field) ? formatCurrency(n) : formatNumber(n, 2);
}

export function StatusBadge({ status, missingSource }: { status: ExceptionStatus; missingSource?: string }) {
  return status === "conflict" ? (
    <Badge variant="conflict">Conflict</Badge>
  ) : (
    // Kept short so it never wraps in the row; the source with no data is named in the
    // values column and in the tooltip.
    <Badge variant="missing" title={missingSource ? `No data from ${missingSource}` : undefined}>
      Missing data
    </Badge>
  );
}

export function filterExceptions(
  exceptions: ReconciliationException[],
  status: StatusFilter,
  query: string,
): ReconciliationException[] {
  const q = query.trim().toLowerCase();
  return exceptions.filter((e) => {
    if (status !== "all" && e.status !== status) return false;
    if (!q) return true;
    const haystack = [e.recordId, e.id, e.entity, e.field, formatDay(e.recordId), formatDay(e.id.split(":")[0])]
      .join(" ")
      .toLowerCase();
    return haystack.includes(q);
  });
}

/**
 * The reconciliation queue. Each row shows every source's value and the size of
 * the gap, so most decisions can be sized up before opening the record.
 */
export function ExceptionsList({
  kind,
  exceptions,
  status,
  query,
  selectedId,
  onReview,
  keyboardEnabled,
}: ExceptionsListProps) {
  const [page, setPage] = useState(1);
  const [active, setActive] = useState(0);
  const rowRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const filtered = filterExceptions(exceptions, status, query);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount);
  const start = (safePage - 1) * PAGE_SIZE;
  const rows = filtered.slice(start, start + PAGE_SIZE);

  // Reset to the first page when the filter changes underneath us.
  useEffect(() => {
    setPage(1);
    setActive(0);
  }, [status, query, kind]);

  // J/K to move, Enter to open: a long queue shouldn't need the mouse (heuristic 7).
  useEffect(() => {
    if (!keyboardEnabled) return;
    function onKey(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (target && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key === "j" || e.key === "k") {
        e.preventDefault();
        setActive((i) => {
          const next = e.key === "j" ? Math.min(rows.length - 1, i + 1) : Math.max(0, i - 1);
          rowRefs.current[next]?.focus();
          return next;
        });
      } else if (e.key === "Enter" && rows[active] && document.activeElement === document.body) {
        onReview(rows[active]);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [keyboardEnabled, rows, active, onReview]);

  if (exceptions.length === 0) {
    return (
      <div className="flex flex-col items-center rounded-xl border border-dashed px-6 py-12 text-center">
        <CheckCircle2 className="size-6 text-status-success" aria-hidden="true" />
        <p className="mt-3 text-sm font-semibold">All reconciled</p>
        <p className="mt-1 text-sm text-muted-foreground">
          No {kind === "daily" ? "daily-sales" : "product-sales"} items need a decision right now.
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-hidden rounded-xl border">
        {rows.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-muted-foreground">
            Nothing matches these filters.
          </p>
        ) : (
          <ul>
            {rows.map((ex, i) => {
              const gap = difference(ex.sources);
              const missing = ex.sources.find((s) => s.value == null)?.source;
              const date = kind === "product" ? ex.id.split(":")[0] : ex.recordId;
              const label = humanizeKey(ex.field);
              const selected = selectedId === ex.recordId || selectedId === ex.id;
              return (
                <li key={ex.id} className="border-t first:border-t-0">
                  <button
                    type="button"
                    ref={(el) => {
                      rowRefs.current[i] = el;
                    }}
                    onClick={() => onReview(ex)}
                    onFocus={() => setActive(i)}
                    aria-label={`Review ${kind === "product" ? ex.recordId : ""} ${formatDay(date)} ${ex.field}`.replace(/\s+/g, " ").trim()}
                    className={cn(
                      // Wide: one line of [record | values | status]. Narrow: values drop to their own
                      // row under the record instead of wrapping mid-cell.
                      "grid w-full grid-cols-[1fr_auto] items-center gap-x-4 gap-y-2 px-4 py-3 text-left lg:grid-cols-[minmax(7rem,9rem)_1fr_auto]",
                      "transition-colors hover:bg-muted/50 focus-visible:bg-muted/60 focus-visible:outline-none",
                      selected && "bg-muted/60 shadow-[inset_3px_0_0_hsl(var(--primary))]",
                    )}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold">
                        {kind === "product" ? ex.recordId : formatDay(date)}
                      </span>
                      <span className="block truncate text-xs text-muted-foreground">
                        {kind === "product" ? `${formatDay(date)} · ${label}` : label}
                      </span>
                    </span>
                    <span className="col-span-2 row-start-2 flex min-w-0 flex-wrap items-center gap-x-4 gap-y-1 lg:col-span-1 lg:row-start-auto">
                      {ex.sources.map((s) => (
                        <SourceLabel key={s.source} source={s.source}>
                          <span
                            className={cn(
                              "text-sm tabular-nums",
                              s.value == null ? "italic text-muted-foreground" : "font-medium",
                            )}
                          >
                            {formatValue(kind, ex.field, s.value)}
                          </span>
                        </SourceLabel>
                      ))}
                      {gap != null ? (
                        <Badge variant="neutral" className="tabular-nums">
                          Δ {kind === "daily" && isMoneyField(ex.field) ? formatCurrency(gap) : formatNumber(gap, 2)}
                        </Badge>
                      ) : null}
                    </span>
                    <span className="col-start-2 row-start-1 flex items-center gap-2 lg:col-start-3">
                      <StatusBadge status={ex.status} missingSource={missing} />
                      <ChevronRight className="size-4 text-muted-foreground" aria-hidden="true" />
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
        <Pagination
          page={safePage}
          pageCount={pageCount}
          onPageChange={setPage}
          from={filtered.length === 0 ? 0 : start + 1}
          to={Math.min(start + PAGE_SIZE, filtered.length)}
          total={filtered.length}
        />
      </div>
      <p className="hidden items-center gap-1.5 px-1 text-xs text-muted-foreground md:flex">
        <Kbd>J</Kbd>
        <Kbd>K</Kbd> move · <Kbd>Enter</Kbd> open · <Kbd>Esc</Kbd> close
      </p>
    </div>
  );
}
