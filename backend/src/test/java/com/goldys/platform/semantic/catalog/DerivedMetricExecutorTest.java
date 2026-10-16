package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.FoodBevSplit;
import com.goldys.platform.semantic.InventoryMetric;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.MissingDataStatus;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductRanking;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReservationSummary;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.semantic.TopSeller;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DerivedMetricExecutorTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  private final ReservationMetricsQuery reservations = mock(ReservationMetricsQuery.class);
  private final SalesMetricsQuery sales = mock(SalesMetricsQuery.class);
  private final LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
  private final InventoryMetricsQuery inventory = mock(InventoryMetricsQuery.class);
  private final ProductMetricsQuery product = mock(ProductMetricsQuery.class);
  private final ProductCategoryMetricsQuery productCategory =
      mock(ProductCategoryMetricsQuery.class);

  private DerivedMetricExecutor executor() {
    MetricCatalog catalog = new MetricCatalog();
    return new DerivedMetricExecutor(
        new ReservationMetricExecutor(reservations, catalog),
        new SalesMetricExecutor(sales, catalog),
        new LabourMetricExecutor(labour, catalog),
        new InventoryMetricExecutor(inventory, catalog),
        new ProductMetricExecutor(product, catalog),
        new FoodBevMetricExecutor(productCategory, catalog),
        labour,
        product,
        catalog);
  }

  @Test
  void exposesAllTwelveDerivedIds() {
    assertThat(executor().ids())
        .containsExactlyInAnyOrder(
            MetricId.RESERVATIONS_NO_SHOW_RATE,
            MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION,
            MetricId.RESERVATIONS_AVG_PARTY_SIZE,
            MetricId.SALES_AVERAGE_SPEND_PER_COVER,
            MetricId.LABOUR_HOURS_PER_COVER,
            MetricId.LABOUR_COST_PER_COVER,
            MetricId.LABOUR_HOURS_VARIANCE,
            MetricId.LABOUR_FOH_PERCENT,
            MetricId.LABOUR_BOH_PERCENT,
            MetricId.INVENTORY_FOOD_COST_PERCENT,
            MetricId.PRODUCT_TOP_SELLERS,
            MetricId.SALES_FOOD_SHARE);
  }

  @Test
  void computesNoShowRateAsNoShowsOverBookings() {
    when(reservations.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(summary(SEP_13, 100, 90, 180, 10)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.RESERVATIONS_NO_SHOW_RATE,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("0.1000");
    assertThat(ts.notices()).isEmpty();
  }

  @Test
  void noShowRateIsNullWithNoticeWhenBookingsAreZero() {
    when(reservations.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(summary(SEP_13, 0, 0, 0, 10)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.RESERVATIONS_NO_SHOW_RATE,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isNull();
    assertThat(ts.series().get(0).points().get(0).status()).isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(ts.notices()).isNotEmpty();
  }

  @Test
  void bookingToCoverConversionDividesAttendedByBookings() {
    when(reservations.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(summary(SEP_13, 100, 90, 180, 10)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("0.9000");
  }

  @Test
  void avgPartySizeDividesCoversByAttended() {
    when(reservations.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(summary(SEP_13, 100, 90, 180, 10)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.RESERVATIONS_AVG_PARTY_SIZE,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("2.0000");
  }

  @Test
  void sumsPerBucketAtWeekGrain() {
    LocalDate monday = SEP_13.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate tuesday = monday.plusDays(1);
    when(reservations.dailySummaries(monday, tuesday))
        .thenReturn(List.of(summary(monday, 60, 50, 120, 10), summary(tuesday, 40, 40, 80, 0)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.RESERVATIONS_NO_SHOW_RATE,
                        new TimeRange(monday, tuesday, Calendar.CALENDAR),
                        TimeGrain.WEEK,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points()).hasSize(1);
    assertThat(ts.series().get(0).points().get(0).bucketStart()).isEqualTo(monday);
    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("0.1000");
  }

  @Test
  void averageSpendPerCoverDividesGrossByCovers() {
    when(reservations.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(summary(SEP_13, 100, 90, 250, 10)));
    when(sales.dailySales(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    SEP_13, new BigDecimal("5000.00"), null, null, "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.SALES_AVERAGE_SPEND_PER_COVER,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("20.0000");
  }

  @Test
  void averageSpendPerCoverIsNullWhenCoversAreZero() {
    when(reservations.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(summary(SEP_13, 100, 90, 0, 10)));
    when(sales.dailySales(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    SEP_13, new BigDecimal("5000.00"), null, null, "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.SALES_AVERAGE_SPEND_PER_COVER,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isNull();
    assertThat(ts.notices()).isNotEmpty();
  }

  @Test
  void foodCostPercentIsUnavailableWithoutValuation() {
    when(sales.dailySales(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    SEP_13, new BigDecimal("5000.00"), null, null, "agreed", false)));
    when(inventory.dailyInventory(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new InventoryMetric(
                    SEP_13, new BigDecimal("1000.00"), null, null, "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.INVENTORY_FOOD_COST_PERCENT,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isNull();
  }

  @Test
  void foodShareIsFoodOverNet() {
    when(productCategory.dailyFoodBev(SEP_13, SEP_13))
        .thenReturn(
            List.of(new FoodBevSplit(SEP_13, new BigDecimal("300.00"), new BigDecimal("200.00"))));
    when(sales.dailySales(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    SEP_13,
                    new BigDecimal("500.00"),
                    new BigDecimal("450.00"),
                    null,
                    "agreed",
                    false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.SALES_FOOD_SHARE,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("0.6667");
    assertThat(ts.notices()).isEmpty();
  }

  @Test
  void foodShareIsNullWhenNetIsZero() {
    when(productCategory.dailyFoodBev(SEP_13, SEP_13))
        .thenReturn(
            List.of(new FoodBevSplit(SEP_13, new BigDecimal("300.00"), new BigDecimal("200.00"))));
    when(sales.dailySales(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    SEP_13, new BigDecimal("500.00"), BigDecimal.ZERO, null, "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.SALES_FOOD_SHARE,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isNull();
    assertThat(ts.series().get(0).points().get(0).status()).isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(ts.notices()).isNotEmpty();
  }

  @Test
  void fohPercentDividesFohCostByGross() {
    when(sales.dailySales(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    SEP_13, new BigDecimal("5000.00"), null, null, "agreed", false)));
    when(labour.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13, "FOH", null, null, null, new BigDecimal("1500.00"), "agreed", false),
                new LabourMetric(
                    SEP_13, "BOH", null, null, null, new BigDecimal("500.00"), "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.LABOUR_FOH_PERCENT,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("0.3000");
  }

  @Test
  void bohPercentDividesBohCostByGross() {
    when(sales.dailySales(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    SEP_13, new BigDecimal("5000.00"), null, null, "agreed", false)));
    when(labour.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13, "FOH", null, null, null, new BigDecimal("1500.00"), "agreed", false),
                new LabourMetric(
                    SEP_13, "BOH", null, null, null, new BigDecimal("500.00"), "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.LABOUR_BOH_PERCENT,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("0.1000");
  }

  @Test
  void hoursVarianceSubtractsActualFromScheduled() {
    when(labour.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13,
                    "FOH",
                    new BigDecimal("80.00"),
                    new BigDecimal("90.00"),
                    null,
                    null,
                    "agreed",
                    false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.LABOUR_HOURS_VARIANCE,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("-10.00");
  }

  @Test
  void hoursVarianceIsUnresolvedWhenScheduledHoursAreMissing() {
    when(labour.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13, "FOH", null, new BigDecimal("90.00"), null, null, "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.LABOUR_HOURS_VARIANCE,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isNull();
    assertThat(ts.series().get(0).points().get(0).status()).isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(ts.notices()).isNotEmpty();
  }

  @Test
  void hoursPerCoverDividesActualHoursByCovers() {
    when(reservations.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(summary(SEP_13, 100, 90, 180, 10)));
    when(labour.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13, "FOH", null, new BigDecimal("90.00"), null, null, "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.LABOUR_HOURS_PER_COVER,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("0.5000");
  }

  @Test
  void costPerCoverDividesLabourCostByCovers() {
    when(reservations.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(summary(SEP_13, 100, 90, 180, 10)));
    when(labour.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13, "FOH", null, null, null, new BigDecimal("1500.00"), "agreed", false)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.LABOUR_COST_PER_COVER,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("8.3333");
  }

  @Test
  void topSellersReturnsRankedList() {
    when(product.qualifiedTopSellers(SEP_13, SEP_13, 5))
        .thenReturn(
            new ProductRanking(
                List.of(
                    new TopSeller("Burger", new BigDecimal("10"), new BigDecimal("300.00"), true)),
                new MetricCoverage(SEP_13, SEP_13, List.of(), List.of(SEP_13), List.of()),
                List.of("Incomplete subtotal")));

    RankedListResult result =
        (RankedListResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.PRODUCT_TOP_SELLERS,
                        new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(),
                        null));

    assertThat(result.notices()).containsExactly("Incomplete subtotal");
    assertThat(result.provenance().coverage().unresolvedDates()).isNotEmpty();
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).label()).isEqualTo("Burger");
    assertThat(result.items().get(0).primary()).isEqualByComparingTo("300.00");
    assertThat(result.items().get(0).secondary()).isEqualByComparingTo("10");
    assertThat(result.items().get(0).hasConflict()).isTrue();
  }

  private static ReservationSummary summary(
      LocalDate date, long bookings, long attended, long covers, long noShows) {
    return new ReservationSummary(
        date, bookings, attended, covers, 0, noShows, 0, 0, null, null, null);
  }

  @Test
  void spendPerCoverIsUnavailableWithoutBoundOnMissingInputs() {
    LocalDate monday = SEP_13.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate sunday = monday.plusDays(6);
    List<DailySalesMetric> gross = new ArrayList<>();
    for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
      gross.add(new DailySalesMetric(d, new BigDecimal("1000.00"), null, null, "agreed", false));
    }
    when(sales.dailySales(monday, sunday)).thenReturn(gross);
    // Covers resolve on Monday only: a naive sum would divide 7000 by 100.
    when(reservations.dailySummaries(monday, sunday))
        .thenReturn(List.of(summary(monday, 100, 90, 100, 10)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.SALES_AVERAGE_SPEND_PER_COVER,
                        new TimeRange(monday, sunday, Calendar.CALENDAR),
                        TimeGrain.WEEK,
                        Set.of(),
                        null));

    MetricPoint p = ts.series().get(0).points().get(0);
    assertThat(p.value()).isNull();
    assertThat(ts.notices()).anyMatch(n -> n.contains("excluded days"));
  }

  @Test
  void spendPerCoverFullWeekCoverageIsUnchanged() {
    LocalDate monday = SEP_13.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate sunday = monday.plusDays(6);
    List<DailySalesMetric> gross = new ArrayList<>();
    List<ReservationSummary> summaries = new ArrayList<>();
    for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
      gross.add(new DailySalesMetric(d, new BigDecimal("1000.00"), null, null, "agreed", false));
      summaries.add(summary(d, 100, 90, 100, 10));
    }
    when(sales.dailySales(monday, sunday)).thenReturn(gross);
    when(reservations.dailySummaries(monday, sunday)).thenReturn(summaries);

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.SALES_AVERAGE_SPEND_PER_COVER,
                        new TimeRange(monday, sunday, Calendar.CALENDAR),
                        TimeGrain.WEEK,
                        Set.of(),
                        null));

    MetricPoint p = ts.series().get(0).points().get(0);
    assertThat(p.value()).isEqualByComparingTo("10.0000"); // 7000 / 700
    assertThat(ts.notices()).isEmpty();
  }

  @Test
  void spendPerCoverIsUnresolvedWhenOperandsHaveNoSharedDays() {
    LocalDate monday = SEP_13.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate tuesday = monday.plusDays(1);
    when(sales.dailySales(monday, tuesday))
        .thenReturn(
            List.of(
                new DailySalesMetric(
                    monday, new BigDecimal("5000.00"), null, null, "agreed", false)));
    when(reservations.dailySummaries(monday, tuesday))
        .thenReturn(List.of(summary(tuesday, 100, 90, 100, 10)));

    TimeSeriesResult ts =
        (TimeSeriesResult)
            executor()
                .evaluate(
                    new MetricQuery(
                        MetricId.SALES_AVERAGE_SPEND_PER_COVER,
                        new TimeRange(monday, tuesday, Calendar.CALENDAR),
                        TimeGrain.WEEK,
                        Set.of(),
                        null));

    MetricPoint p = ts.series().get(0).points().get(0);
    assertThat(p.value()).isNull();
    assertThat(p.status()).isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(ts.notices()).isNotEmpty();
  }
}
