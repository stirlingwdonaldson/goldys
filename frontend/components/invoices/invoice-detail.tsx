"use client";

import { AlertTriangle, ArrowDownRight, ArrowUpRight, Download, ExternalLink } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { useToast } from "@/components/feedback/toast";
import { useDemoMode } from "@/lib/demo-mode";
import { invoiceFlagLabel } from "@/lib/work-queue";
import { formatCurrency, formatDay, formatNumber, formatPercent } from "@/lib/format";
import type { InvoiceDetail, InvoiceIngestFlag, LineGraphNode } from "@/lib/api/types";
import { LoadingState } from "@/components/states/loading-state";
import { InlineError } from "@/components/states/inline-error";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  checkTotals,
  dayBefore,
  displayProductName,
  distinctStockCode,
  lineMatchKey,
  priceChanges,
  relativeDue,
  type PriceChange,
} from "./invoice-lines";

export function invoicePdfUrl(invoiceNumber: string): string {
  return `/api/invoices/${encodeURIComponent(invoiceNumber)}/pdf`;
}

/** Full-page route for one invoice; also what "Copy link" copies. */
export function invoiceHref(invoiceNumber: string): string {
  return `/invoices/${encodeURIComponent(invoiceNumber)}`;
}

/** Copies the invoice's full-page link to the clipboard, with a toast either way. */
export function useCopyInvoiceLink(invoiceNumber: string): () => Promise<void> {
  const { toast } = useToast();
  return async () => {
    try {
      await navigator.clipboard.writeText(`${window.location.origin}${invoiceHref(invoiceNumber)}`);
      toast({ title: "Link copied", tone: "success" });
    } catch {
      toast({ title: "Couldn't copy the link", description: "Copy it from the address bar instead.", tone: "error" });
    }
  };
}

export interface InvoiceData {
  detail: ReturnType<typeof useApiData<InvoiceDetail>>;
  flags: InvoiceIngestFlag[];
  previous: InvoiceDetail | null;
}

/**
 * Loads one invoice plus the context the detail view annotates it with: this invoice's ingestion
 * flags, and the same supplier's previous invoice (for unit-cost movement). The previous invoice
 * is the newest one dated strictly before this one; same-day invoices have no defined order, so
 * they're never compared against each other.
 */
export function useInvoiceData(invoiceNumber: string): InvoiceData {
  const detail = useApiData((api) => api.getInvoice(invoiceNumber), [invoiceNumber]);
  const allFlags = useApiData((api) => api.listInvoiceFlags());

  const supplier = detail.data?.supplierName ?? null;
  const invoiceDate = detail.data?.invoiceDate ?? null;
  const previous = useApiData(
    async (api) => {
      if (!supplier || !invoiceDate) return null;
      const page = await api.listInvoices({ supplier, to: dayBefore(invoiceDate) }, 0, 1);
      const prev = page.items[0];
      return prev ? api.getInvoice(prev.invoiceNumber) : null;
    },
    [supplier, invoiceDate],
  );

  return {
    detail,
    // Flags and the price comparison are annotations: if they fail, the invoice still shows.
    flags: (allFlags.data ?? []).filter((f) => f.invoiceNumber === invoiceNumber),
    previous: previous.data ?? null,
  };
}

/** Header figures: total, issue and due dates, purchase number. */
export function InvoiceSummaryGrid({ invoice }: { invoice: InvoiceDetail }) {
  return (
    <dl className="grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
      <div className="col-span-2 sm:col-span-1">
        <dt className="text-xs text-muted-foreground">Total</dt>
        <dd className="text-2xl font-semibold tabular-nums">{formatCurrency(invoice.totalAmount)}</dd>
        {invoice.amountExTax != null ? (
          <dd className="mt-0.5 text-xs text-muted-foreground tabular-nums">
            Ex-tax {formatCurrency(invoice.amountExTax)} · GST {formatCurrency(invoice.gstAmount)} · Freight{" "}
            {formatCurrency(invoice.freightAmount)}
          </dd>
        ) : null}
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Issued</dt>
        <dd className="text-sm">{formatDay(invoice.invoiceDate)}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Due</dt>
        <dd className="text-sm">
          {invoice.dueDate ? (
            <>
              {formatDay(invoice.dueDate)}
              <span className="block text-xs text-muted-foreground">{relativeDue(invoice.dueDate)}</span>
            </>
          ) : (
            "—"
          )}
        </dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Purchase no.</dt>
        <dd className="break-all text-sm">{invoice.purchaseNumber ?? "—"}</dd>
      </div>
    </dl>
  );
}

