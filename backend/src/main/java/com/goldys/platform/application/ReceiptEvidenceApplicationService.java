package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import com.goldys.platform.canonical.ReceiptImportScope;
import com.goldys.platform.connectors.lightspeed.*;
import com.goldys.platform.semantic.*;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class ReceiptEvidenceApplicationService {
  private final PermissionService permissions;
  private final LightspeedReceiptEvidenceIngestService imports;
  private final ReceiptGroupQuery groups;
  private final LightspeedSaleItemIngestService financial;

  public ReceiptEvidenceApplicationService(
      PermissionService permissions,
      LightspeedReceiptEvidenceIngestService imports,
      ReceiptGroupQuery groups,
      LightspeedSaleItemIngestService financial) {
    this.permissions = permissions;
    this.imports = imports;
    this.groups = groups;
    this.financial = financial;
  }

  public LightspeedReceiptEvidenceIngestService.ImportResult upload(
      UserRole role,
      String dataset,
      byte[] bytes,
      long siteNumber,
      String siteName,
      LocalDate from,
      LocalDate to) {
    permissions.require(role, new ResourceKey("connectors"), PermissionAction.WRITE);
    if (!dataset.equals("mapping") && !dataset.equals("transactions"))
      throw new IllegalArgumentException("Unknown receipt dataset");
    return imports.ingest(
        bytes, new ReceiptImportScope(siteNumber, siteName, from, to), dataset.equals("mapping"));
  }

  public void financial(UserRole role, byte[] bytes) {
    permissions.require(role, new ResourceKey("connectors"), PermissionAction.WRITE);
    financial.ingestCsv(bytes);
  }

  public ReceiptGroup group(UserRole role, String number, LocalDate from, LocalDate to) {
    permissions.require(role, new ResourceKey("sale-items.metrics"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("payments.metrics"), PermissionAction.READ);
    if (number == null
        || number.isBlank()
        || number.length() > 255
        || from == null
        || to == null
        || from.isAfter(to)) throw new IllegalArgumentException("Invalid receipt group scope");
    return groups.group(number, from, to);
  }
}
