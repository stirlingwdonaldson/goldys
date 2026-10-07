package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DailySalesMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolvedDailySalesQueryTest {

  @Test
  void mapsNetAndGst() {
    ResolvedDailySalesRepository repo = mock(ResolvedDailySalesRepository.class);
    ResolvedDailySales row =
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13),
            new BigDecimal("27650.66"),
            new BigDecimal("25136.96"),
            new BigDecimal("2513.70"),
            "agreed",
            "agreed",
            false,
            java.time.Instant.EPOCH);
    when(repo.findByTradingDateBetweenOrderByTradingDateAsc(
            LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13)))
        .thenReturn(List.of(row));

    List<DailySalesMetric> result =
        new ResolvedDailySalesQuery(repo)
            .dailySales(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13));

    DailySalesMetric m = result.get(0);
    assertThat(m.grossSales()).isEqualByComparingTo("27650.66");
    assertThat(m.netSales()).isEqualByComparingTo("25136.96");
    assertThat(m.gst()).isEqualByComparingTo("2513.70");
  }
}
