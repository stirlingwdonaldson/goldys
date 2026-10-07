package com.goldys.platform.semantic.catalog;

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
 * zeroed; a bucket with no resolved value is a null point.
 */
final class GrainAggregator {
  private GrainAggregator() {}

  record Bucket(List<MetricPoint> points, List<LocalDate> missingDays) {}

  static Bucket sum(
      Map<LocalDate, BigDecimal> byDay, LocalDate from, LocalDate to, TimeGrain grain) {
    TreeMap<LocalDate, Sum> buckets = new TreeMap<>();
    List<LocalDate> missingDays = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      BigDecimal v = byDay.get(d);
      LocalDate key = bucketStart(d, grain);
      Sum s = buckets.computeIfAbsent(key, k -> new Sum());
      if (v == null) {
        missingDays.add(d);
      } else {
        s.anyValue = true;
        s.total = s.total.add(v);
      }
    }
    List<MetricPoint> points = new ArrayList<>();
    for (Map.Entry<LocalDate, Sum> e : buckets.entrySet()) {
      Sum s = e.getValue();
      points.add(new MetricPoint(e.getKey(), s.anyValue ? s.total : null));
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
    BigDecimal total = BigDecimal.ZERO;
  }
}
