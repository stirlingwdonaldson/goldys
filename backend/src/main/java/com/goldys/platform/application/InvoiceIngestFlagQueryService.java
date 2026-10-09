package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.connectors.ctb.InvoiceIngestFlagService;
import com.goldys.platform.semantic.InvoiceIngestFlagView;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read model for invoice-ingestion flags. Gated on the same {@code inventory.cost} resource as
 * {@link InvoiceGraphService} and {@link InventoryReportingService}; no flag is visible without the
 * invoice/food-cost read permission.
 */
@Service
public class InvoiceIngestFlagQueryService {

  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");

  private final InvoiceIngestFlagService flags;
  private final PermissionService permissions;

  public InvoiceIngestFlagQueryService(
      InvoiceIngestFlagService flags, PermissionService permissions) {
    this.flags = flags;
    this.permissions = permissions;
  }

  public List<InvoiceIngestFlagView> flags(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return flags.listFlags();
  }
}
