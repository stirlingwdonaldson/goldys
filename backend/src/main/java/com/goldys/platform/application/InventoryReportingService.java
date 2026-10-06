package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * The Inventory screen's read model. Authorizes the read and composes inventory COGS with resolved
 * sales to produce the food-cost percentage.
 */
@Service
public class InventoryReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");
  private static final int SCALE = 4;

  private final InventoryMetricsQuery inventory;
  private final SalesMetricsQuery sales;
  private final PermissionService permissions;

  public InventoryReportingService(
      InventoryMetricsQuery inventory, SalesMetricsQuery sales, PermissionService permissions) {
    this.inventory = inventory;
    this.sales = sales;
    this.permissions = permissions;
  }

  public InventorySummary summary(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);

    BigDecimal purchases = inventory.purchases(from, to);
    BigDecimal wastage = inventory.wastage(from, to);
    BigDecimal grossSales = sumGrossSales(from, to);
    BigDecimal foodCostPercent =
        purchases == null || grossSales.signum() == 0
            ? null
            : purchases.divide(grossSales, SCALE, RoundingMode.HALF_UP);

    return new InventorySummary(
        from.toString(), to.toString(), purchases, wastage, foodCostPercent);
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

  public record InventorySummary(
      String from,
      String to,
      BigDecimal purchases,
      BigDecimal wastage,
      BigDecimal foodCostPercent) {}
}
