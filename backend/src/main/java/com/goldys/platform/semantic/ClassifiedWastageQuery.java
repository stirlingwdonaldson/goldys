package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Item evidence and explicit mappings; quantities/costs are source observations, not verified loss.
 */
public interface ClassifiedWastageQuery {
  record Line(
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      LocalDate date,
      String name,
      BigDecimal quantity,
      BigDecimal sourceUnitCost,
      String unit,
      String unitSize,
      String reason,
      String supplier,
      Integer recipeId,
      Long mappingRevisionId,
      String mappingScope,
      String mappingReason,
      String salesNature,
      String stockBucket,
      String itemRole,
      String businessArea,
      String lifecycle) {}

  List<Line> lines(LocalDate from, LocalDate to, int offset, int limit);
}
