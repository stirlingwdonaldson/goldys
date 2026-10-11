"use client";

import { useMemo } from "react";
import { PermissionDenied } from "@/components/states/permission-denied";
import { Badge, type BadgeProps } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusDot } from "@/components/ui/status-dot";
import type { Api } from "@/lib/api";
import type { DeletedSaleRow, PaymentRow, SaleItemRow } from "@/lib/api/types";
import { formatCurrency, formatDay, formatNumber, formatPercent } from "@/lib/format";
import { useApiData } from "@/lib/use-api-data";
import { cn } from "@/lib/utils";
import { summariseSale, type CheckTone, type SaleSummary, type SaleVerdict } from "./sale-summary";

/** The start of the Lightspeed data; the wide range is cheap thanks to the `sale_number` index. */
export const DATA_EPOCH = "2020-11-23";

/** The list endpoints' page-size cap; fetched whole so a large sale isn't silently truncated. */
const FETCH_SIZE = 200;

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

interface SaleData {
  payments: PaymentRow[];
  items: SaleItemRow[];
  deletedOrder: DeletedSaleRow | null;
  truncatedPayments: number;
  truncatedItems: number;
}

/** Loads one sale's tenders, line items, and (if voided) its deleted order. */
async function loadSale(api: Api, saleNumber: string): Promise<SaleData> {
  const to = todayIso();
  const [payments, items, deletedSales] = await Promise.all([
    api.listPayments({ saleNumber, from: DATA_EPOCH, to }, 0, FETCH_SIZE),
    api.listSaleItems({ saleNumber, from: DATA_EPOCH, to }, 0, FETCH_SIZE),
    api.listDeletedSales({ saleNumber, from: DATA_EPOCH, to }, 0, FETCH_SIZE),
  ]);
  return {
    payments: payments.items,
    items: items.items,
    deletedOrder: deletedSales.items[0] ?? null,
    truncatedPayments: Math.max(0, payments.total - payments.items.length),
    truncatedItems: Math.max(0, items.total - items.items.length),
  };
}

const VERDICT: Record<SaleVerdict, { label: string; variant: NonNullable<BadgeProps["variant"]> }> = {
  voided: { label: "Voided", variant: "failed" },
  balanced: { label: "Balanced", variant: "success" },
  mismatch: { label: "Doesn't balance", variant: "conflict" },
  unpaid: { label: "No payment", variant: "missing" },
  "no-items": { label: "No line items", variant: "missing" },
  partial: { label: "Partial", variant: "missing" },
};

const DOT_TONE: Record<Exclude<CheckTone, "info">, "success" | "conflict" | "missing" | "failed"> = {
  success: "success",
  conflict: "conflict",
  missing: "missing",
  failed: "failed",
};

interface SaleSummaryViewProps {
  saleNumber: string;
  /** Injected by tests; defaults to the demo/live singleton. */
  apiOverride?: Api;
}

/**
 * One sale read as a receipt with a verdict: a headline sentence, the key totals, a
 * checklist of what stands out (balance, voids, tips, discounts, missing data), then the
 * line items and tenders side by side. Replaces the hub-and-spokes graph, which showed the
 * same rows but left the reader to do the arithmetic.
 */
export function SaleSummaryView({ saleNumber, apiOverride }: SaleSummaryViewProps) {
  const data = useApiData((api) => loadSale(api, saleNumber), [saleNumber], apiOverride);
  const summary = useMemo(
    () =>
      data.data
        ? summariseSale(
            data.data.payments,
            data.data.items,
            data.data.deletedOrder,
            data.data.truncatedPayments,
            data.data.truncatedItems,
          )
        : null,
    [data.data],
  );

  if (data.loading) return <Skeleton className="h-[220px] w-full" />;

  if (data.error) {
    if (data.error.code === "NOT_PERMITTED") return <PermissionDenied subject="sale detail" />;
    return (
      <p role="alert" className="text-sm text-destructive">
        Couldn&apos;t load this sale. {data.error.message}
      </p>
    );
  }

  if (
    !summary ||
    (summary.lines.length === 0 &&
      summary.tenders.length === 0 &&
      summary.deletedOrder == null &&
      summary.verdict !== "partial")
  ) {
    return (
      <p className="text-sm text-muted-foreground">
        No payments or line items recorded for sale {saleNumber}.
      </p>
    );
  }

  return <SaleSummaryBody summary={summary} />;
}

