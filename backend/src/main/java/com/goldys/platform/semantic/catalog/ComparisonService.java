package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Centralizes period-comparison semantics: derive the reference range for a comparison. */
@Service
public class ComparisonService {

  /** The reference range for {@code query.comparison()}, or null when there is no comparison. */
  public TimeRange referenceRange(MetricQuery query) {
    if (query.comparison() == null) {
      return null;
    }
    TimeRange r = query.range();
    long days = r.to().toEpochDay() - r.from().toEpochDay() + 1;
    return switch (query.comparison()) {
      case PREVIOUS_DAY -> new TimeRange(r.from().minusDays(1), r.to().minusDays(1), r.calendar());
      case PREVIOUS_WEEK, SAME_WEEKDAY_LAST_WEEK ->
          new TimeRange(r.from().minusDays(7), r.to().minusDays(7), r.calendar());
      case SAME_PERIOD_LAST_YEAR ->
          new TimeRange(r.from().minusYears(1), r.to().minusYears(1), r.calendar());
      case ROLLING_4_WEEKS -> new TimeRange(r.from().minusDays(28), r.to(), r.calendar());
      case ROLLING_12_WEEKS -> new TimeRange(r.from().minusDays(84), r.to(), r.calendar());
      case BUDGET, FORECAST ->
          throw new UnsupportedOperationException("no data source yet: " + query.comparison());
    };
  }

  public record ComparisonResult(
      MetricResult current, MetricResult reference, BigDecimal deltaPercent) {}

  static BigDecimal deltaPercent(BigDecimal current, BigDecimal reference) {
    if (reference == null || reference.signum() == 0) {
      return null;
    }
    return current.subtract(reference).divide(reference, 4, RoundingMode.HALF_UP);
  }
}
