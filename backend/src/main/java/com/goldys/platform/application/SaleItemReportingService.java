package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.SaleItemMetricsQuery;
import com.goldys.platform.semantic.SaleItemMix;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** The sale-items read model. Authorizes the read and delegates to the resolved semantic query. */
@Service
public class SaleItemReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("sale-items.metrics");

  private final SaleItemMetricsQuery metrics;
  private final PermissionService permissions;

  public SaleItemReportingService(SaleItemMetricsQuery metrics, PermissionService permissions) {
    this.metrics = metrics;
    this.permissions = permissions;
  }

  /** Resolved sale-item mix over the inclusive range. */
  public List<SaleItemMix> dailyByCategory(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.dailyByCategory(from, to);
  }
}
