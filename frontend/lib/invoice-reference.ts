/** Internal navigation references keep UUIDs distinct from vendor invoice numbers. */
export function invoiceReference(invoice: { invoiceId?: string | null; invoiceNumber: string }): string {
  return invoice.invoiceId ? `id:${invoice.invoiceId}` : `number:${invoice.invoiceNumber}`;
}
export function selectedInvoice(params: { get(name: string): string | null }): string | null {
  const id = params.get("invoiceId");
  const number = params.get("invoice");
  return id ? `id:${id}` : number ? `number:${number}` : null;
}
export function withSelectedInvoice(params: URLSearchParams, reference: string | null): URLSearchParams {
  const next = new URLSearchParams(params);
  next.delete("invoice");
  next.delete("invoiceId");
  if (reference) next.set(reference.startsWith("id:") ? "invoiceId" : "invoice", reference.replace(/^(id:|number:)/, ""));
  return next;
}
export function invoiceLabel(number: string | undefined, reference: string): string {
  return number ?? (reference.startsWith("id:") ? "" : reference.replace(/^number:/, ""));
}
