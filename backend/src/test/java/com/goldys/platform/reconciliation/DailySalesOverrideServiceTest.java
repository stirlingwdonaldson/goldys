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
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailySalesOverrideServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final String ACTOR_EMAIL = "a@b.com";

  private static CanonicalDailySalesQuery queryWithLightspeed() {
    CanonicalDailySalesQuery query = mock(CanonicalDailySalesQuery.class);
    when(query.currentDailySalesForDate(SEP_13))
        .thenReturn(
            List.of(
                new DailySalesView("LIGHTSPEED", SEP_13, new BigDecimal("27650.66"), null, null)));
    return query;
  }

  @Test
  void deniedUserGetsAccessDenied() {
    PermissionService permissions = mock(PermissionService.class);
    org.mockito.Mockito.doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    DailySalesOverrideService service =
        new DailySalesOverrideService(
            mock(DailySalesOverrideRepository.class), permissions, queryWithLightspeed());

    assertThatThrownBy(() -> service.save(OWNER, ACTOR_EMAIL, SEP_13, "LIGHTSPEED", null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void unknownSourceIsRejected() {
    DailySalesOverrideService service =
        new DailySalesOverrideService(
            mock(DailySalesOverrideRepository.class),
            mock(PermissionService.class),
            queryWithLightspeed());

    assertThatThrownBy(() -> service.save(OWNER, ACTOR_EMAIL, SEP_13, "CTB", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CTB");
  }

  @Test
  void permittedSaveRecordsTheOverride() {
    PermissionService permissions = mock(PermissionService.class);
    DailySalesOverrideRepository repository = mock(DailySalesOverrideRepository.class);
    when(repository.lockCurrent(SEP_13)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    DailySalesOverrideService service =
        new DailySalesOverrideService(repository, permissions, queryWithLightspeed());

    DailySalesOverride saved =
        service.save(OWNER, ACTOR_EMAIL, SEP_13, "LIGHTSPEED", "typo in POS");

    assertThat(saved.authoritativeSource()).isEqualTo("LIGHTSPEED");
    assertThat(saved.actorEmail()).isEqualTo(ACTOR_EMAIL);
    verify(permissions)
        .require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.WRITE);
  }

  @Test
  void aSecondSaveSupersedesThePriorOverride() {
    PermissionService permissions = mock(PermissionService.class);
    DailySalesOverrideRepository repository = mock(DailySalesOverrideRepository.class);
    DailySalesOverride prior =
        DailySalesOverride.create(SEP_13, "LIGHTSPEED", null, ACTOR_EMAIL, java.time.Instant.now());
    when(repository.lockCurrent(SEP_13)).thenReturn(Optional.of(prior));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    DailySalesOverrideService service =
        new DailySalesOverrideService(repository, permissions, queryWithLightspeed());

    service.save(OWNER, ACTOR_EMAIL, SEP_13, "LIGHTSPEED", null);

    assertThat(prior.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(prior);
  }
}
