import { fetchApi } from "./client";
import type { InvoiceGraphNode, LineGraphNode, SupplierGraphNode } from "./types";

/** Ranked supplier spend for the invoice graph (`GET /api/inventory/graph/suppliers`). */
export async function getInvoiceGraphSuppliers(
  from: string,
  to: string,
): Promise<SupplierGraphNode[]> {
  return fetchApi<SupplierGraphNode[]>(
    `/api/inventory/graph/suppliers?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

/** One supplier's invoices, newest first (`GET /api/inventory/graph/suppliers/{name}/invoices`). */
export async function getInvoiceGraphInvoices(
  supplier: string,
  from: string,
  to: string,
): Promise<InvoiceGraphNode[]> {
  return fetchApi<InvoiceGraphNode[]>(
    `/api/inventory/graph/suppliers/${encodeURIComponent(supplier)}/invoices?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

/** One invoice's line items, largest first (`GET /api/inventory/graph/invoices/{number}/lines`). */
export async function getInvoiceGraphLines(
  invoiceNumber: string,
): Promise<LineGraphNode[]> {
  return fetchApi<LineGraphNode[]>(
    `/api/inventory/graph/invoices/${encodeURIComponent(invoiceNumber)}/lines`,
  );
}
