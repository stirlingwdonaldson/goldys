package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserManagementServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final ResourceKey RESOURCE = new ResourceKey("users");

  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final PermissionService permissions = mock(PermissionService.class);
  private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
  private final UserManagementService service =
      new UserManagementService(accounts, encoder, permissions, sessionRegistry);

  @Test
  void createNormalizesEmailAndCreatesAnOwnerAwaitingPasswordChange() {
    when(accounts.existsByEmail("a@b.com")).thenReturn(false);
    when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ManagedUserSummary created =
        service.create(OWNER, UUID.randomUUID(), "  A@B.com ", "A B", "password123");

    assertThat(created.email()).isEqualTo("a@b.com");
    assertThat(created.active()).isTrue();
    assertThat(created.self()).isFalse();
    ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
    verify(accounts).save(saved.capture());
    assertThat(saved.getValue().department()).isEqualTo("ALL");
    assertThat(saved.getValue().seniority()).isEqualTo("OWNER");
    assertThat(saved.getValue().mustChangePassword()).isTrue();
    assertThat(encoder.matches("password123", saved.getValue().passwordHash())).isTrue();
    verify(permissions).require(OWNER, RESOURCE, PermissionAction.WRITE);
  }

  @Test
  void createRejectsADuplicateEmailRegardlessOfCase() {
    when(accounts.existsByEmail("a@b.com")).thenReturn(true);

    assertThatThrownBy(
            () -> service.create(OWNER, UUID.randomUUID(), "A@B.com", "A B", "password123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void updateRefusesSelfDeactivation() {
    UserAccount actor = activeAccount();
    when(accounts.findById(actor.id())).thenReturn(Optional.of(actor));

    assertThatThrownBy(() -> service.update(OWNER, actor.id(), actor.id(), null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("your own account");
  }

  @Test
  void updateRefusesDeactivatingTheLastActiveOwner() {
    UserAccount target = activeAccount();
    when(accounts.findById(target.id())).thenReturn(Optional.of(target));
    when(accounts.countByDepartmentAndSeniorityAndActiveTrue("ALL", "OWNER")).thenReturn(1L);

    assertThatThrownBy(() -> service.update(OWNER, UUID.randomUUID(), target.id(), null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("last active owner");
  }

  @Test
  void deactivationAndResetRevokeLiveSessions() {
    UserAccount target = activeAccount();
    when(accounts.findById(target.id())).thenReturn(Optional.of(target));
    when(accounts.countByDepartmentAndSeniorityAndActiveTrue("ALL", "OWNER")).thenReturn(2L);
    SessionInformation session = mock(SessionInformation.class);
    AccountUserDetails principal = principalFor(target.id());
    when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
    when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of(session));

    service.update(OWNER, UUID.randomUUID(), target.id(), null, false);
    service.resetPassword(OWNER, target.id(), "newpassword123");

    verify(session, times(2)).expireNow();
  }

  @Test
  void listAuthorizesReadAndMarksTheCaller() {
    UserAccount caller = activeAccount();
    when(accounts.findAllByOrderByEmailAsc()).thenReturn(List.of(caller, activeAccount()));

    List<ManagedUserSummary> users = service.list(OWNER, caller.id());

    assertThat(users).hasSize(2);
    assertThat(users.getFirst().self()).isTrue();
    assertThat(users.get(1).self()).isFalse();
    verify(permissions).require(OWNER, RESOURCE, PermissionAction.READ);
  }

  @Test
  void updateRequiresAtLeastOneField() {
    assertThatThrownBy(
            () -> service.update(OWNER, UUID.randomUUID(), UUID.randomUUID(), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nothing to update");
  }

  @Test
  void unknownAccountIsAValidationError() {
    UUID target = UUID.randomUUID();
    when(accounts.findById(target)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(OWNER, UUID.randomUUID(), target, "Name", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Account not found");
  }

  private static UserAccount activeAccount() {
    return UserAccount.create(
        UUID.randomUUID() + "@example.com",
        "hash",
        "A B",
        "ALL",
        "OWNER",
        Instant.parse("2026-11-03T00:00:00Z"));
  }

  private static AccountUserDetails principalFor(UUID id) {
    return new AccountUserDetails(id, "a@b.com", "hash", "A B", "ALL", "OWNER", true, false);
  }
}
