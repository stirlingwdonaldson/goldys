package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class MetricCoverageTest {
  @Test
  void distinguishesZeroUnresolvedAndAbsentWithoutClaimingCompleteness() {
    LocalDate day = LocalDate.of(2026, 10, 12);
    var values = new HashMap<LocalDate, BigDecimal>();
    values.put(day, new BigDecimal("100"));
    values.put(day.plusDays(1), BigDecimal.ZERO);
    values.put(day.plusDays(2), null);
    var bucket = GrainAggregator.sum(values, day, day.plusDays(3), TimeGrain.TOTAL);
    assertThat(bucket.points().getFirst().value()).isEqualByComparingTo("100");
    assertThat(bucket.coverage().observedDates()).containsExactly(day, day.plusDays(1));
    assertThat(bucket.coverage().unresolvedDates()).containsExactly(day.plusDays(2));
    assertThat(bucket.coverage().absentDates()).containsExactly(day.plusDays(3));
    assertThat(bucket.coverage().expectedTradingPopulationKnown()).isFalse();
    assertThat(bucket.coverage().notices().getFirst())
        .contains("Incomplete subtotal", "1 unresolved", "1 day(s) without");
    var provenance =
        new MetricProvenance(
                MetricId.SALES_NET,
                "1",
                new TimeRange(day, day.plusDays(3), Calendar.CALENDAR),
                TimeGrain.TOTAL,
                "sales",
                null,
                bucket.missingDays(),
                "1")
            .withCoverage(bucket.coverage());
    assertThat(provenance.coverage()).isEqualTo(bucket.coverage());
  }
}
