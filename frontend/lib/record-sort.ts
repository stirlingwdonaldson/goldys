import type { SortingState } from "@tanstack/react-table";

export interface RecordSort { field: string; direction: "asc" | "desc" }
export interface RecordReadOptions { signal?: AbortSignal; sort?: RecordSort }
export const SORT_FIELDS = {
  invoices: ["supplierName", "invoiceNumber", "invoiceDate", "dueDate", "totalAmount", "purchaseNumber", "pdf"],
  sales: ["date", "totalSales", "gst", "net"],
  payments: ["tradingDate", "saleNumber", "paymentTypeName", "lspayPaymentMode", "reconciled", "registerName", "staffName", "amount", "tip", "surcharge", "tendered", "paymentCount"],
  deleted: ["tradingDate", "saleNumber", "orderType", "note", "staffName", "deletedByStaffName", "deletedRegisterName", "tableNumber", "totalIncTax", "totalExTax", "totalTax", "totalCost"],
  items: ["tradingDate", "saleNumber", "receiptLineId", "itemName", "productNumber", "sku", "categoryName", "orderType", "quantitySold", "soldPriceIncTax", "costIncTax", "amount"],
} as const;
export function readSort(params: URLSearchParams, fields: readonly string[], fallback: string, prefix = ""): RecordSort {
  const field = params.get(`${prefix}sort`);
  return { field: field && fields.includes(field) ? field : fallback, direction: params.get(`${prefix}direction`) === "asc" ? "asc" : "desc" };
}
export function tableSorting(sort: RecordSort): SortingState { return sort.field ? [{ id: sort.field, desc: sort.direction === "desc" }] : []; }
export function sortQuery(sort?: RecordSort): string { return sort?.field ? `&${new URLSearchParams({ sort: sort.field, direction: sort.direction })}` : ""; }
/** Demo ordering mirrors numeric/date/text semantics; nulls always last and stable identity breaks ties. */
export function sortRecords<T>(rows: T[], sort: RecordSort | undefined, value: (row: T, field: string) => unknown, identity: (row: T) => string): T[] {
  if (!sort?.field) return rows;
  const compare = (a: unknown, b: unknown): number => {
    if (a == null || b == null) return a == null ? b == null ? 0 : 1 : -1;
    const order = typeof a === "number" && typeof b === "number" ? a - b : String(a) < String(b) ? -1 : String(a) > String(b) ? 1 : 0;
    return sort.direction === "desc" ? -order : order;
  };
  return [...rows].sort((a, b) => compare(value(a, sort.field), value(b, sort.field)) || (identity(a) < identity(b) ? -1 : identity(a) > identity(b) ? 1 : 0));
}
