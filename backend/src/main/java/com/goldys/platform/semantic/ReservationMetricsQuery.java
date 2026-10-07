package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business reads over resolved reservations. Implementations read the resolved projection;
 * consumers (dashboards, reporting tools, exports) never derive these figures from canonical source
 * rows.
 */
public interface ReservationMetricsQuery {

  /** Resolved daily covers for the inclusive range, ascending by date (summed across periods). */
  List<CoversMetric> dailyCovers(LocalDate from, LocalDate to);

  /** The resolved summary for a single date (both service periods combined). */
  Optional<ReservationSummary> summary(LocalDate date);

  /** Resolved per-day summaries for the inclusive range, ascending by date. */
  List<ReservationSummary> dailySummaries(LocalDate from, LocalDate to);

  /** Resolved covers broken down by service period, ascending by date then period. */
  List<ServicePeriodCovers> coversByServicePeriod(LocalDate from, LocalDate to);

  /** Compares total covers between two periods. */
  PeriodComparison comparePeriods(Period a, Period b);

  /** No-show rate over the inclusive range, or empty when there are no bookings. */
  Optional<BigDecimal> noShowRate(LocalDate from, LocalDate to);

  /** Attended ÷ bookings over the inclusive range, or empty when there are no bookings. */
  Optional<BigDecimal> bookingToCoverConversion(LocalDate from, LocalDate to);
}
