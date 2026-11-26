package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import com.goldys.platform.connectors.ctb.CtbProductDetailRecovery;
import org.springframework.stereotype.Service;

/** Explicit connector-maintenance permission is required for both preview and apply. */
@Service
public class CtbProductDetailRecoveryApplicationService {
  private final PermissionService permissions;
  private final CtbProductDetailRecovery recovery;

  public CtbProductDetailRecoveryApplicationService(
      PermissionService permissions, CtbProductDetailRecovery recovery) {
    this.permissions = permissions;
    this.recovery = recovery;
  }

  public CtbProductDetailRecovery.Preview preview(
      UserRole role, CtbProductDetailRecovery.Request request) {
    permissions.require(role, new ResourceKey("connectors"), PermissionAction.WRITE);
    return recovery.preview(request);
  }

  public CtbProductDetailRecovery.Result apply(
      UserRole role, CtbProductDetailRecovery.Request request, String approvedPreviewSha256) {
    permissions.require(role, new ResourceKey("connectors"), PermissionAction.WRITE);
    return recovery.apply(request, approvedPreviewSha256);
  }
}
