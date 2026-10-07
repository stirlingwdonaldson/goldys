package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.ResolutionState;
import com.goldys.platform.semantic.ResolutionStateQuery;
import com.goldys.platform.semantic.catalog.MetricId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * {@link ResolutionStateQuery} backed by the resolved read projections. Routes a {@link MetricId}
 * to the domain's resolved read model and projects each row to a {@link ResolutionState}. Derived
 * metrics have no direct resolved projection and therefore no resolution state of their own.
 */
@Service
public class ResolutionStateServiceImpl implements ResolutionStateQuery {
  private final ResolvedDailySalesRepository dailySales;
  private final ResolvedProductSalesRepository productSales;
  private final ResolvedReservationDayRepository reservations;
  private final ResolvedLabourDayRepository labour;
  private final ResolvedInventoryDayRepository inventory;

  public ResolutionStateServiceImpl(
      ResolvedDailySalesRepository dailySales,
      ResolvedProductSalesRepository productSales,
      ResolvedReservationDayRepository reservations,
      ResolvedLabourDayRepository labour,
      ResolvedInventoryDayRepository inventory) {
    this.dailySales = dailySales;
    this.productSales = productSales;
    this.reservations = reservations;
    this.labour = labour;
    this.inventory = inventory;
  }

  @Override
  public List<ResolutionState> states(MetricId metric, LocalDate from, LocalDate to) {
    return switch (metric) {
      case SALES_GROSS, SALES_NET, SALES_GST ->
          dailySales.findByTradingDateBetweenOrderByTradingDateAsc(from, to).stream()
              .map(
                  r ->
                      new ResolutionState(
                          r.tradingDate(),
                          r.resolutionType(),
                          r.authoritativeSource(),
                          r.resolvedAt()))
              .toList();
      case PRODUCT_SALES_AMOUNT, PRODUCT_SALES_QUANTITY ->
          productSales
              .findByTradingDateBetweenOrderByTradingDateAscProductNameKeyAsc(from, to)
              .stream()
              .map(
                  r ->
                      new ResolutionState(
                          r.tradingDate(),
                          r.resolutionType(),
                          r.authoritativeSource(),
                          r.resolvedAt()))
              .toList();
      case RESERVATIONS_BOOKINGS,
          RESERVATIONS_ATTENDED,
          RESERVATIONS_COVERS,
          RESERVATIONS_NO_SHOWS ->
          reservations
              .findByTradingDateBetweenOrderByTradingDateAscServicePeriodAsc(from, to)
              .stream()
              .map(
                  r ->
                      new ResolutionState(
                          r.tradingDate(),
                          r.resolutionType(),
                          r.authoritativeSource(),
                          r.resolvedAt()))
              .toList();
      case LABOUR_SCHEDULED_HOURS, LABOUR_ACTUAL_HOURS, LABOUR_COST ->
          labour.findByTradingDateBetweenOrderByTradingDateAscDepartmentAsc(from, to).stream()
              .map(
                  r ->
                      new ResolutionState(
                          r.tradingDate(),
                          r.resolutionType(),
                          r.authoritativeSource(),
                          r.resolvedAt()))
              .toList();
      case INVENTORY_PURCHASES, INVENTORY_WASTAGE, INVENTORY_STOCK_ON_HAND ->
          inventory.findByTradingDateBetweenOrderByTradingDateAsc(from, to).stream()
              .map(
                  r ->
                      new ResolutionState(
                          r.tradingDate(),
                          r.resolutionType(),
                          r.authoritativeSource(),
                          r.resolvedAt()))
              .toList();
      default ->
          throw new IllegalArgumentException(
              "no resolution state for derived metric " + metric.value());
    };
  }
}
