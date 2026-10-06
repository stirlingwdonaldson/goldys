package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResolutionRuleServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void deniedSaveThrows() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    ResolutionRuleService service =
        new ResolutionRuleService(
            mock(ResolutionRuleRepository.class), permissions, mock(DailySalesProjector.class));

    assertThatThrownBy(
            () ->
                service.save(
                    OWNER,
                    "a@b.com",
                    new ResolutionRuleService.RuleInput(
                        "daily_sales", "daily_sales", "priority", null, List.of("CTB"))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void saveSupersedesThePriorRule() {
    PermissionService permissions = mock(PermissionService.class);
    ResolutionRuleRepository repository = mock(ResolutionRuleRepository.class);
    ResolutionRule prior =
        ResolutionRule.create(
            "daily_sales",
            "daily_sales",
            "priority",
            null,
            List.of("CTB"),
            "a@b.com",
            Instant.now());
    when(repository.lockCurrent("daily_sales", "daily_sales")).thenReturn(Optional.of(prior));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ResolutionRuleService service =
        new ResolutionRuleService(repository, permissions, mock(DailySalesProjector.class));

    ResolutionRuleView saved =
        service.save(
            OWNER,
            "a@b.com",
            new ResolutionRuleService.RuleInput(
                "daily_sales", "daily_sales", "priority", null, List.of("LIGHTSPEED")));

    assertThat(prior.supersededAt()).isNotNull();
    verify(repository).saveAndFlush(prior);
    assertThat(saved.sourcePriority()).containsExactly("LIGHTSPEED");
  }

  @Test
  void rejectsUnknownEntityType() {
    ResolutionRuleService service =
        new ResolutionRuleService(
            mock(ResolutionRuleRepository.class),
            mock(PermissionService.class),
            mock(DailySalesProjector.class));

    assertThatThrownBy(
            () ->
                service.save(
                    OWNER,
                    "a@b.com",
                    new ResolutionRuleService.RuleInput(
                        "sales", "daily_sales", "priority", null, List.of("CTB"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("entity type");
  }

  @Test
  void rejectsPriorityWithoutSourceOrder() {
    ResolutionRuleService service =
        new ResolutionRuleService(
            mock(ResolutionRuleRepository.class),
            mock(PermissionService.class),
            mock(DailySalesProjector.class));

    assertThatThrownBy(
            () ->
                service.save(
                    OWNER,
                    "a@b.com",
                    new ResolutionRuleService.RuleInput(
                        "daily_sales", "daily_sales", "priority", null, List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source order");
  }

  @Test
  void rejectsUnknownCustomLogic() {
    ResolutionRuleService service =
        new ResolutionRuleService(
            mock(ResolutionRuleRepository.class),
            mock(PermissionService.class),
            mock(DailySalesProjector.class));

    assertThatThrownBy(
            () ->
                service.save(
                    OWNER,
                    "a@b.com",
                    new ResolutionRuleService.RuleInput(
                        "daily_sales", "daily_sales", "custom", "frobnicate", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("custom logic");
  }

  @Test
  void deleteRejectsMalformedId() {
    ResolutionRuleService service =
        new ResolutionRuleService(
            mock(ResolutionRuleRepository.class),
            mock(PermissionService.class),
            mock(DailySalesProjector.class));

    assertThatThrownBy(() -> service.delete(OWNER, "a@b.com", "not-a-uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid rule id");
  }

  @Test
  void deleteRejectsUnknownId() {
    ResolutionRuleRepository repository = mock(ResolutionRuleRepository.class);
    java.util.UUID id = java.util.UUID.randomUUID();
    when(repository.findCurrentById(id)).thenReturn(Optional.empty());
    ResolutionRuleService service =
        new ResolutionRuleService(
            repository, mock(PermissionService.class), mock(DailySalesProjector.class));

    assertThatThrownBy(() -> service.delete(OWNER, "a@b.com", id.toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No current rule");
  }
}