function SaleSummaryBody({ summary }: { summary: SaleSummary }) {
  const verdict = VERDICT[summary.verdict];
  // A voided-only sale has nothing to total or itemise; its check row says it all.
  const hasRows = summary.lines.length > 0 || summary.tenders.length > 0 || summary.verdict === "partial";
  return (
    <div className="flex flex-col gap-5 rounded-xl border bg-card p-5">
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={verdict.variant}>{verdict.label}</Badge>
          <p className="text-sm font-medium">{summary.headline}</p>
        </div>
        {summary.context.length > 0 ? (
          <dl className="flex flex-wrap gap-x-5 gap-y-1 text-xs">
            {summary.context.map((c) => (
              <div key={c.label} className="flex gap-1.5">
                <dt className="text-muted-foreground">{c.label}</dt>
                <dd className="font-medium">{c.label === "Date" ? formatDay(c.value) : c.value}</dd>
              </div>
            ))}
          </dl>
        ) : null}
      </div>

      {hasRows ? (
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Figure label="Rung up" value={formatCurrency(summary.itemsTotal)} />
          <Figure label="Paid" value={formatCurrency(summary.paidTotal)} />
          <Figure label="Tips" value={formatCurrency(summary.tipTotal)} muted={summary.tipTotal === 0} />
          <Figure
            label="Gross margin"
            value={formatPercent(summary.margin)}
            hint={summary.grossProfit != null ? `${formatCurrency(summary.grossProfit)} profit` : undefined}
          />
        </dl>
      ) : null}

      {summary.checks.length > 0 ? (
        <ul aria-label="What stands out" className="flex flex-col gap-1.5">
          {summary.checks.map((c) => (
            <li key={c.id} className="flex items-start gap-2.5 text-sm">
              <span className="mt-1.5 flex size-2 shrink-0 items-center justify-center">
                {c.tone === "info" ? (
                  <span aria-hidden="true" className="inline-block size-1.5 rounded-full bg-status-info" />
                ) : (
                  <StatusDot tone={DOT_TONE[c.tone]} />
                )}
              </span>
              <span className={cn(c.tone === "info" && "text-muted-foreground")}>{c.text}</span>
            </li>
          ))}
        </ul>
      ) : null}

      {hasRows ? (
        <div className="grid gap-5 md:grid-cols-[3fr_2fr]">
          <ReceiptTable
            title="Line items"
            empty="No line items recorded."
            rows={summary.lines.map((l) => ({
              key: l.key,
              primary: l.name,
              secondary: [
                l.quantity != null && l.unitPrice != null
                  ? `${formatNumber(l.quantity)} × ${formatCurrency(l.unitPrice)}`
                  : l.quantity != null
                    ? `× ${formatNumber(l.quantity)}`
                    : null,
                l.category,
              ]
                .filter(Boolean)
                .join(" · "),
              amount: formatCurrency(l.total),
            }))}
            total={summary.itemsTotal}
          />
          <ReceiptTable
            title="Tenders"
            empty="No tenders recorded."
            rows={summary.tenders.map((t) => ({
              key: t.key,
              primary: t.name,
              secondary: [
                t.tip ? `+${formatCurrency(t.tip)} tip` : null,
                t.surcharge ? `+${formatCurrency(t.surcharge)} surcharge` : null,
              ]
                .filter(Boolean)
                .join(" · "),
              amount: formatCurrency(t.amount),
            }))}
            total={summary.paidTotal}
          />
        </div>
      ) : null}
    </div>
  );
}

function Figure({
  label,
  value,
  hint,
  muted = false,
}: {
  label: string;
  value: string;
  hint?: string;
  muted?: boolean;
}) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5 rounded-lg bg-muted/50 px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={cn("text-lg font-semibold tabular-nums", muted && "text-muted-foreground")}>{value}</dd>
      {hint ? <dd className="text-xs text-muted-foreground tabular-nums">{hint}</dd> : null}
    </div>
  );
}

interface ReceiptRow {
  key: string;
  primary: string;
  secondary: string;
  amount: string;
}

function ReceiptTable({
  title,
  empty,
  rows,
  total,
}: {
  title: string;
  empty: string;
  rows: ReceiptRow[];
  total: number | null;
}) {
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</h3>
      {rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">{empty}</p>
      ) : (
        <table className="w-full text-sm">
          <caption className="sr-only">{title}</caption>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key} className="border-b border-dashed last:border-0">
                <td className="py-1.5 pr-3 align-top">
                  <div className="font-medium">{r.primary}</div>
                  {r.secondary ? <div className="text-xs text-muted-foreground">{r.secondary}</div> : null}
                </td>
                <td className="py-1.5 text-right align-top tabular-nums">{r.amount}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t">
              <th scope="row" className="pt-2 text-left font-semibold">
                Total
              </th>
              <td className="pt-2 text-right font-semibold tabular-nums">{formatCurrency(total)}</td>
            </tr>
          </tfoot>
        </table>
      )}
    </div>
  );
}
