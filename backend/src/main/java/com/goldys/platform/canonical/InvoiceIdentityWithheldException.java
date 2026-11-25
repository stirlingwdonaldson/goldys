package com.goldys.platform.canonical;

/** An invoice-local identity ambiguity; unrelated invoices may still be processed. */
public class InvoiceIdentityWithheldException extends IllegalArgumentException {
  public InvoiceIdentityWithheldException() {
    super(
        "Invoice identity withheld: placeholder number or ambiguous supplier/line parentage requires review");
  }
}
