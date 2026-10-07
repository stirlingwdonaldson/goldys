package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.Comparison;
import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DashboardFiltersTest {

  private static final TimeRange RANGE =
      new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), Calendar.CALENDAR);
  private static final MetricCatalog CATALOG = new MetricCatalog();

  @Test
  void departmentFilterDoesNotApplyToSalesGross() {
    DashboardFilters f = new DashboardFilters(null, null, Set.of(Dimension.DEPARTMENT));
    MetricQuery q = new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null);

    MetricQuery merged = SavedDashboardApplicationService.merge(q, f, CATALOG);

    assertThat(merged.dimensions()).isEmpty(); // sales.gross declares no DEPARTMENT
  }

  @Test
  void departmentFilterAppliesToLabourCost() {
    DashboardFilters f = new DashboardFilters(null, null, Set.of(Dimension.DEPARTMENT));
    MetricQuery q = new MetricQuery(MetricId.LABOUR_COST, RANGE, TimeGrain.DAY, Set.of(), null);

    MetricQuery merged = SavedDashboardApplicationService.merge(q, f, CATALOG);

    assertThat(merged.dimensions()).contains(Dimension.DEPARTMENT);
  }

  @Test
  void dateRangeOverrides() {
    TimeRange r =
        new TimeRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), Calendar.CALENDAR);
    DashboardFilters f = new DashboardFilters(r, Comparison.PREVIOUS_WEEK, Set.of());
    MetricQuery q = new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null);

    MetricQuery merged = SavedDashboardApplicationService.merge(q, f, CATALOG);

    assertThat(merged.range()).isEqualTo(r);
    assertThat(merged.comparison()).isEqualTo(Comparison.PREVIOUS_WEEK);
  }
}
