package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

  private final MetricQueryService metrics;
  private final PermissionService permissions;
  private final com.goldys.platform.semantic.LabourMetricsQuery labour;

  public LabourReportingService(
      MetricQueryService metrics,
      PermissionService permissions,
      com.goldys.platform.semantic.LabourMetricsQuery labour) {
    this.metrics = metrics;
    this.permissions = permissions;
    this.labour = labour;
  }

  /** Period aggregates and ratios share catalogue semantics and retain qualifications. */
  public LabourSummary summary(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE_HOURS, PermissionAction.READ);
    permissions.require(role, RESOURCE_COST, PermissionAction.READ);

    permissions.require(role, new ResourceKey("reconciliation.sales"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("reservations.metrics"), PermissionAction.READ);
    PeriodMetricReader reader =
        new PeriodMetricReader(metrics, new TimeRange(from, to, Calendar.CALENDAR));
    BigDecimal scheduled = reader.value(MetricId.LABOUR_SCHEDULED_HOURS);
    BigDecimal actual = reader.value(MetricId.LABOUR_ACTUAL_HOURS);
    BigDecimal cost = reader.value(MetricId.LABOUR_COST);
    BigDecimal variance = reader.value(MetricId.LABOUR_HOURS_VARIANCE);
    BigDecimal hoursPerCover = reader.value(MetricId.LABOUR_HOURS_PER_COVER);
    BigDecimal costPerCover = reader.value(MetricId.LABOUR_COST_PER_COVER);
    BigDecimal foh = reader.value(MetricId.LABOUR_FOH_PERCENT);
    BigDecimal boh = reader.value(MetricId.LABOUR_BOH_PERCENT);
    List<String> notices = new java.util.ArrayList<>(reader.notices());
    notices.add(
        "Wages only; super and other employment costs excluded pending source-basis confirmation");
    notices.add(
        "Per-cover figures use stated party size of attended bookings, not verified actual arrivals");
    notices.add(
        "Tanda source observations: missing days and open shifts remain unavailable. Completed pending shifts may be included; zero source costs are not an assurance of complete payroll.");
    notices.add("Teams retain their Tanda names without FOH/BOH regrouping.");
    return new LabourSummary(
        from.toString(),
        to.toString(),
        scheduled,
        actual,
        cost,
        variance,
        hoursPerCover,
        costPerCover,
        foh,
        boh,
        List.copyOf(notices),
        labour.dailyLabour(from, to.plusDays(14)));
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
      BigDecimal bohLabourCostPercent,
      List<String> notices,
      List<com.goldys.platform.semantic.LabourMetric> days) {}
}
