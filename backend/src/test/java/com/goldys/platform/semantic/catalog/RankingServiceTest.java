package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.TopSeller;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankingServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 1, 7);
  private static final TimeRange RANGE = new TimeRange(FROM, TO, Calendar.CALENDAR);

  private final ProductMetricsQuery products = mock(ProductMetricsQuery.class);
  private final LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
  private final RankingService service = new RankingService(products, labour);

  @Test
  void rejectsAnUnsupportedMetricDimensionPair() {
    assertThatThrownBy(
            () ->
                service.rank(
                    MetricId.SALES_GROSS,
                    Dimension.PRODUCT,
                    new TimeRange(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), Calendar.CALENDAR),
                    5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no ranked read");
  }

  @Test
  void rejectsANonPositiveLimit() {
    assertThatThrownBy(
            () -> service.rank(MetricId.PRODUCT_SALES_AMOUNT, Dimension.PRODUCT, RANGE, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit must be positive");
  }

  @Test
  void ranksProductsByAmountDelegatingToTopSellers() {
    when(products.topSellers(FROM, TO, 5))
        .thenReturn(
            List.of(
                new TopSeller("Steak", new BigDecimal("12"), new BigDecimal("540.00"), false),
                new TopSeller("Wine", new BigDecimal("30"), new BigDecimal("450.00"), true)));

    RankedListResult result =
        service.rank(MetricId.PRODUCT_SALES_AMOUNT, Dimension.PRODUCT, RANGE, 5);

    assertThat(result.metric()).isEqualTo(MetricId.PRODUCT_SALES_AMOUNT);
    assertThat(result.items())
        .containsExactly(
            new MetricRankedItem("Steak", new BigDecimal("540.00"), new BigDecimal("12"), false),
            new MetricRankedItem("Wine", new BigDecimal("450.00"), new BigDecimal("30"), true));
    assertThat(result.provenance().metric()).isEqualTo(MetricId.PRODUCT_SALES_AMOUNT);
    assertThat(result.provenance().range()).isEqualTo(RANGE);
    verify(products).topSellers(FROM, TO, 5);
  }

  @Test
  void ranksDepartmentsByCostAggregatingDailyLabour() {
    when(labour.dailyLabour(FROM, TO))
        .thenReturn(
            List.of(
                labour(FROM, "Kitchen", "90"),
                labour(FROM.plusDays(1), "Kitchen", "100"),
                labour(FROM, "Bar", "200")));

    RankedListResult result = service.rank(MetricId.LABOUR_COST, Dimension.DEPARTMENT, RANGE, 5);

    assertThat(result.metric()).isEqualTo(MetricId.LABOUR_COST);
    assertThat(result.items())
        .containsExactly(
            new MetricRankedItem("Bar", new BigDecimal("200"), null, false),
            new MetricRankedItem("Kitchen", new BigDecimal("190"), null, false));
    assertThat(result.provenance().metric()).isEqualTo(MetricId.LABOUR_COST);
  }

  @Test
  void ranksDepartmentsByActualHoursAndRespectsLimit() {
    when(labour.dailyLabour(FROM, TO))
        .thenReturn(
            List.of(
                hours(FROM, "Kitchen", "8", "7.5"),
                hours(FROM, "Bar", "4", "4"),
                hours(FROM, "Floor", "6", "6")));

    RankedListResult result =
        service.rank(MetricId.LABOUR_ACTUAL_HOURS, Dimension.DEPARTMENT, RANGE, 2);

    assertThat(result.metric()).isEqualTo(MetricId.LABOUR_ACTUAL_HOURS);
    assertThat(result.items())
        .extracting(MetricRankedItem::label)
        .containsExactly("Kitchen", "Floor");
  }

  private static LabourMetric labour(LocalDate date, String department, String actualCost) {
    return new LabourMetric(
        date,
        department,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal(actualCost),
        "deputy",
        false);
  }

  private static LabourMetric hours(
      LocalDate date, String department, String scheduledHours, String actualHours) {
    return new LabourMetric(
        date,
        department,
        new BigDecimal(scheduledHours),
        new BigDecimal(actualHours),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "deputy",
        false);
  }
}
