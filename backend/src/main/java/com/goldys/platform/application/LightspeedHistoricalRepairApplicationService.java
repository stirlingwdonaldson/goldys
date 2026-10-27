package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.connectors.lightspeed.LightspeedHistoricalRepair;
import org.springframework.stereotype.Service;

/** Uses the existing connector-maintenance grant; no startup or scheduled execution. */
@Service
public class LightspeedHistoricalRepairApplicationService {
  private static final ResourceKey RESOURCE = new ResourceKey("connectors");
  private final PermissionService permissions;
  private final LightspeedHistoricalRepair repair;

  public LightspeedHistoricalRepairApplicationService(
      PermissionService permissions, LightspeedHistoricalRepair repair) {
    this.permissions = permissions;
    this.repair = repair;
  }

  public LightspeedHistoricalRepair.Preview preview(UserRole role, byte[] csv) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    return repair.preview(csv);
  }

  public LightspeedHistoricalRepair.Result apply(UserRole role, byte[] csv, String approvedSha256) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    return repair.apply(csv, approvedSha256);
  }
}
