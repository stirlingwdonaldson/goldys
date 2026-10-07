package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The Staff & Labor screen's read model. Authorizes the read and composes single-domain labour
 * metrics with cross-domain denominators (covers and sales) to produce hours/cost-per-cover and
 * FOH/BOH labour-cost percentages.
 */
@Service
public class LabourReportingService {
  private static final ResourceKey RESOURCE_HOURS = new ResourceKey("labour.hours");
  private static final ResourceKey RESOURCE_COST = new ResourceKey("labour.cost");
  private static final int SCALE = 4;

  private final LabourMetricsQuery labour;
  private final MetricQueryService metrics;
  private final PermissionService permissions;

  public LabourReportingService(
      LabourMetricsQuery labour, MetricQueryService metrics, PermissionService permissions) {
    this.labour = labour;
    this.metrics = metrics;
    this.permissions = permissions;
  }

  public LabourSummary summary(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE_HOURS, PermissionAction.READ);
    permissions.require(role, RESOURCE_COST, PermissionAction.READ);

    TimeRange range = new TimeRange(from, to, Calendar.CALENDAR);

    BigDecimal scheduledHours = total(MetricId.LABOUR_SCHEDULED_HOURS, range);
    BigDecimal actualHours = total(MetricId.LABOUR_ACTUAL_HOURS, range);
    BigDecimal labourCost = total(MetricId.LABOUR_COST, range);
    BigDecimal variance = total(MetricId.LABOUR_HOURS_VARIANCE, range);

    BigDecimal covers = total(MetricId.RESERVATIONS_COVERS, range);
    BigDecimal grossSales = total(MetricId.SALES_GROSS, range);

    return new LabourSummary(
        from.toString(),
        to.toString(),
        scheduledHours,
        actualHours,
        labourCost,
        variance,
        ratio(actualHours, covers),
        ratio(labourCost, covers),
        departmentPercent(from, to, "FOH", grossSales),
        departmentPercent(from, to, "BOH", grossSales));
  }

  /** Sums non-null per-day points across all series; null when nothing resolved (never zero). */
  private BigDecimal total(MetricId id, TimeRange range) {
    TimeSeriesResult result =
        (TimeSeriesResult) metrics.query(new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null));
    BigDecimal total = BigDecimal.ZERO;
    boolean any = false;
    for (MetricSeries series : result.series()) {
      for (MetricPoint point : series.points()) {
        if (point.value() != null) {
          total = total.add(point.value());
          any = true;
        }
      }
    }
    return any ? total : null;
  }

  private BigDecimal departmentPercent(
      LocalDate from, LocalDate to, String department, BigDecimal grossSales) {
    BigDecimal cost = null;
    boolean unknown = false;
    for (LabourMetric m : labour.dailyLabour(from, to)) {
      if (department.equals(m.department())) {
        if (m.actualCost() == null) {
          unknown = true;
        } else {
          cost = cost == null ? m.actualCost() : cost.add(m.actualCost());
        }
      }
    }
    if (unknown) {
      cost = null;
    }
    if (cost == null || grossSales == null || grossSales.signum() == 0) {
      return null;
    }
    return cost.divide(grossSales, SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) {
      return null;
    }
    return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
  }

  public record LabourSummary(
      String from,
      String to,
      BigDecimal scheduledHours,
      BigDecimal actualHours,
      BigDecimal labourCost,
      BigDecimal variance,
      BigDecimal hoursPerCover,
      BigDecimal labourCostPerCover,
      BigDecimal fohLabourCostPercent,
      BigDecimal bohLabourCostPercent) {}
}
