package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.DeletedSaleDay;
import com.goldys.platform.semantic.DeletedSaleMetricsQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The deleted-sales read model. Authorizes the read and delegates to the resolved semantic query.
 */
@Service
public class DeletedSaleReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("deleted-sales.metrics");

  private final DeletedSaleMetricsQuery metrics;
  private final PermissionService permissions;

  public DeletedSaleReportingService(
      DeletedSaleMetricsQuery metrics, PermissionService permissions) {
    this.metrics = metrics;
    this.permissions = permissions;
  }

  /** Resolved deleted-sale totals over the inclusive range. */
  public List<DeletedSaleDay> dailyTotals(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.dailyTotals(from, to);
  }
}
