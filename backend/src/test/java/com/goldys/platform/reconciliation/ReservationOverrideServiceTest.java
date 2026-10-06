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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReservationOverrideServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);
  private static final String ACTOR = "a@b.com";

  @Test
  void deniedUserGetsAccessDenied() {
    PermissionService permissions = mock(PermissionService.class);
    org.mockito.Mockito.doThrow(AccessDeniedException.forResource("reservations.metrics"))
        .when(permissions)
        .require(any(), any(), any());

    ReservationOverrideService service =
        new ReservationOverrideService(
            mock(ReservationOverrideRepository.class), permissions, mock(ReservationProjector.class));

    assertThatThrownBy(() -> service.save(OWNER, ACTOR, SEP_20, "LUNCH", 100L, "reason"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void permittedSaveRecordsTheOverride() {
    PermissionService permissions = mock(PermissionService.class);
    ReservationOverrideRepository repository = mock(ReservationOverrideRepository.class);
    when(repository.lockCurrent(SEP_20, "LUNCH")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ReservationOverrideService service =
        new ReservationOverrideService(repository, permissions, mock(ReservationProjector.class));

    ReservationOverride saved = service.save(OWNER, ACTOR, SEP_20, "LUNCH", 100L, "manual count");

    assertThat(saved.overriddenCovers()).isEqualTo(100L);
    assertThat(saved.actorEmail()).isEqualTo(ACTOR);
    verify(permissions)
        .require(OWNER, new ResourceKey("reservations.metrics"), PermissionAction.WRITE);
  }

  @Test
  void aSecondSaveSupersedesThePriorOverride() {
    PermissionService permissions = mock(PermissionService.class);
    ReservationOverrideRepository repository = mock(ReservationOverrideRepository.class);
    ReservationOverride prior =
        ReservationOverride.create(SEP_20, "LUNCH", 90L, "old", ACTOR, Instant.now());
    when(repository.lockCurrent(SEP_20, "LUNCH")).thenReturn(Optional.of(prior));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ReservationOverrideService service =
        new ReservationOverrideService(repository, permissions, mock(ReservationProjector.class));

    service.save(OWNER, ACTOR, SEP_20, "LUNCH", 100L, "new");

    assertThat(prior.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(prior);
  }

  @Test
  void currentCoversReturnsTheActiveOverride() {
    ReservationOverrideRepository repository = mock(ReservationOverrideRepository.class);
    when(repository.findCurrent(SEP_20, "LUNCH"))
        .thenReturn(
            Optional.of(ReservationOverride.create(SEP_20, "LUNCH", 100L, "r", ACTOR, Instant.now())));

    ReservationOverrideService service =
        new ReservationOverrideService(repository, mock(PermissionService.class), mock(ReservationProjector.class));

    assertThat(service.currentCovers(SEP_20, "LUNCH")).contains(100L);
  }
}
