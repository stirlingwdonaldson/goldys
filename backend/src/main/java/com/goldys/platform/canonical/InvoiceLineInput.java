package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized invoice line-item observation from one source.
 *
 * <p>{@code invoiceLogicalId} is the parent invoice's stable logical id, so a line is a child of an
 * invoice entity regardless of which invoice version is current.
 */
public record InvoiceLineInput(
    String sourceSystem,
    String sourceRecordRef,
    String invoiceNumber,
    UUID invoiceLogicalId,
    LocalDate invoiceDate,
    String productNameKey,
    String stockCode,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal lineTotal,
    String category,
    String uom,
    BigDecimal unitQuantity,
    BigDecimal packSize,
    BigDecimal wetAmount,
    UUID rawRecordId,
    String origin) {
  InvoiceLineInput forInvoice(InvoiceIdentity invoice) {
    return new InvoiceLineInput(
        sourceSystem,
        invoice.sourceRecordRef() + ":" + sourceRecordRef,
        invoiceNumber,
        invoice.logicalEntityId(),
        invoiceDate,
        productNameKey,
        stockCode,
        quantity,
        unitCost,
        lineTotal,
        category,
        uom,
        unitQuantity,
        packSize,
        wetAmount,
        rawRecordId,
        origin);
  }
}
