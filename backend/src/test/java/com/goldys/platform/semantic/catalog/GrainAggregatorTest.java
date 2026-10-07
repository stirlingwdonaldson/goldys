package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.MissingDataStatus;
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

  @Test
  void marksAPresentButNullDayUnresolvedAndAnAbsentDayNotReceived() {
    Map<LocalDate, BigDecimal> byDay = new HashMap<>();
    byDay.put(MON, null); // a source row exists but its value is unresolved
    // MON.plusDays(1) is absent: no source row at all

    GrainAggregator.Bucket b = GrainAggregator.sum(byDay, MON, MON.plusDays(1), TimeGrain.DAY);

    assertThat(b.points()).hasSize(2);
    assertThat(b.points().get(0).value()).isNull();
    assertThat(b.points().get(0).status()).isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(b.points().get(1).value()).isNull();
    assertThat(b.points().get(1).status()).isEqualTo(MissingDataStatus.NOT_RECEIVED);
    assertThat(b.missingDays()).containsExactly(MON, MON.plusDays(1));
  }

  @Test
  void marksATrueZeroAsZeroAndANormalValueAsAbsent() {
    Map<LocalDate, BigDecimal> byDay =
        Map.of(MON, BigDecimal.ZERO, MON.plusDays(1), new BigDecimal("10"));

    GrainAggregator.Bucket b = GrainAggregator.sum(byDay, MON, MON.plusDays(1), TimeGrain.DAY);

    assertThat(b.points().get(0).value()).isEqualByComparingTo("0");
    assertThat(b.points().get(0).status()).isEqualTo(MissingDataStatus.ZERO);
    assertThat(b.points().get(1).value()).isEqualByComparingTo("10");
    assertThat(b.points().get(1).status()).isNull();
  }
}
