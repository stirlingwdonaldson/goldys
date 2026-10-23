package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DeletedSaleDay;
import com.goldys.platform.semantic.DeletedSaleMetricsQuery;
import com.goldys.platform.semantic.SaleItemMetricsQuery;
import com.goldys.platform.semantic.SaleItemMix;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SalesDetailCoverageTest {
  @Test
  void distinguishesZeroUnresolvedAndAbsentDatesIncludingIncompleteCategoryTotals() {
    LocalDate from = LocalDate.of(2026, 10, 1);
    LocalDate to = from.plusDays(2);
    SaleItemMetricsQuery saleItems = mock(SaleItemMetricsQuery.class);
    when(saleItems.dailyByCategory(from, to))
        .thenReturn(
            List.of(
                new SaleItemMix(from, "Food", BigDecimal.ZERO, BigDecimal.ZERO, false),
                new SaleItemMix(from.plusDays(1), "Food", null, null, true),
                new SaleItemMix(from.plusDays(1), "Drink", BigDecimal.ONE, BigDecimal.TEN, false)));
    DeletedSaleMetricsQuery deleted = mock(DeletedSaleMetricsQuery.class);
    when(deleted.dailyTotals(from, to))
        .thenReturn(
            List.of(
                new DeletedSaleDay(from, 0, BigDecimal.ZERO, BigDecimal.ZERO, false),
                new DeletedSaleDay(from.plusDays(1), 1, null, null, true)));
    for (MetricExecutor executor :
        List.of(
            new SaleItemMetricExecutor(saleItems, new MetricCatalog()),
            new DeletedSaleMetricExecutor(deleted, new MetricCatalog()))) {
      MetricId id =
          executor.ids().contains(MetricId.SALE_ITEMS_AMOUNT)
              ? MetricId.SALE_ITEMS_AMOUNT
              : MetricId.DELETED_SALES_AMOUNT;
      TimeSeriesResult result =
          (TimeSeriesResult)
              executor.evaluate(
                  new MetricQuery(
                      id,
                      new TimeRange(from, to, Calendar.CALENDAR),
                      TimeGrain.TOTAL,
                      Set.of(),
                      null));
      assertThat(result.provenance().coverage().observedDates()).containsExactly(from);
      assertThat(result.provenance().coverage().unresolvedDates())
          .containsExactly(from.plusDays(1));
      assertThat(result.provenance().coverage().absentDates()).containsExactly(to);
      assertThat(result.provenance().coverage().expectedTradingPopulationKnown()).isFalse();
      assertThat(result.notices()).anyMatch(notice -> notice.startsWith("Incomplete subtotal:"));
    }
  }
}
