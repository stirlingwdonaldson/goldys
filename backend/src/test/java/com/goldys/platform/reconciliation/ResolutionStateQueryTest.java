package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.ResolutionState;
import com.goldys.platform.semantic.ResolutionStateQuery;
import com.goldys.platform.semantic.catalog.MetricId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolutionStateQueryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
  private static final LocalDate TO = LocalDate.of(2026, 9, 30);

  private ResolvedDailySalesRepository dailySales;
  private ResolvedProductSalesRepository productSales;
  private ResolvedReservationDayRepository reservations;
  private ResolvedLabourDayRepository labour;
  private ResolvedInventoryDayRepository inventory;
  private ResolutionStateQuery query;

  @BeforeEach
  void setUp() {
    dailySales = mock(ResolvedDailySalesRepository.class);
    productSales = mock(ResolvedProductSalesRepository.class);
    reservations = mock(ResolvedReservationDayRepository.class);
    labour = mock(ResolvedLabourDayRepository.class);
    inventory = mock(ResolvedInventoryDayRepository.class);
    query =
        new ResolutionStateServiceImpl(dailySales, productSales, reservations, labour, inventory);
  }

  @Test
  void salesGrossMapsResolvedDailySalesRows() {
    ResolvedDailySales row =
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13),
            new BigDecimal("27650.66"),
            new BigDecimal("25136.96"),
            new BigDecimal("2513.70"),
            "agreed",
            "pos",
            false,
            Instant.parse("2026-09-13T10:15:30Z"));
    when(dailySales.findByTradingDateBetweenOrderByTradingDateAsc(FROM, TO))
        .thenReturn(List.of(row));

    List<ResolutionState> states = query.states(MetricId.SALES_GROSS, FROM, TO);

    assertThat(states).hasSize(1);
    assertThat(states.get(0))
        .isEqualTo(
            new ResolutionState(
                LocalDate.of(2026, 9, 13), "agreed", "pos", Instant.parse("2026-09-13T10:15:30Z")));
  }

  @Test
  void productSalesAmountMapsResolvedProductSalesRows() {
    ResolvedProductSales row =
        new ResolvedProductSales(
            LocalDate.of(2026, 9, 14),
            "flat-white",
            new BigDecimal("42"),
            new BigDecimal("199.50"),
            "override",
            "pos",
            false,
            Instant.parse("2026-09-14T08:00:00Z"));
    when(productSales.findByTradingDateBetweenOrderByTradingDateAscProductNameKeyAsc(FROM, TO))
        .thenReturn(List.of(row));

    List<ResolutionState> states = query.states(MetricId.PRODUCT_SALES_AMOUNT, FROM, TO);

    assertThat(states).hasSize(1);
    assertThat(states.get(0))
        .isEqualTo(
            new ResolutionState(
                LocalDate.of(2026, 9, 14),
                "override",
                "pos",
                Instant.parse("2026-09-14T08:00:00Z")));
  }

  @Test
  void reservationsCoversMapsResolvedReservationDayRows() {
    ResolvedReservationDay row =
        new ResolvedReservationDay(
            LocalDate.of(2026, 9, 15),
            "DINNER",
            120,
            118,
            180,
            2,
            0,
            5,
            "rule",
            "reservation-source",
            false,
            Instant.parse("2026-09-15T23:00:00Z"));
    when(reservations.findByTradingDateBetweenOrderByTradingDateAscServicePeriodAsc(FROM, TO))
        .thenReturn(List.of(row));

    List<ResolutionState> states = query.states(MetricId.RESERVATIONS_COVERS, FROM, TO);

    assertThat(states).hasSize(1);
    assertThat(states.get(0))
        .isEqualTo(
            new ResolutionState(
                LocalDate.of(2026, 9, 15),
                "rule",
                "reservation-source",
                Instant.parse("2026-09-15T23:00:00Z")));
  }

  @Test
  void labourCostMapsResolvedLabourDayRows() {
    ResolvedLabourDay row =
        new ResolvedLabourDay(
            LocalDate.of(2026, 9, 16),
            "FOH",
            new BigDecimal("8.0"),
            new BigDecimal("8.5"),
            new BigDecimal("240.00"),
            new BigDecimal("255.00"),
            "agreed",
            "labour-source",
            false,
            Instant.parse("2026-09-16T06:00:00Z"));
    when(labour.findByTradingDateBetweenOrderByTradingDateAscDepartmentAsc(FROM, TO))
        .thenReturn(List.of(row));

    List<ResolutionState> states = query.states(MetricId.LABOUR_COST, FROM, TO);

    assertThat(states).hasSize(1);
    assertThat(states.get(0))
        .isEqualTo(
            new ResolutionState(
                LocalDate.of(2026, 9, 16),
                "agreed",
                "labour-source",
                Instant.parse("2026-09-16T06:00:00Z")));
  }

  @Test
  void inventoryPurchasesMapsResolvedInventoryDayRows() {
    ResolvedInventoryDay row =
        new ResolvedInventoryDay(
            LocalDate.of(2026, 9, 17),
            new BigDecimal("1200.00"),
            new BigDecimal("80.00"),
            new BigDecimal("5000.00"),
            "single",
            "inventory-source",
            false,
            Instant.parse("2026-09-17T07:30:00Z"));
    when(inventory.findByTradingDateBetweenOrderByTradingDateAsc(FROM, TO))
        .thenReturn(List.of(row));

    List<ResolutionState> states = query.states(MetricId.INVENTORY_PURCHASES, FROM, TO);

    assertThat(states).hasSize(1);
    assertThat(states.get(0))
        .isEqualTo(
            new ResolutionState(
                LocalDate.of(2026, 9, 17),
                "single",
                "inventory-source",
                Instant.parse("2026-09-17T07:30:00Z")));
  }

  @Test
  void derivedMetricsHaveNoDirectResolutionState() {
    List<MetricId> derived =
        List.of(
            MetricId.SALES_AVERAGE_SPEND_PER_COVER,
            MetricId.RESERVATIONS_NO_SHOW_RATE,
            MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION,
            MetricId.RESERVATIONS_AVG_PARTY_SIZE,
            MetricId.LABOUR_HOURS_PER_COVER,
            MetricId.LABOUR_FOH_PERCENT,
            MetricId.INVENTORY_FOOD_COST_PERCENT,
            MetricId.PRODUCT_TOP_SELLERS);

    for (MetricId metric : derived) {
      assertThatThrownBy(() -> query.states(metric, FROM, TO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no resolution state for derived metric")
          .hasMessageContaining(metric.value());
    }
  }
}
