package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Source-qualified observation. No conversion, valuation or inference of a current balance. */
public record InventoryObservation(
    String kind,
    String sourceSystem,
    String sourceRecordRef,
    UUID logicalId,
    LocalDate date,
    String productName,
    String stockCode,
    BigDecimal quantity,
    String unit,
    String unitSize,
    BigDecimal unitCost,
    BigDecimal sourceValue,
    String area,
    Boolean stocktakePartial,
    Boolean stocktakeLocked,
    int parentSourceId,
    Instant recordedAt) {}
