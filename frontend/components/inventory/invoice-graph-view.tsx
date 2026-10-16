"use client";

import { useMemo, useState } from "react";
import { DataQualifications } from "@/components/data-display/data-qualifications";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { PermissionDenied } from "@/components/states/permission-denied";
import { Skeleton } from "@/components/ui/skeleton";
import type { Api } from "@/lib/api";
import { useApiData } from "@/lib/use-api-data";
import { buildInvoiceGraph } from "./invoice-graph";

const SUPPLIER_PREFIX = "supplier:";
const INVOICE_PREFIX = "invoice:";

interface InvoiceGraphViewProps {
  from: string;
  to: string;
  /** Injected by tests; defaults to the demo/live singleton. */
  apiOverride?: Api;
}

/**
 * The invoice node graph: suppliers → invoices → line items, one column revealed per drill. The
 * `onDrill` handler decodes the `supplier:`/`invoice:` ids the builder stamps on nodes.
 */
export function InvoiceGraphView({ from, to, apiOverride }: InvoiceGraphViewProps) {
  const [focusedSupplier, setFocusedSupplier] = useState<string | null>(null);
  const [focusedInvoice, setFocusedInvoice] = useState<string | null>(null);

  const suppliers = useApiData(
    (api) => api.getInvoiceGraphSuppliers(from, to),
    [from, to],
    apiOverride,
  );
  const invoices = useApiData(
    (api) => loadSupplierInvoices(api, focusedSupplier, from, to),
    [focusedSupplier, from, to],
    apiOverride,
  );
  const lines = useApiData(
    (api) => loadInvoiceLines(api, focusedInvoice),
    [focusedInvoice],
    apiOverride,
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

  if (suppliers.error) {
    if (suppliers.error.code === "NOT_PERMITTED") {
      return <PermissionDenied subject="purchase lineage" />;
    }
    return (
      <p role="alert" className="text-sm text-destructive">
        Couldn&apos;t load the invoice graph. {suppliers.error.message}
      </p>
    );
  }

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
      <DataQualifications notices={[...new Set((suppliers.data ?? []).flatMap(s => s.notices ?? []))]} />
      {invoices.error || lines.error ? <p role="alert">{invoices.error?.message ?? lines.error?.message}</p> : null}
      <FlowCanvas
        graph={graph}
        onDrill={onDrill}
        ariaLabel="Invoices: suppliers, invoices, and line items"
        height={360}
      />
    </div>
  );
}


function loadSupplierInvoices(api: Api, reference: string | null, from: string, to: string) {
  if (!reference) return Promise.resolve(null);
  if (reference.startsWith("id:")) return api.getInvoiceGraphInvoicesById(reference.slice(3), from, to);
  return api.getInvoiceGraphInvoices(reference, from, to);
}

function loadInvoiceLines(api: Api, reference: string | null) {
  if (!reference) return Promise.resolve(null);
  if (reference.startsWith("id:")) return api.getInvoiceGraphLinesById(reference.slice(3));
  return api.getInvoiceGraphLines(reference);
}
