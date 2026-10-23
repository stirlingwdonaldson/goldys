package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.SaleCosting;
import com.goldys.platform.semantic.SaleCostingQuery;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class SaleCostingService {
  private final SaleCostingQuery query;
  private final PermissionService permissions;

  public SaleCostingService(SaleCostingQuery query, PermissionService permissions) {
    this.query = query;
    this.permissions = permissions;
  }

  public SaleCosting costing(UserRole role, String saleNumber, LocalDate from, LocalDate to) {
    permissions.require(role, new ResourceKey("sale-items.metrics"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    if (saleNumber == null
        || saleNumber.isBlank()
        || saleNumber.length() > 255
        || from == null
        || to == null
        || from.isAfter(to))
      throw new IllegalArgumentException("A sale number and valid date range are required");
    return query.costing(saleNumber, from, to);
  }
}
