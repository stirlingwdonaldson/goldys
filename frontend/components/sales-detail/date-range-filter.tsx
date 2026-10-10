"use client";

import { Input } from "@/components/ui/input";

export interface DateRange {
  from: string;
  to: string;
}

/** Trailing 30-day window, so the screen renders without the operator touching a date input. */
export function defaultRange(): DateRange {
  const to = new Date();
  const from = new Date(Date.UTC(to.getUTCFullYear(), to.getUTCMonth(), to.getUTCDate() - 29));
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

interface DateRangeFilterProps {
  from: string;
  to: string;
  onChange: (range: DateRange) => void;
}

/**
 * Shared trading-date filter for the Sales detail tabs. The page owns the state so
 * every tab reads the same window; this component only renders and reports changes.
 */
export function DateRangeFilter({ from, to, onChange }: DateRangeFilterProps) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="flex flex-col gap-1">
        <label htmlFor="sales-detail-from" className="text-xs text-muted-foreground">
          From
        </label>
        <Input
          id="sales-detail-from"
          type="date"
          value={from}
          onChange={(e) => onChange({ from: e.target.value, to })}
          className="w-40"
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="sales-detail-to" className="text-xs text-muted-foreground">
          To
        </label>
        <Input
          id="sales-detail-to"
          type="date"
          value={to}
          onChange={(e) => onChange({ from, to: e.target.value })}
          className="w-40"
        />
      </div>
    </div>
  );
}
