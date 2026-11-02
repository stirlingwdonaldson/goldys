package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.connectors.lightspeed.*;
import com.goldys.platform.semantic.ReceiptGroupQuery;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReceiptEvidenceApplicationServiceTest {
  private final PermissionService permissions = mock(PermissionService.class);
  private final LightspeedReceiptEvidenceIngestService imports =
      mock(LightspeedReceiptEvidenceIngestService.class);
  private final LightspeedSaleItemIngestService financial =
      mock(LightspeedSaleItemIngestService.class);
  private final ReceiptGroupQuery groups = mock(ReceiptGroupQuery.class);
  private final ReceiptEvidenceApplicationService service =
      new ReceiptEvidenceApplicationService(permissions, imports, groups, financial);
  private final UserRole role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void deniedImportsHaveNoLedgerOrFinancialEffects() {
    doThrow(AccessDeniedException.forResource("connectors"))
        .when(permissions)
        .require(role, new ResourceKey("connectors"), PermissionAction.WRITE);
    assertThatThrownBy(
            () ->
                service.upload(
                    role,
                    "transactions",
                    new byte[0],
                    96181,
                    "site",
                    LocalDate.now(),
                    LocalDate.now()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.financial(role, new byte[0]))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(imports, financial);
  }

  @Test
  void receiptEvidenceRequiresBothMerchandiseAndPaymentReadPermissions() {
    doThrow(AccessDeniedException.forResource("payments.metrics"))
        .when(permissions)
        .require(role, new ResourceKey("payments.metrics"), PermissionAction.READ);
    assertThatThrownBy(() -> service.group(role, "receipt", LocalDate.now(), LocalDate.now()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(groups);
  }
}
