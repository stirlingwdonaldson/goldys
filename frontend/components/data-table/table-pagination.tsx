"use client";

import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

/** The rows-per-page choices every table offers. */
export const PAGE_SIZE_OPTIONS = [10, 25, 50, 100] as const;
/** Tables open on a short page; people widen it with the rows-per-page menu. */
export const DEFAULT_PAGE_SIZE = PAGE_SIZE_OPTIONS[0];

interface TablePaginationProps {
  /** Zero-based page index. */
  page: number;
  pageSize: number;
  /** Total rows across every page. */
  total: number;
  onPageChange: (page: number) => void;
  /** Called with the new size; callers return to the first page. */
  onPageSizeChange: (pageSize: number) => void;
  className?: string;
}

/**
 * The one pagination bar every table uses, whether it pages on the client or the server:
 * rows per page, "1–10 of 240", and first/previous/next/last. Hidden while everything fits on
 * one page at the smallest size, since there is nothing to page or shrink then.
 */
export function TablePagination({ page, pageSize, total, onPageChange, onPageSizeChange, className }: TablePaginationProps) {
  if (total <= pageSize && pageSize <= PAGE_SIZE_OPTIONS[0]) return null;
  // A caller-chosen size outside the standard list still needs to show in the menu.
  const sizes = [...new Set<number>([...PAGE_SIZE_OPTIONS, pageSize])].sort((a, b) => a - b);
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const from = page * pageSize + 1;
  const to = Math.min((page + 1) * pageSize, total);
  const last = pageCount - 1;

  return (
    <div className={cn("flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground", className)}>
      <div className="flex items-center gap-2">
        <span id="rows-per-page-label">Rows per page</span>
        <Select value={String(pageSize)} onValueChange={(v) => onPageSizeChange(Number(v))}>
          <SelectTrigger className="h-8 w-[4.5rem]" aria-labelledby="rows-per-page-label">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {sizes.map((size) => (
              <SelectItem key={size} value={String(size)}>
                {size}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex items-center gap-1">
        <span className="mr-2 tabular-nums">
          {from}–{to} of {total}
        </span>
        <PageButton label="First page" disabled={page <= 0} onClick={() => onPageChange(0)}>
          <ChevronsLeft />
        </PageButton>
        <PageButton label="Previous page" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft />
        </PageButton>
        <PageButton label="Next page" disabled={page >= last} onClick={() => onPageChange(page + 1)}>
          <ChevronRight />
        </PageButton>
        <PageButton label="Last page" disabled={page >= last} onClick={() => onPageChange(last)}>
          <ChevronsRight />
        </PageButton>
      </div>
    </div>
  );
}

function PageButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <Button variant="outline" size="icon" className="size-8" aria-label={label} title={label} disabled={disabled} onClick={onClick}>
      {children}
    </Button>
  );
}

/**
 * Page/size state for a server-paged table. `resetKey` is anything that starts a new result set
 * (a filter, a date range): when it changes the table returns to the first page, otherwise the
 * old page index could point past the new total.
 */
export function usePageState(resetKey: string) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSizeState] = useState<number>(DEFAULT_PAGE_SIZE);
  useEffect(() => {
    setPage(0);
  }, [resetKey]);
  const setPageSize = (size: number) => {
    setPageSizeState(size);
    setPage(0);
  };
  return { page, setPage, pageSize, setPageSize };
}

/** Client-side paging over an in-memory list, for plain tables outside {@link DataTable}. */
export function useClientPagination<T>(rows: T[], resetKey = "") {
  const state = usePageState(resetKey);
  const pageCount = Math.max(1, Math.ceil(rows.length / state.pageSize));
  // Clamp rather than trust the stored index: the list can shrink under it.
  const page = Math.min(state.page, pageCount - 1);
  const pageRows = rows.slice(page * state.pageSize, (page + 1) * state.pageSize);
  return {
    pageRows,
    pagination: {
      page,
      pageSize: state.pageSize,
      total: rows.length,
      onPageChange: state.setPage,
      onPageSizeChange: state.setPageSize,
    },
  };
}
