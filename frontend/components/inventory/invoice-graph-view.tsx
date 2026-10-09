"use client";

import { useMemo, useState } from "react";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { Skeleton } from "@/components/ui/skeleton";
import { useApiData } from "@/lib/use-api-data";
import { buildInvoiceGraph } from "./invoice-graph";

const SUPPLIER_PREFIX = "supplier:";
const INVOICE_PREFIX = "invoice:";

interface InvoiceGraphViewProps {
  from: string;
  to: string;
}

/**
 * The invoice node graph: suppliers → invoices → line items, one column revealed per drill. The
 * `onDrill` handler decodes the `supplier:`/`invoice:` ids the builder stamps on nodes.
 */
export function InvoiceGraphView({ from, to }: InvoiceGraphViewProps) {
  const [focusedSupplier, setFocusedSupplier] = useState<string | null>(null);
  const [focusedInvoice, setFocusedInvoice] = useState<string | null>(null);

  const suppliers = useApiData((api) => api.getInvoiceGraphSuppliers(from, to), [from, to]);
  const invoices = useApiData(
    (api) =>
      focusedSupplier
        ? api.getInvoiceGraphInvoices(focusedSupplier, from, to)
        : Promise.resolve(null),
    [focusedSupplier, from, to],
  );
  const lines = useApiData(
    (api) =>
      focusedInvoice ? api.getInvoiceGraphLines(focusedInvoice) : Promise.resolve(null),
    [focusedInvoice],
  );

  const graph = useMemo(
    () =>
      buildInvoiceGraph({
        suppliers: suppliers.data ?? [],
        invoices: invoices.data,
        lines: lines.data,
        focusedSupplier,
        focusedInvoice,
      }),
    [suppliers.data, invoices.data, lines.data, focusedSupplier, focusedInvoice],
  );

  function onDrill(id: string) {
    if (id.startsWith(SUPPLIER_PREFIX)) {
      setFocusedSupplier(id.slice(SUPPLIER_PREFIX.length));
      setFocusedInvoice(null);
    } else if (id.startsWith(INVOICE_PREFIX)) {
      setFocusedInvoice(id.slice(INVOICE_PREFIX.length));
    }
  }

  function back() {
    if (focusedInvoice) setFocusedInvoice(null);
    else setFocusedSupplier(null);
  }

  if (suppliers.loading) return <Skeleton className="h-[360px] w-full" />;

  return (
    <div className="flex flex-col gap-2">
      {focusedSupplier || focusedInvoice ? (
        <button
          type="button"
          onClick={back}
          className="self-start text-sm text-muted-foreground underline-offset-2 hover:underline"
        >
          ← Back
        </button>
      ) : null}
      <FlowCanvas
        graph={graph}
        onDrill={onDrill}
        ariaLabel="Invoices: suppliers, invoices, and line items"
        height={360}
      />
    </div>
  );
}
