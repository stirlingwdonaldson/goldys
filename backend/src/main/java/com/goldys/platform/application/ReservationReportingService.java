package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReservationSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The Reservations screen's read model. Authorizes the read and delegates to the resolved
 * reservation semantic query; business metrics come only from the resolved projection.
 */
@Service
public class ReservationReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("reservations.metrics");

  private final ReservationMetricsQuery metrics;
  private final PermissionService permissions;

  public ReservationReportingService(
      ReservationMetricsQuery metrics, PermissionService permissions) {
    this.metrics = metrics;
    this.permissions = permissions;
  }

  /** The resolved summary for a date, or empty when there is no data yet. */
  public Optional<ReservationSummary> summary(UserRole role, LocalDate date) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.summary(date);
  }

  /** Resolved daily covers over the inclusive range. */
  public List<CoversMetric> dailyCovers(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.dailyCovers(from, to);
  }
}
