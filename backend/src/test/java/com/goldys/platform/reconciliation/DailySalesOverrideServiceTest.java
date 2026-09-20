package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailySalesOverrideServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  @Test
  void deniedUserGetsAccessDenied() {
    PermissionService permissions = mock(PermissionService.class);
    org.mockito.Mockito.doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    DailySalesOverrideService service =
        new DailySalesOverrideService(mock(DailySalesOverrideRepository.class), permissions);

    assertThatThrownBy(() -> service.save(OWNER, "iss", "sub", SEP_13, "LIGHTSPEED", null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void permittedSaveRecordsTheOverride() {
    PermissionService permissions = mock(PermissionService.class);
    DailySalesOverrideRepository repository = mock(DailySalesOverrideRepository.class);
    when(repository.lockCurrent(SEP_13)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    DailySalesOverrideService service = new DailySalesOverrideService(repository, permissions);

    DailySalesOverride saved =
        service.save(OWNER, "iss", "sub", SEP_13, "LIGHTSPEED", "typo in POS");

    assertThat(saved.authoritativeSource()).isEqualTo("LIGHTSPEED");
    assertThat(saved.actorOidcSubject()).isEqualTo("sub");
    verify(permissions)
        .require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.WRITE);
  }

  @Test
  void aSecondSaveSupersedesThePriorOverride() {
    PermissionService permissions = mock(PermissionService.class);
    DailySalesOverrideRepository repository = mock(DailySalesOverrideRepository.class);
    DailySalesOverride prior =
        DailySalesOverride.create(SEP_13, "CTB", null, "iss", "sub", java.time.Instant.now());
    when(repository.lockCurrent(SEP_13)).thenReturn(Optional.of(prior));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    DailySalesOverrideService service = new DailySalesOverrideService(repository, permissions);

    service.save(OWNER, "iss", "sub", SEP_13, "LIGHTSPEED", null);

    assertThat(prior.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(prior);
  }
}
