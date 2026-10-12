package com.goldys.platform.forecasting;

import com.goldys.platform.semantic.catalog.MetricPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges the semantic layer's {@link MetricPoint} rows onto the daily date axis the TimesFM model
 * expects, and builds the {@link TimesFmRequest} payload. Pure and stateless: no Spring wiring, no
 * I/O.
 *
 * <p>The alignment rules are deliberate:
 *
 * <ul>
 *   <li>One value per axis date — {@code null} where no point exists. A missing day is a gap, never
 *       an implicit zero; the model must be told the difference.
 *   <li>When two points share a date the first wins ({@link Map#putIfAbsent}), so a duplicate row
 *       from the semantic layer cannot silently overwrite an earlier value.
 *   <li>Points on dates outside the axis are ignored rather than shifting the axis.
 * </ul>
 */
public final class ForecastSeriesAssembler {

  private ForecastSeriesAssembler() {}

  /** Every calendar date from {@code from} to {@code to}, inclusive, in ascending order. */
  public static List<LocalDate> axis(LocalDate from, LocalDate to) {
    List<LocalDate> dates = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      dates.add(date);
    }
    return dates;
  }

  /**
   * Returns one value per axis date, aligned by index: the point's {@code value} where a point
   * exists and {@code null} otherwise. Points whose date falls outside the axis are ignored, and
   * when several points share a date the first one in {@code points} wins.
   */
  public static List<BigDecimal> align(List<LocalDate> axis, List<MetricPoint> points) {
    Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
    for (MetricPoint point : points) {
      byDate.putIfAbsent(point.bucketStart(), point.value());
    }
    List<BigDecimal> aligned = new ArrayList<>(axis.size());
    for (LocalDate date : axis) {
      aligned.add(byDate.get(date));
    }
    return aligned;
  }

  /**
   * Builds the model payload: the target aligned to {@code axis}, each covariate aligned the same
   * way, and {@code horizonDays}/{@code quantiles} passed through unchanged.
   */
  public static TimesFmRequest request(
      List<LocalDate> axis,
      List<MetricPoint> target,
      List<CovariateSeries> covariates,
      int horizonDays,
      List<String> quantiles) {
    List<TimesFmRequest.Covariate> alignedCovariates = new ArrayList<>(covariates.size());
    for (CovariateSeries covariate : covariates) {
      alignedCovariates.add(
          new TimesFmRequest.Covariate(covariate.name(), align(axis, covariate.points())));
    }
    return new TimesFmRequest(axis, align(axis, target), alignedCovariates, horizonDays, quantiles);
  }
}
