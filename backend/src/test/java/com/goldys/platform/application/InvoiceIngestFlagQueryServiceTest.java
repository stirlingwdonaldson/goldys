package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.connectors.ctb.InvoiceIngestFlagService;
import com.goldys.platform.semantic.InvoiceIngestFlagView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceIngestFlagQueryServiceTest {

  private final InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final InvoiceIngestFlagQueryService service =
      new InvoiceIngestFlagQueryService(flags, permissions);

  private static UserRole role() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  @Test
  void delegatesFlagsAfterAuthorizing() {
    when(flags.listFlags())
        .thenReturn(
            List.of(
                new InvoiceIngestFlagView(
                    "MISSING_PDF", "INV-1", null, null, "no PDF filename in CSV", Instant.EPOCH)));

    List<InvoiceIngestFlagView> result = service.flags(role());

    assertThat(result).hasSize(1);
    verify(permissions).require(role(), new ResourceKey("inventory.cost"), PermissionAction.READ);
  }

  @Test
  void rejectsReadsWithoutInventoryPermission() {
    doThrow(new RuntimeException("denied")).when(permissions).require(any(), any(), any());

    assertThatThrownBy(() -> service.flags(role())).isInstanceOf(RuntimeException.class);
    verify(flags, never()).listFlags();
  }
}