/** This invoice's ingestion anomalies, so a reader knows which lines to double-check. */
export function InvoiceFlags({ flags }: { flags: InvoiceIngestFlag[] }) {
  if (flags.length === 0) return null;
  return (
    <div role="status" className="rounded-xl bg-status-conflict-soft p-4 text-sm">
      <p className="flex items-center gap-2 font-medium text-status-conflict">
        <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
        {flags.length === 1 ? "1 ingestion issue" : `${flags.length} ingestion issues`}
      </p>
      <ul className="mt-2 flex flex-col gap-1 pl-6">
        {flags.map((f, i) => (
          <li key={`${f.flagType}-${f.stockCode ?? ""}-${i}`}>
            <span className="font-medium">{invoiceFlagLabel(f.flagType)}</span>
            {f.stockCode ? <span className="font-mono text-xs"> · {f.stockCode}</span> : null}
            {f.detail ? <span className="text-muted-foreground"> — {f.detail}</span> : null}
          </li>
        ))}
      </ul>
    </div>
  );
}

function PriceChangeBadge({ change, previousLabel }: { change: PriceChange; previousLabel: string }) {
  const up = change.ratio > 0;
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  const sign = up ? "+" : "−";
  return (
    <Badge
      variant={up ? "conflict" : "success"}
      className="gap-0.5 px-1.5 py-0 text-[11px] font-medium tabular-nums"
      title={`Was ${formatCurrency(change.previousUnitCost)} on ${previousLabel}`}
    >
      <Icon className="size-3" aria-hidden="true" />
      <span className="sr-only">{up ? "Up" : "Down"} </span>
      {sign}
      {formatPercent(Math.abs(change.ratio))}
    </Badge>
  );
}

/**
 * The line items: product (with its stock code only when it says something the name doesn't),
 * quantity with unit, unit cost with movement versus the previous invoice, and line total. The
 * footer adds the lines up and flags a gap against the header total.
 */
export function InvoiceLinesTable({
  invoice,
  flags,
  previous,
}: {
  invoice: InvoiceDetail;
  flags: InvoiceIngestFlag[];
  previous: InvoiceDetail | null;
}) {
  const lines = invoice.lines;
  if (lines.length === 0) {
    return <p className="text-sm text-muted-foreground">No line detail for this invoice.</p>;
  }

  const changes = previous ? priceChanges(lines, previous.lines) : new Map<string, PriceChange>();
  const previousLabel = previous ? `${formatDay(previous.invoiceDate)} (invoice ${previous.invoiceNumber})` : "";
  const flaggedCodes = new Set(flags.map((f) => f.stockCode).filter((c): c is string => c != null));
  const totals = checkTotals(lines, invoice.amountExTax);

  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-x-auto rounded-lg border">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-muted/40 text-left text-xs text-muted-foreground">
              <th className="px-3 py-2 font-medium">Product</th>
              <th className="px-3 py-2 text-right font-medium">Qty</th>
              <th className="px-3 py-2 text-right font-medium">Unit cost</th>
              <th className="px-3 py-2 text-right font-medium">Total</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l, i) => (
              <InvoiceLineRow
                key={`${l.stockCode ?? ""}-${i}`}
                line={l}
                flagged={l.stockCode != null && flaggedCodes.has(l.stockCode)}
                change={changes.get(lineMatchKey(l)) ?? null}
                previousLabel={previousLabel}
              />
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t bg-muted/20">
              <td className="px-3 py-2 text-xs text-muted-foreground" colSpan={3}>
                {lines.length === 1 ? "1 line" : `${lines.length} lines`}
              </td>
              <td className="px-3 py-2 text-right font-medium tabular-nums">
                {formatCurrency(lines.reduce((s, l) => s + l.lineTotal, 0))}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
      {totals && !totals.matches ? (
        <p className="flex items-start gap-2 rounded-lg bg-status-conflict-soft px-3 py-2 text-xs text-status-conflict">
          <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
          <span>
            Lines add up to {formatCurrency(totals.linesTotal)} ex-tax,{" "}
            {formatCurrency(Math.abs(totals.difference))} {totals.difference > 0 ? "less" : "more"} than the
            invoice&apos;s ex-tax total. Check for freight charged as a line, or a line that wasn&apos;t
            captured.
          </span>
        </p>
      ) : null}
      {previous && changes.size > 0 ? (
        <p className="text-xs text-muted-foreground">
          Price changes compare unit cost with {previous.supplierName}&apos;s previous invoice,{" "}
          {previous.invoiceNumber} on {formatDay(previous.invoiceDate)}.
        </p>
      ) : null}
    </div>
  );
}

