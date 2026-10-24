"use client";

import { invoiceLabel } from "@/lib/invoice-reference";
import Link from "next/link";
import type { KeyboardEvent } from "react";
import { ChevronDown, ChevronUp, Link2, Maximize2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "@/components/ui/sheet";
import {
  InvoiceDetailBody,
  InvoicePdfActions,
  invoiceHref,
  useCopyInvoiceLink,
  useInvoiceData,
} from "./invoice-detail";

interface InvoiceSheetProps {
  /** The open invoice; null closes the sheet. */
  invoiceNumber: string | null;
  /** Invoice numbers in the order the list shows them, for previous/next. */
  order: string[];
  onNavigate: (invoiceNumber: string) => void;
  onClose: () => void;
  onRestoreFocus?: () => void;
}

/**
 * A right-hand slide-over for one invoice. It floats over the list, so it stays in view however
 * far the list is scrolled. ↑/↓ (or k/j) step through the list without closing; Esc closes.
 */
export function InvoiceSheet({ invoiceNumber, order, onNavigate, onClose, onRestoreFocus }: InvoiceSheetProps) {
  return (
    <Sheet open={invoiceNumber != null} onOpenChange={(open) => (open ? undefined : onClose())}>
      <SheetContent
        side="right"
        hideClose
        onCloseAutoFocus={(event) => { if (onRestoreFocus) { event.preventDefault(); onRestoreFocus(); } }}
        className="flex w-full flex-col gap-0 overflow-y-auto p-0 sm:max-w-5xl"
      >
        {invoiceNumber ? (
          <InvoiceSheetContent
            // Remount per invoice so the tab and scroll position reset on previous/next.
            key={invoiceNumber}
            invoiceNumber={invoiceNumber}
            order={order}
            onNavigate={onNavigate}
            onClose={onClose}
          />
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function isTypingTarget(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName))
  );
}

function InvoiceSheetContent({
  invoiceNumber,
  order,
  onNavigate,
  onClose,
}: InvoiceSheetProps & { invoiceNumber: string }) {
  const copyLink = useCopyInvoiceLink(invoiceNumber);
  const data = useInvoiceData(invoiceNumber);
  const invoice = data.detail.data;

  const index = order.indexOf(invoiceNumber);
  const prev = index > 0 ? order[index - 1] : null;
  const next = index >= 0 && index < order.length - 1 ? order[index + 1] : null;

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.metaKey || e.ctrlKey || e.altKey || isTypingTarget(e.target)) return;
    const target = e.key === "ArrowUp" || e.key === "k" ? prev : e.key === "ArrowDown" || e.key === "j" ? next : null;
    if (target) {
      e.preventDefault();
      onNavigate(target);
    }
  }


  return (
    <div className="flex min-h-full flex-col" onKeyDown={onKeyDown}>
      <div className="sticky top-0 z-10 flex items-center gap-1 border-b bg-background px-4 py-2">
        <Button
          variant="ghost"
          size="icon"
          className="size-8"
          disabled={!prev}
          onClick={() => prev && onNavigate(prev)}
          aria-label="Previous invoice"
          title="Previous invoice (↑)"
        >
          <ChevronUp className="size-4" aria-hidden="true" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="size-8"
          disabled={!next}
          onClick={() => next && onNavigate(next)}
          aria-label="Next invoice"
          title="Next invoice (↓)"
        >
          <ChevronDown className="size-4" aria-hidden="true" />
        </Button>
        {index >= 0 ? (
          <span className="ml-1 text-xs tabular-nums text-muted-foreground">
            {index + 1} of {order.length}
          </span>
        ) : null}
        <div className="ml-auto flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="size-8"
            onClick={copyLink}
            aria-label="Copy link"
            title="Copy link"
          >
            <Link2 className="size-4" aria-hidden="true" />
          </Button>
          <Button asChild variant="ghost" size="icon" className="size-8" title="Open full page">
            <Link href={invoiceHref(invoiceNumber)} aria-label="Expand to full page">
              <Maximize2 className="size-4" aria-hidden="true" />
            </Link>
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="size-8"
            onClick={onClose}
            aria-label="Close"
            title="Close (Esc)"
          >
            <X className="size-4" aria-hidden="true" />
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-5 p-6">
        <div className="flex flex-wrap items-start gap-3">
          <div className="min-w-0">
            <SheetDescription className="text-sm text-muted-foreground">
              {invoice?.supplierName ?? "Invoice"}
            </SheetDescription>
            <SheetTitle className="text-xl font-semibold">Invoice {invoiceLabel(invoice?.invoiceNumber, invoiceNumber)}</SheetTitle>
          </div>
          {invoice ? (
            <div className="ml-auto flex items-center gap-1">
              <InvoicePdfActions invoice={invoice} />
            </div>
          ) : null}
        </div>
        <InvoiceDetailBody data={data} layout="sheet" />
      </div>
    </div>
  );
}
