package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only view of one current invoice line carrying the full enrichment field set (stock code,
 * UOM, per-pack unit quantity, pack size, WET). Used by line-level reporting (unit-cost-per-UOM,
 * COGS-by-supplier/WET); CSV remains authoritative for quantity/unitCost/lineTotal.
 */
public record EnrichedInvoiceLine(
    String invoiceNumber,
    LocalDate invoiceDate,
    String productNameKey,
    String stockCode,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal lineTotal,
    String uom,
    BigDecimal unitQuantity,
    BigDecimal packSize,
    BigDecimal wetAmount) {}
