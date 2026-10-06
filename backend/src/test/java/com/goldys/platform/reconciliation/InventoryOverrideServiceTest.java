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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InventoryOverrideServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);
  private static final String ACTOR = "a@b.com";

  @Test
  void deniedUserGetsAccessDenied() {
    PermissionService permissions = mock(PermissionService.class);
    org.mockito.Mockito.doThrow(AccessDeniedException.forResource("inventory.cost"))
        .when(permissions)
        .require(any(), any(), any());

    InventoryOverrideService service =
        new InventoryOverrideService(
            mock(InventoryOverrideRepository.class), permissions, mock(InventoryProjector.class));

    assertThatThrownBy(() -> service.save(OWNER, ACTOR, SEP_20, new BigDecimal("70"), "reason"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void permittedSaveRecordsAndProjects() {
    PermissionService permissions = mock(PermissionService.class);
    InventoryOverrideRepository repository = mock(InventoryOverrideRepository.class);
    when(repository.lockCurrent(SEP_20)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    InventoryProjector projector = mock(InventoryProjector.class);

    InventoryOverrideService service = new InventoryOverrideService(repository, permissions, projector);

    InventoryOverride saved =
        service.save(OWNER, ACTOR, SEP_20, new BigDecimal("70.00"), "manual COGS");

    assertThat(saved.overriddenPurchases()).isEqualByComparingTo(new BigDecimal("70.00"));
    verify(permissions).require(OWNER, new ResourceKey("inventory.cost"), PermissionAction.WRITE);
    verify(projector).recompute(SEP_20);
  }

  @Test
  void aSecondSaveSupersedesThePriorOverride() {
    PermissionService permissions = mock(PermissionService.class);
    InventoryOverrideRepository repository = mock(InventoryOverrideRepository.class);
    InventoryOverride prior =
        InventoryOverride.create(SEP_20, new BigDecimal("60.00"), "old", ACTOR, Instant.now());
    when(repository.lockCurrent(SEP_20)).thenReturn(Optional.of(prior));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    InventoryOverrideService service =
        new InventoryOverrideService(repository, permissions, mock(InventoryProjector.class));

    service.save(OWNER, ACTOR, SEP_20, new BigDecimal("70.00"), "new");

    assertThat(prior.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(prior);
  }
}
