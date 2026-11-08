package com.goldys.platform.canonical;

/** An unconfirmed CSV revision that must leave the invoice's header and lines unchanged. */
public class InvoiceRevisionWithheldException extends IllegalArgumentException {
  public InvoiceRevisionWithheldException() {
    super(
        "Invoice export revision withheld: complete-versus-partial semantics require confirmation");
  }
}
