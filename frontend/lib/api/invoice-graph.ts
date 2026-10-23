import { fetchApi } from "./client";
import type { InvoiceGraphNode, LineGraphNode, SupplierGraphNode } from "./types";

/** Ranked supplier spend for the invoice graph (`GET /api/inventory/graph/suppliers`). */
export async function getInvoiceGraphSuppliers(
  from: string,
  to: string,
options?: { signal?: AbortSignal }): Promise<SupplierGraphNode[]> {
  return fetchApi<SupplierGraphNode[]>(
    `/api/inventory/graph/suppliers?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,{ signal: options?.signal }
  );
}

/** One supplier's invoices, newest first (`GET /api/inventory/graph/suppliers/{name}/invoices`). */
export async function getInvoiceGraphInvoices(
  supplier: string,
  from: string,
  to: string,
options?: { signal?: AbortSignal }): Promise<InvoiceGraphNode[]> {
  return fetchApi<InvoiceGraphNode[]>(
    `/api/inventory/graph/suppliers/${encodeURIComponent(supplier)}/invoices?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,{ signal: options?.signal }
  );
}

/** One invoice's line items, largest first (`GET /api/inventory/graph/invoices/{number}/lines`). */
export async function getInvoiceGraphLines(
  invoiceNumber: string,
options?: { signal?: AbortSignal }): Promise<LineGraphNode[]> {
  return fetchApi<LineGraphNode[]>(
    `/api/inventory/graph/invoices/${encodeURIComponent(invoiceNumber)}/lines`,{ signal: options?.signal }
  );
}

export async function getInvoiceGraphInvoicesById(supplierId: string, from: string, to: string, options?: { signal?: AbortSignal }): Promise<InvoiceGraphNode[]> {
  return fetchApi<InvoiceGraphNode[]>(`/api/inventory/graph/suppliers/by-id/${encodeURIComponent(supplierId)}/invoices?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, { signal: options?.signal });
}
export async function getInvoiceGraphLinesById(invoiceId: string, options?: { signal?: AbortSignal }): Promise<LineGraphNode[]> {
  return fetchApi<LineGraphNode[]>(`/api/inventory/graph/invoices/by-id/${encodeURIComponent(invoiceId)}/lines`, { signal: options?.signal });
}
