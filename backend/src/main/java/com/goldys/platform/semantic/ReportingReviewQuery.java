package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Bounded owner review over resolved observations; exposure is never a certified loss. */
public interface ReportingReviewQuery {
  enum Domain {
    SALES,
    SALES_BASIS,
    WASTAGE
  }

  record Item(
      String scope,
      String sourceSystem,
      String entityKey,
      String names,
      Integer recipeId,
      LocalDate firstDate,
      LocalDate lastDate,
      long observations,
      BigDecimal knownExposure,
      BigDecimal missingBasisGross,
      long missingBasis,
      long unresolved,
      String policies,
      String holds,
      String evidence) {}

  record WasteCoverage(
      long lines,
      long classifiedLines,
      long reasonLines,
      long missingCost,
      long missingDate,
      BigDecimal knownValue,
      BigDecimal unknownValue) {}

  List<Item> items(Domain domain, LocalDate from, LocalDate to, int offset, int limit);

  WasteCoverage wasteCoverage(LocalDate from, LocalDate to);
}
