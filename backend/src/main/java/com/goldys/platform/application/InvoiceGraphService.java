package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.InvoiceGraphNode;
import com.goldys.platform.semantic.InvoiceGraphQuery;
import com.goldys.platform.semantic.LineGraphNode;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The invoice node graph's read model: authorizes the read and delegates to the semantic {@link
 * InvoiceGraphQuery}. Uses the same {@code inventory.cost} gate as {@link
 * InventoryReportingService}.
 */
@Service
public class InvoiceGraphService {

  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");

  private final InvoiceGraphQuery query;
  private final PermissionService permissions;

  public InvoiceGraphService(InvoiceGraphQuery query, PermissionService permissions) {
    this.query = query;
    this.permissions = permissions;
  }

  public List<SupplierGraphNode> suppliers(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.suppliers(from, to);
  }

  public List<InvoiceGraphNode> invoicesForSupplier(
      UserRole role, String supplier, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.invoicesForSupplier(supplier, from, to);
  }

  public List<LineGraphNode> linesForInvoice(UserRole role, String invoiceNumber) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.linesForInvoice(invoiceNumber);
  }
}