function InvoiceLineRow({
  line,
  flagged,
  change,
  previousLabel,
}: {
  line: LineGraphNode;
  flagged: boolean;
  change: PriceChange | null;
  previousLabel: string;
}) {
  const code = distinctStockCode(line);
  return (
    <tr className={cn("border-b align-top last:border-0", flagged && "bg-status-conflict-soft/60")}>
      <td className="px-3 py-2">
        <span className="flex items-center gap-1.5">
          {flagged ? (
            <AlertTriangle className="size-3.5 shrink-0 text-status-conflict" aria-label="Flagged line" />
          ) : null}
          {displayProductName(line.productNameKey)}
        </span>
        {code ? <span className="block font-mono text-[11px] text-muted-foreground">{code}</span> : null}
      </td>
      <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums">
        {formatNumber(line.quantity, 3)}
        {line.uom ? <span className="ml-1 text-xs text-muted-foreground">{line.uom.toLowerCase()}</span> : null}
      </td>
      <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums">
        <span className="flex flex-col items-end gap-0.5">
          {formatCurrency(line.unitCost)}
          {change ? <PriceChangeBadge change={change} previousLabel={previousLabel} /> : null}
        </span>
      </td>
      <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums">
        {formatCurrency(line.lineTotal)}
        {line.lineGstAmount != null || line.lineTaxRate != null || line.incGst != null ? (
          <span className="block text-[11px] font-normal text-muted-foreground">
            {line.incGst === true ? "incl GST" : line.incGst === false ? "ex GST" : ""}
            {line.lineGstAmount != null ? ` · GST ${formatCurrency(line.lineGstAmount)}` : ""}
            {line.lineTaxRate != null ? ` · ${line.lineTaxRate}%` : ""}
          </span>
        ) : null}
      </td>
    </tr>
  );
}

/** The PDF preview, or a note when there isn't one to show. */
export function InvoicePdf({
  invoice,
  className,
}: {
  invoice: Pick<InvoiceDetail, "invoiceNumber" | "hasPdf">;
  className?: string;
}) {
  const { demo } = useDemoMode();
  if (!invoice.hasPdf) {
    return <p className="text-sm text-muted-foreground">No PDF was received for this invoice.</p>;
  }
  if (demo) {
    return <p className="text-xs text-muted-foreground">PDF preview unavailable in demo mode</p>;
  }
  return (
    <iframe
      data-testid="invoice-pdf-preview"
      title="Invoice PDF preview"
      src={invoicePdfUrl(invoice.invoiceNumber)}
      className={cn("w-full rounded-lg border", className)}
    />
  );
}

/** Download and open-in-new-tab buttons; nothing when there's no PDF or in demo mode. */
export function InvoicePdfActions({ invoice }: { invoice: Pick<InvoiceDetail, "invoiceNumber" | "hasPdf"> }) {
  const { demo } = useDemoMode();
  if (!invoice.hasPdf || demo) return null;
  const url = invoicePdfUrl(invoice.invoiceNumber);
  return (
    <>
      <Button asChild variant="outline" size="sm">
        <a href={`${url}?disposition=attachment`}>
          <Download className="mr-1.5 h-4 w-4" aria-hidden="true" />
          Download
        </a>
      </Button>
      <Button asChild variant="ghost" size="sm">
        <a href={url} target="_blank" rel="noreferrer">
          <ExternalLink className="mr-1.5 h-4 w-4" aria-hidden="true" />
          Open PDF
        </a>
      </Button>
    </>
  );
}

/**
 * The body shared by the slide-over and the full page. The extracted invoice data (header
 * figures, then line items, with any ingestion issues at the bottom) sits next to the original
 * PDF so the two can be read side by side; on narrow screens they stack instead. `layout` only
 * tunes the PDF height for the two containers.
 */
export function InvoiceDetailBody({
  data,
  layout,
}: {
  data: InvoiceData;
  layout: "sheet" | "page";
}) {
  const { detail, flags, previous } = data;
  if (detail.loading) return <LoadingState rows={4} />;
  if (detail.error || !detail.data) {
    return (
      <InlineError>
        {detail.error?.code === "NOT_FOUND"
          ? "This invoice doesn't exist, or is no longer current."
          : "Couldn't load this invoice. Refresh the page to try again."}
      </InlineError>
    );
  }
  const invoice = detail.data;

  const extracted = (
    <div className="flex min-w-0 flex-col gap-5">
      <InvoiceSummaryGrid invoice={invoice} />
      <InvoiceLinesTable invoice={invoice} flags={flags} previous={previous} />
      <InvoiceFlags flags={flags} />
    </div>
  );

  // Without a PDF there's nothing to set alongside, so keep one readable column.
  if (!invoice.hasPdf) {
    return <div className="flex max-w-4xl flex-col gap-5">{extracted}</div>;
  }

  return (
    <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] md:items-start">
      {extracted}
      <div className="md:sticky md:top-6">
        <InvoicePdf invoice={invoice} className={layout === "page" ? "h-[80vh]" : "h-[70vh]"} />
      </div>
    </div>
  );
}
