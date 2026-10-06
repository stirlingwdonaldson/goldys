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

class LabourOverrideServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);
  private static final String ACTOR = "a@b.com";

  @Test
  void deniedUserGetsAccessDenied() {
    PermissionService permissions = mock(PermissionService.class);
    org.mockito.Mockito.doThrow(AccessDeniedException.forResource("labour.hours"))
        .when(permissions)
        .require(any(), any(), any());

    LabourOverrideService service =
        new LabourOverrideService(
            mock(LabourOverrideRepository.class), permissions, mock(LabourProjector.class));

    assertThatThrownBy(
            () -> service.save(OWNER, ACTOR, SEP_20, "FOH", new BigDecimal("8"), "reason"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void permittedSaveRecordsAndProjects() {
    PermissionService permissions = mock(PermissionService.class);
    LabourOverrideRepository repository = mock(LabourOverrideRepository.class);
    when(repository.lockCurrent(SEP_20, "FOH")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    LabourProjector projector = mock(LabourProjector.class);

    LabourOverrideService service = new LabourOverrideService(repository, permissions, projector);

    LabourOverride saved =
        service.save(OWNER, ACTOR, SEP_20, "FOH", new BigDecimal("8.00"), "manual hours");

    assertThat(saved.overriddenActualHours()).isEqualByComparingTo(new BigDecimal("8.00"));
    verify(permissions).require(OWNER, new ResourceKey("labour.hours"), PermissionAction.WRITE);
    verify(projector).recompute(SEP_20);
  }

  @Test
  void aSecondSaveSupersedesThePriorOverride() {
    PermissionService permissions = mock(PermissionService.class);
    LabourOverrideRepository repository = mock(LabourOverrideRepository.class);
    LabourOverride prior =
        LabourOverride.create(SEP_20, "FOH", new BigDecimal("7.00"), "old", ACTOR, Instant.now());
    when(repository.lockCurrent(SEP_20, "FOH")).thenReturn(Optional.of(prior));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    LabourOverrideService service =
        new LabourOverrideService(repository, permissions, mock(LabourProjector.class));

    service.save(OWNER, ACTOR, SEP_20, "FOH", new BigDecimal("8.00"), "new");

    assertThat(prior.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(prior);
  }
}
