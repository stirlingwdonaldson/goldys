package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.Period;
import com.goldys.platform.semantic.PeriodComparison;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReservationSummary;
import com.goldys.platform.semantic.ServicePeriodCovers;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** {@link ReservationMetricsQuery} backed by the resolved reservation projection. */
@Service
public class ResolvedReservationQuery implements ReservationMetricsQuery {
  private static final int SCALE = 4;

  private final ResolvedReservationDayRepository repository;

  public ResolvedReservationQuery(ResolvedReservationDayRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<CoversMetric> dailyCovers(LocalDate from, LocalDate to) {
    Map<LocalDate, Total> byDate = new LinkedHashMap<>();
    for (ResolvedReservationDay r : rows(from, to)) {
      Total t = byDate.computeIfAbsent(r.tradingDate(), k -> new Total(r.authoritativeSource()));
      t.covers += r.covers();
      t.hasConflict |= r.hasConflict();
    }
    return byDate.entrySet().stream()
        .map(
            e ->
                new CoversMetric(
                    e.getKey(), e.getValue().covers, e.getValue().source, e.getValue().hasConflict))
        .toList();
  }

  @Override
  public Optional<ReservationSummary> summary(LocalDate date) {
    List<ResolvedReservationDay> rows = rows(date, date);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    long bookings = 0, attended = 0, covers = 0, cancelled = 0, noShows = 0, walkIns = 0;
    for (ResolvedReservationDay r : rows) {
      bookings += r.bookings();
      attended += r.attended();
      covers += r.covers();
      cancelled += r.cancelled();
      noShows += r.noShows();
      walkIns += r.walkIns();
    }
    return Optional.of(
        new ReservationSummary(
            date,
            bookings,
            attended,
            covers,
            cancelled,
            noShows,
            walkIns,
            ratio(covers, attended),
            ratio(noShows, bookings),
            ratio(attended, bookings)));
  }

  @Override
  public List<ServicePeriodCovers> coversByServicePeriod(LocalDate from, LocalDate to) {
    // Order periods chronologically (LUNCH before DINNER), not lexically (DINNER < LUNCH).
    return rows(from, to).stream()
        .sorted(
            Comparator.comparing(ResolvedReservationDay::tradingDate)
                .thenComparingInt(r -> periodRank(r.servicePeriod())))
        .map(r -> new ServicePeriodCovers(r.tradingDate(), r.servicePeriod(), r.covers()))
        .toList();
  }

  private static int periodRank(String period) {
    return "LUNCH".equals(period) ? 0 : 1;
  }

  @Override
  public PeriodComparison comparePeriods(Period a, Period b) {
    long coversA = sumCovers(a.from(), a.to());
    long coversB = sumCovers(b.from(), b.to());
    return new PeriodComparison(coversA, coversB, ratioDelta(coversB - coversA, coversA));
  }

  @Override
  public Optional<BigDecimal> noShowRate(LocalDate from, LocalDate to) {
    long bookings = 0, noShows = 0;
    for (ResolvedReservationDay r : rows(from, to)) {
      bookings += r.bookings();
      noShows += r.noShows();
    }
    return Optional.ofNullable(ratio(noShows, bookings));
  }

  @Override
  public Optional<BigDecimal> bookingToCoverConversion(LocalDate from, LocalDate to) {
    long bookings = 0, attended = 0;
    for (ResolvedReservationDay r : rows(from, to)) {
      bookings += r.bookings();
      attended += r.attended();
    }
    return Optional.ofNullable(ratio(attended, bookings));
  }

  private long sumCovers(LocalDate from, LocalDate to) {
    long total = 0;
    for (ResolvedReservationDay r : rows(from, to)) {
      total += r.covers();
    }
    return total;
  }

  private List<ResolvedReservationDay> rows(LocalDate from, LocalDate to) {
    return repository.findByTradingDateBetweenOrderByTradingDateAscServicePeriodAsc(from, to);
  }

  private static BigDecimal ratio(long numerator, long denominator) {
    if (denominator == 0) {
      return null;
    }
    return BigDecimal.valueOf(numerator)
        .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal ratioDelta(long delta, long base) {
    if (base == 0) {
      return null;
    }
    return BigDecimal.valueOf(delta).divide(BigDecimal.valueOf(base), SCALE, RoundingMode.HALF_UP);
  }

  private static final class Total {
    long covers;
    boolean hasConflict;
    String source;

    Total(String source) {
      this.source = source;
    }
  }
}
