package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A normalized wastage-line observation from one source. {@code lineId} is the source's globally
 * unique {@code wastageRecordDetailId}; {@code wastageRecordId} anchors it for consistency with the
 * other parent-scoped line identities. Wastage has no stock code in the source — product identity is
 * {@code productNameKey} from {@code stockDesc}.
 */
public record WastageInput(
    String sourceSystem,
    int wastageRecordId,
    int lineId,
    LocalDate wastageDate,
    String productNameKey,
    BigDecimal quantity,
    BigDecimal cost,
    String reason,
    String supplierName,
    String unitOfMeasurementName,
    String unitSize,
    Integer recipeId,
    UUID rawRecordId) {}
