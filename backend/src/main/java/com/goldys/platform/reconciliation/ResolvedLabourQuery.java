package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link LabourMetricsQuery} backed by the resolved labour projection. */
@Service
public class ResolvedLabourQuery implements LabourMetricsQuery {
  private final ResolvedLabourDayRepository repository;

  public ResolvedLabourQuery(ResolvedLabourDayRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<LabourMetric> dailyLabour(LocalDate from, LocalDate to) {
    return rows(from, to).stream().map(this::toMetric).toList();
  }

  @Override
  public BigDecimal scheduledHours(LocalDate from, LocalDate to) {
    return sumHours(rows(from, to).stream().map(ResolvedLabourDay::scheduledHours).toList());
  }

  @Override
  public BigDecimal actualHours(LocalDate from, LocalDate to) {
    return sumHours(rows(from, to).stream().map(ResolvedLabourDay::actualHours).toList());
  }

  @Override
  public BigDecimal labourCost(LocalDate from, LocalDate to) {
    BigDecimal total = BigDecimal.ZERO;
    var days = rows(from, to);
    if (days.isEmpty()) return null;
    for (ResolvedLabourDay r : days) {
      if (r.actualCost() == null) {
        return null; // any unknown day cost makes the period total unknown
      }
      total = total.add(r.actualCost());
    }
    return total;
  }

  @Override
  public BigDecimal scheduledVsActualVariance(LocalDate from, LocalDate to) {
    var scheduled = scheduledHours(from, to);
    var actual = actualHours(from, to);
    return scheduled == null || actual == null ? null : scheduled.subtract(actual);
  }

  private List<ResolvedLabourDay> rows(LocalDate from, LocalDate to) {
    return repository.findByTradingDateBetweenOrderByTradingDateAscDepartmentAsc(from, to);
  }

  private LabourMetric toMetric(ResolvedLabourDay r) {
    return new LabourMetric(
        r.tradingDate(),
        r.department(),
        r.scheduledHours(),
        r.actualHours(),
        r.scheduledCost(),
        r.actualCost(),
        r.authoritativeSource(),
        r.hasConflict());
  }

  private static BigDecimal sumHours(List<BigDecimal> values) {
    if (values.isEmpty()) return null;
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal v : values) {
      if (v == null) return null;
      total = total.add(v);
    }
    return total;
  }
}
