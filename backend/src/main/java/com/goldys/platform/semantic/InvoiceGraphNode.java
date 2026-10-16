package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One invoice header for the invoice graph. */
public record InvoiceGraphNode(
    String invoiceNumber,
    LocalDate invoiceDate,
    BigDecimal totalAmount,
    String purchaseNumber,
    String pdfFilename,
    java.util.UUID invoiceId,
    BigDecimal amountExTax) {
  public InvoiceGraphNode(
      String number, LocalDate date, BigDecimal amount, String purchase, String pdf) {
    this(number, date, amount, purchase, pdf, null, null);
  }
}
