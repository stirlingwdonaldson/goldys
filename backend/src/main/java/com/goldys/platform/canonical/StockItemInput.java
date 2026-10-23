package com.goldys.platform.canonical;

import java.util.UUID;

/** A supplier-scoped stock-item observation. */
public record StockItemInput(
    String sourceSystem,
    String sourceRecordRef,
    UUID supplierLogicalId,
    String stockCode,
    String name,
    UUID sourceProductId,
    UUID rawRecordId) {}
