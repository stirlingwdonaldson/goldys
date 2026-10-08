package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.DataExplorerQuery;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
import com.goldys.platform.semantic.RawFilter;
import com.goldys.platform.semantic.RawRecordDetail;
import com.goldys.platform.semantic.RawRecordSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Authorized read facade for the owner's data explorer. Every method is gated on the {@code
 * connectors} READ permission before delegating to the {@link DataExplorerQuery} port, so the REST
 * controller never touches the explorer's read seam directly.
 */
@Service
public class DataExplorerService {
  private static final ResourceKey RESOURCE = new ResourceKey("connectors");

  private final DataExplorerQuery explorer;
  private final PermissionService permissions;

  public DataExplorerService(DataExplorerQuery explorer, PermissionService permissions) {
    this.explorer = explorer;
    this.permissions = permissions;
  }

  public DataPage<RawRecordSummary> listRaw(UserRole role, RawFilter filter, int page, int size) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return explorer.listRaw(filter, page, size);
  }

  public RawRecordDetail rawDetail(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return explorer.rawDetail(id);
  }

  public List<EntityDescriptor> canonicalEntities(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return explorer.canonicalEntities();
  }

  public DataPage<GenericRow> canonicalRows(UserRole role, String entityId, int page, int size) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return explorer.canonicalRows(entityId, page, size);
  }

  public List<EntityDescriptor> resolvedDomains(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return explorer.resolvedDomains();
  }

  public DataPage<GenericRow> resolvedRows(UserRole role, String domainId, int page, int size) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return explorer.resolvedRows(domainId, page, size);
  }
}
