package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrainAggregatorTest {

  private static final LocalDate MON = LocalDate.of(2026, 9, 7); // Monday

  @Test
  void sumsResolvedDaysAndFlagsMissingOnes() {
    // HashMap (not Map.of) because a null value marks an unresolved day.
    Map<LocalDate, BigDecimal> byDay = new HashMap<>();
    byDay.put(MON, new BigDecimal("10"));
    byDay.put(MON.plusDays(1), new BigDecimal("20"));
    byDay.put(MON.plusDays(2), null);

    GrainAggregator.Bucket b = GrainAggregator.sum(byDay, MON, MON.plusDays(2), TimeGrain.DAY);

    assertThat(b.points()).hasSize(3);
    assertThat(b.points().get(0).value()).isEqualByComparingTo("10");
    assertThat(b.points().get(1).value()).isEqualByComparingTo("20");
    assertThat(b.points().get(2).value()).isNull(); // day is unresolved -> null, not zero
    assertThat(b.missingDays()).containsExactly(MON.plusDays(2));
  }

  @Test
  void bucketsToIsoMondayWeek() {
    Map<LocalDate, BigDecimal> byDay =
        Map.of(MON, new BigDecimal("10"), MON.plusDays(1), new BigDecimal("20"));

    GrainAggregator.Bucket b = GrainAggregator.sum(byDay, MON, MON.plusDays(1), TimeGrain.WEEK);

    assertThat(b.points()).hasSize(1);
    assertThat(b.points().get(0).bucketStart()).isEqualTo(MON); // ISO Monday
    assertThat(b.points().get(0).value()).isEqualByComparingTo("30");
  }

  @Test
  void returnsNullPointWhenNothingResolvedInABucket() {
    Map<LocalDate, BigDecimal> byDay = new HashMap<>();
    byDay.put(MON, null);

    GrainAggregator.Bucket b = GrainAggregator.sum(byDay, MON, MON, TimeGrain.DAY);
    assertThat(b.points().get(0).value()).isNull();
  }
}
