package com.goldys.platform.application;

import com.goldys.platform.semantic.WeeklyInventoryQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The same authorised report snapshot is rendered on screen and exported to Excel. */
public record WeeklyReport(
    LocalDate from, LocalDate to, Instant generatedAt, String timezone, List<Week> weeks) {
  public record Week(
      LocalDate start,
      LocalDate end,
      List<LocalDate> dates,
      List<Row> rows,
      List<WeeklyReportTargets.Target> targets,
      Wastage wastage,
      List<WeeklyInventoryQuery.StockCount> stockCounts,
      Map<String, Assessment> assessments) {}

  public enum AssessmentOutcome {
    MEETS_TARGET,
    BELOW_TARGET,
    ABOVE_LIMIT,
    OUTSIDE_RANGE,
    WITHHELD,
    UNAVAILABLE,
    SOURCE_QUALIFIED,
    TARGET
  }

  public enum AssessmentReason {
    INCOMPLETE_EVIDENCE,
    NO_EFFECTIVE_TARGET,
    BASIS_MISMATCH,
    COMPARISON_UNAVAILABLE
  }

  /** Eligibility is separate from numeric availability and never certifies source completeness. */
  public record Assessment(AssessmentOutcome outcome, AssessmentReason reason) {}

  public record WasteReason(
      String reason,
      List<String> originalReasons,
      long lines,
      long valuedLines,
      BigDecimal value,
      BigDecimal shareOfValue) {}

  public record Wastage(
      long lines,
      long valuedLines,
      BigDecimal value,
      BigDecimal reasonCoverageLines,
      BigDecimal reasonCoverageValue,
      List<WasteReason> reasons) {}

  public record Row(
      String id,
      String section,
      String label,
      String unit,
      List<BigDecimal> days,
      BigDecimal total,
      String status,
      List<String> notes) {}
}
