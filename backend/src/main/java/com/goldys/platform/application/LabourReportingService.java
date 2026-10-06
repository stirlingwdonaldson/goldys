package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
  private final ReservationMetricsQuery reservations;
  private final SalesMetricsQuery sales;
  private final PermissionService permissions;

  public LabourReportingService(
      LabourMetricsQuery labour,
      ReservationMetricsQuery reservations,
      SalesMetricsQuery sales,
      PermissionService permissions) {
    this.labour = labour;
    this.reservations = reservations;
    this.sales = sales;
    this.permissions = permissions;
  }

  public LabourSummary summary(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE_HOURS, PermissionAction.READ);
    permissions.require(role, RESOURCE_COST, PermissionAction.READ);

    BigDecimal scheduledHours = labour.scheduledHours(from, to);
    BigDecimal actualHours = labour.actualHours(from, to);
    BigDecimal labourCost = labour.labourCost(from, to);
    BigDecimal variance = labour.scheduledVsActualVariance(from, to);

    long covers = sumCovers(from, to);
    BigDecimal grossSales = sumGrossSales(from, to);

    return new LabourSummary(
        from.toString(),
        to.toString(),
        scheduledHours,
        actualHours,
        labourCost,
        variance,
        ratio(actualHours, covers),
        labourCost == null ? null : ratio(labourCost, covers),
        departmentPercent(from, to, "FOH", grossSales),
        departmentPercent(from, to, "BOH", grossSales));
  }

  private long sumCovers(LocalDate from, LocalDate to) {
    long total = 0;
    for (CoversMetric m : reservations.dailyCovers(from, to)) {
      total += m.covers();
    }
    return total;
  }

  private BigDecimal sumGrossSales(LocalDate from, LocalDate to) {
    BigDecimal total = BigDecimal.ZERO;
    for (DailySalesMetric m : sales.dailySales(from, to)) {
      if (m.grossSales() != null) {
        total = total.add(m.grossSales());
      }
    }
    return total;
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

  private static BigDecimal ratio(BigDecimal numerator, long denominator) {
    if (numerator == null || denominator == 0) {
      return null;
    }
    return numerator.divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
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
