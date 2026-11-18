package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.semantic.catalog.*;
import com.goldys.platform.widget.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MetricChartRendererTest {
  private final MetricChartRenderer renderer = new MetricChartRenderer(new MetricCatalog());
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

  private static MetricQuery query(MetricId metric, LocalDate from, int days) {
    return new MetricQuery(
        metric,
        new TimeRange(from, from.plusDays(days - 1), Calendar.CALENDAR),
        TimeGrain.DAY,
        Set.of(),
        null);
  }

  private static ChartOptions options(ChartKind kind) {
    return new ChartOptions(kind, false, false, null);
  }

  private static TimeSeriesResult result(MetricQuery query, String... values) {
    var points =
        java.util.stream.IntStream.range(0, values.length)
            .mapToObj(
                i ->
                    new MetricPoint(
                        query.range().from().plusDays(i),
                        values[i] == null ? null : new BigDecimal(values[i])))
            .toList();
    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, points)),
        List.of("Qualified source"),
        new MetricProvenance(
            query.metric(),
            "1",
            query.range(),
            query.grain(),
            "test",
            Instant.EPOCH,
            List.of(),
            "1"));
  }

  @ParameterizedTest
  @EnumSource(ChartKind.class)
  void everyFamilyCarriesTheSameVerifiedValuesAndSerializes(ChartKind kind) throws Exception {
    var query = query(MetricId.SALES_GROSS, MONDAY, 3);
    var plan = List.of(query);
    renderer.validatePlan(plan, options(kind));
    var chart = renderer.render(plan, List.of(result(query, "10", "20", "30")), options(kind));
    assertThat(chart.kind()).isEqualTo(kind);
    assertThat(chart.yFormat()).isEqualTo("currency");
    assertThat(chart.series().getFirst().points())
        .extracting(Point::y)
        .containsExactly(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"));
    var mapper = new ObjectMapper();
    var json = mapper.readTree(mapper.writeValueAsString(chart));
    assertThat(json.path("type").asText()).isEqualTo("chart");
    assertThat(mapper.treeToValue(json, WidgetSpec.class)).isEqualTo(chart);
  }

  @Test
  void rankedBarsPreserveRankingAndNeverBecomeAShareOfTheWhole() {
    var query = query(MetricId.PRODUCT_TOP_SELLERS, MONDAY, 3);
    var ranked =
        new RankedListResult(
            query.metric(),
            List.of(
                new MetricRankedItem("Wine", new BigDecimal("30"), null, false),
                new MetricRankedItem("Beer", BigDecimal.TEN, null, false)),
            List.of(),
            result(query, "1").provenance());
    var chart = renderer.render(List.of(query), List.of(ranked), options(ChartKind.BAR));
    assertThat(chart.series().getFirst().points())
        .extracting(Point::x)
        .containsExactly("Wine", "Beer");
    assertThat(chart.yFormat()).isEqualTo("currency");
    assertThatThrownBy(
            () -> renderer.render(List.of(query), List.of(ranked), options(ChartKind.PIE)))
        .hasMessageContaining("BAR only");
  }

  @Test
  void stackedAreaPreservesDisjointGroupsAndRejectsMissingGroupBuckets() {
    var base = query(MetricId.SALES_GROSS, MONDAY, 3);
    var query =
        new MetricQuery(
            base.metric(), base.range(), base.grain(), Set.of(Dimension.SERVICE_PERIOD), null);
    var breakfast = result(query, "10", "20", "30");
    var dinner = result(query, "40", "50", "60");
    var grouped =
        new TimeSeriesResult(
            query.metric(),
            List.of(
                new MetricSeries("Breakfast", breakfast.series().getFirst().points()),
                new MetricSeries("Dinner", dinner.series().getFirst().points())),
            List.of(),
            breakfast.provenance());
    var options = new ChartOptions(ChartKind.AREA, true, false, null);
    renderer.validatePlan(List.of(query), options);
    var chart = renderer.render(List.of(query), List.of(grouped), options);
    assertThat(chart.series()).hasSize(2);
    assertThat(chart.series().get(1).label()).contains("Dinner");
    assertThat(chart.series().get(1).points())
        .extracting(Point::y)
        .containsExactly(new BigDecimal("40"), new BigDecimal("50"), new BigDecimal("60"));
    var incomplete =
        new TimeSeriesResult(
            query.metric(),
            List.of(new MetricSeries("Dinner", dinner.series().getFirst().points().subList(0, 2))),
            List.of(),
            dinner.provenance());
    assertThatThrownBy(() -> renderer.render(List.of(query), List.of(incomplete), options))
        .hasMessageContaining("complete nonnegative");
  }

  @Test
  void alignsPeriodsWithoutInventingMissingValuesOrCollidingSeries() {
    var current = query(MetricId.SALES_GROSS, MONDAY, 5);
    var previous = query(MetricId.SALES_GROSS, MONDAY.minusWeeks(1), 5);
    var options = new ChartOptions(ChartKind.LINE, false, false, ChartOptions.Alignment.WEEKDAY);
    renderer.validatePlan(List.of(current, previous), options);
    var chart =
        renderer.render(
            List.of(current, previous),
            List.of(result(current, "10", null, "0"), result(previous, "20", "30")),
            options);
    assertThat(chart.series()).extracting(Series::key).doesNotHaveDuplicates();
    assertThat(chart.series().getFirst().points())
        .extracting(Point::x)
        .containsExactly("Mon", "Tue", "Wed", "Thu", "Fri");
    assertThat(chart.series().getFirst().points())
        .extracting(Point::y)
        .containsExactly(BigDecimal.TEN, null, BigDecimal.ZERO, null, null);
    assertThat(chart.series().get(1).label()).contains("2026-11-02", "2026-11-06");
  }

  @Test
  void dateAlignmentSortsTheUnionAndPreservesBothPeriods() {
    var current = query(MetricId.SALES_GROSS, MONDAY, 2);
    var previous = query(MetricId.SALES_GROSS, MONDAY.minusWeeks(1), 2);
    var chart =
        renderer.render(
            List.of(current, previous),
            List.of(result(current, "10", "20"), result(previous, "30", "40")),
            options(ChartKind.LINE));
    assertThat(chart.series().getFirst().points())
        .extracting(Point::x)
        .containsExactly("2026-11-02", "2026-11-03", "2026-11-09", "2026-11-10");
    assertThat(chart.series().getFirst().points())
        .extracting(Point::y)
        .containsExactly(null, null, BigDecimal.TEN, new BigDecimal("20"));
  }

  @Test
  void totalsAreCategoriesNotPretendDailyPoints() {
    var one =
        new MetricQuery(
            MetricId.SALES_GROSS,
            new TimeRange(MONDAY, MONDAY.plusDays(6), Calendar.CALENDAR),
            TimeGrain.TOTAL,
            Set.of(),
            null);
    var two =
        new MetricQuery(
            MetricId.SALES_GROSS,
            new TimeRange(MONDAY.minusWeeks(1), MONDAY.minusDays(1), Calendar.CALENDAR),
            TimeGrain.TOTAL,
            Set.of(),
            null);
    renderer.validatePlan(List.of(one, two), options(ChartKind.BAR));
    var chart =
        renderer.render(
            List.of(one, two),
            List.of(result(one, "100"), result(two, "200")),
            options(ChartKind.BAR));
    assertThat(chart.series()).hasSize(1);
    assertThat(chart.series().getFirst().points())
        .extracting(Point::x)
        .allSatisfy(x -> assertThat(x).contains(" – "));
    assertThatThrownBy(() -> renderer.validatePlan(List.of(one, two), options(ChartKind.LINE)))
        .hasMessageContaining("ordered");
  }

  @Test
  void rejectsMixedUnitsGrainsAndInvalidWeekdayAlignment() {
    var sales = query(MetricId.SALES_GROSS, MONDAY, 3);
    assertThatThrownBy(
            () ->
                renderer.validatePlan(
                    List.of(sales, query(MetricId.RESERVATIONS_BOOKINGS, MONDAY, 3)),
                    options(ChartKind.LINE)))
        .hasMessageContaining("same unit");
    var shifted = query(MetricId.SALES_GROSS, MONDAY.minusDays(6), 3);
    assertThatThrownBy(
            () ->
                renderer.validatePlan(
                    List.of(sales, shifted),
                    new ChartOptions(ChartKind.LINE, false, false, ChartOptions.Alignment.WEEKDAY)))
        .hasMessageContaining("matching weekdays");
    var total = new MetricQuery(sales.metric(), sales.range(), TimeGrain.TOTAL, Set.of(), null);
    assertThatThrownBy(() -> renderer.validatePlan(List.of(sales, total), options(ChartKind.BAR)))
        .hasMessageContaining("same grain");
  }

  @ParameterizedTest
  @EnumSource(
      value = ChartKind.class,
      names = {"PIE", "DONUT", "RADAR", "RADIAL"})
  void angularChartsNeverHideGapsOrNegativeValues(ChartKind kind) {
    var query = query(MetricId.SALES_GROSS, MONDAY, 3);
    assertThatThrownBy(
            () ->
                renderer.render(
                    List.of(query), List.of(result(query, "10", null, "20")), options(kind)))
        .hasMessageContaining("complete nonnegative");
    assertThatThrownBy(
            () ->
                renderer.render(
                    List.of(query), List.of(result(query, "10", "-1", "20")), options(kind)))
        .hasMessageContaining("complete nonnegative");
  }

  @Test
  void barsRetainNegativesAndRealZeroButNoChartPretendsAbsentDataExists() {
    var query = query(MetricId.SALES_GROSS, MONDAY, 3);
    var chart =
        renderer.render(
            List.of(query), List.of(result(query, "-1", "0", null)), options(ChartKind.BAR));
    assertThat(chart.series().getFirst().points())
        .extracting(Point::y)
        .containsExactly(new BigDecimal("-1"), BigDecimal.ZERO, null);
    assertThatThrownBy(
            () ->
                renderer.render(
                    List.of(query),
                    List.of(result(query, null, null, null)),
                    options(ChartKind.LINE)))
        .hasMessageContaining("No observed");
    assertThatThrownBy(
            () ->
                renderer.render(
                    List.of(query),
                    List.of(result(query, "0", "0", "0")),
                    options(ChartKind.DONUT)))
        .hasMessageContaining("Zero-only");
  }

  @Test
  void cannotTurnRatiosOrOverlappingQueriesIntoPartsOfAWhole() {
    var ratio = query(MetricId.RESERVATIONS_NO_SHOW_RATE, MONDAY, 3);
    assertThatThrownBy(() -> renderer.validatePlan(List.of(ratio), options(ChartKind.PIE)))
        .hasMessageContaining("additive");
    var sales = query(MetricId.SALES_GROSS, MONDAY, 3);
    assertThatThrownBy(() -> renderer.validatePlan(List.of(sales, sales), options(ChartKind.DONUT)))
        .hasMessageContaining("one additive");
    assertThatThrownBy(
            () ->
                renderer.validatePlan(
                    List.of(sales), new ChartOptions(ChartKind.AREA, true, false, null)))
        .hasMessageContaining("groups");
  }

  @Test
  void incompleteCoverageCannotBePresentedAsAWholeEvenWithNonNullTotals() {
    var query = query(MetricId.SALES_GROSS, MONDAY, 3);
    var result = result(query, "10", "20", "30");
    var provenance = result.provenance();
    var incomplete =
        new TimeSeriesResult(
            result.metric(),
            result.series(),
            result.notices(),
            new MetricProvenance(
                result.metric(),
                "1",
                query.range(),
                query.grain(),
                "test",
                Instant.EPOCH,
                List.of(MONDAY),
                "1"));
    assertThatThrownBy(
            () -> renderer.render(List.of(query), List.of(incomplete), options(ChartKind.PIE)))
        .hasMessageContaining("Incomplete coverage");
    assertThat(provenance.missingPeriods()).isEmpty();
  }

  @Test
  void boundsAndInvalidOptionsFailExplicitly() {
    var query = query(MetricId.SALES_GROSS, MONDAY, 367);
    assertThatThrownBy(() -> renderer.validatePlan(List.of(query), options(ChartKind.LINE)))
        .hasMessageContaining("366");
    assertThatThrownBy(() -> new ChartOptions(ChartKind.PIE, true, false, null))
        .hasMessageContaining("stacking");
    assertThatThrownBy(() -> new ChartOptions(ChartKind.LINE, false, true, null))
        .hasMessageContaining("horizontal");
  }
}
