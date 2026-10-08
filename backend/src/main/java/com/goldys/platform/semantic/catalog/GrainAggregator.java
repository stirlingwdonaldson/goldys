package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.MissingDataStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Buckets per-day values into DAY/WEEK/MONTH grains. A null or absent day is flagged missing, never
 * zeroed; a bucket with no resolved value is a null point carrying a {@link MissingDataStatus}
 * ({@code NOT_RECEIVED} when no source row exists for the day, {@code UNRESOLVED} when a source row
 * exists but its value is unresolved).
 */
final class GrainAggregator {
  private GrainAggregator() {}

  record Bucket(List<MetricPoint> points, List<LocalDate> missingDays) {}

  static Bucket sum(
      Map<LocalDate, BigDecimal> byDay, LocalDate from, LocalDate to, TimeGrain grain) {
    TreeMap<LocalDate, Sum> buckets = new TreeMap<>();
    List<LocalDate> missingDays = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      LocalDate key = bucketStart(d, grain);
      Sum s = buckets.computeIfAbsent(key, k -> new Sum());
      if (!byDay.containsKey(d)) {
        // No source row for this day at all.
        missingDays.add(d);
      } else {
        BigDecimal v = byDay.get(d);
        if (v == null) {
          // A source row existed but its value is unresolved.
          s.unresolved = true;
          missingDays.add(d);
        } else {
          s.anyValue = true;
          s.total = s.total.add(v);
        }
      }
    }
    List<MetricPoint> points = new ArrayList<>();
    for (Map.Entry<LocalDate, Sum> e : buckets.entrySet()) {
      Sum s = e.getValue();
      if (s.anyValue) {
        points.add(new MetricPoint(e.getKey(), s.total));
      } else if (s.unresolved) {
        points.add(new MetricPoint(e.getKey(), null, MissingDataStatus.UNRESOLVED));
      } else {
        points.add(new MetricPoint(e.getKey(), null, MissingDataStatus.NOT_RECEIVED));
      }
    }
    return new Bucket(List.copyOf(points), List.copyOf(missingDays));
  }

  static LocalDate bucketStart(LocalDate d, TimeGrain grain) {
    return switch (grain) {
      case DAY -> d;
      case WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTH -> d.withDayOfMonth(1);
    };
  }

  private static final class Sum {
    boolean anyValue;
    boolean unresolved;
    BigDecimal total = BigDecimal.ZERO;
  }
}
