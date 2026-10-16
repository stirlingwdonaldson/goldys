import { describe, expect, it } from "vitest";
import { invoiceReference, selectedInvoice, withSelectedInvoice, invoiceLabel } from "./invoice-reference";

describe("invoice navigation references", () => {
  it("keeps ID-looking vendor numbers on the legacy number route", () => {
    const number = "id:10000000-0000-0000-0000-000000000001";
    const reference = invoiceReference({invoiceNumber:number});
    expect(reference).toBe("number:"+number);
    const query = withSelectedInvoice(new URLSearchParams(), reference);
    expect(query.get("invoice")).toBe(number);
    expect(query.has("invoiceId")).toBe(false);
    expect(selectedInvoice(query)).toBe(reference);
    expect(invoiceLabel(undefined,reference)).toBe(number);
  });
  it("switches between qualified selection and a legacy link without retaining both", () => {
    const query = withSelectedInvoice(new URLSearchParams("invoice=OLD&from=2026-10-01"), "id:new");
    expect(query.get("invoiceId")).toBe("new");
    expect(query.has("invoice")).toBe(false);
    expect(query.get("from")).toBe("2026-10-01");
    expect(selectedInvoice(query)).toBe("id:new");
    expect(withSelectedInvoice(query,null).has("invoiceId")).toBe(false);
  });
});
