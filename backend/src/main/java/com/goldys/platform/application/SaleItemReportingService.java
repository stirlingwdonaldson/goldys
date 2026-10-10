package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.SaleItemMetricsQuery;
import com.goldys.platform.semantic.SaleItemMix;
import com.goldys.platform.semantic.SaleItemRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** The sale-items read model. Authorizes the read and delegates to the resolved semantic query. */
@Service
public class SaleItemReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("sale-items.metrics");

  private final SaleItemMetricsQuery metrics;
  private final CanonicalSaleItemQuery saleItems;
  private final PermissionService permissions;

  public SaleItemReportingService(
      SaleItemMetricsQuery metrics,
      CanonicalSaleItemQuery saleItems,
      PermissionService permissions) {
    this.metrics = metrics;
    this.saleItems = saleItems;
    this.permissions = permissions;
  }

  /** Resolved sale-item mix over the inclusive range. */
  public List<SaleItemMix> dailyByCategory(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.dailyByCategory(from, to);
  }

  /** Paged, filterable listing of current sale line items (canonical browse surface). */
  public DataPage<SaleItemRow> list(
      UserRole role,
      String category,
      String saleNumber,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return saleItems.page(category, saleNumber, from, to, page, size);
  }
}
