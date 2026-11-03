package com.goldys.platform.auth;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-only account administration for the single owner role. */
@Service
public class UserManagementService {
  private static final ResourceKey RESOURCE = new ResourceKey("users");
  private static final String OWNER_DEPARTMENT = "ALL";
  private static final String OWNER_SENIORITY = "OWNER";
  private static final Clock CLOCK = Clock.systemUTC();

  private final UserAccountRepository accounts;
  private final PasswordEncoder passwordEncoder;
  private final PermissionService permissions;
  private final SessionRegistry sessionRegistry;

  public UserManagementService(
      UserAccountRepository accounts,
      PasswordEncoder passwordEncoder,
      PermissionService permissions,
      SessionRegistry sessionRegistry) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
    this.permissions = permissions;
    this.sessionRegistry = sessionRegistry;
  }

  @Transactional(readOnly = true)
  public List<ManagedUserSummary> list(UserRole actorRole, UUID actorId) {
    permissions.require(actorRole, RESOURCE, PermissionAction.READ);
    return accounts.findAllByOrderByEmailAsc().stream()
        .map(account -> summary(account, actorId))
        .toList();
  }

  @Transactional
  public ManagedUserSummary create(
      UserRole actorRole, UUID actorId, String email, String displayName, String password) {
    permissions.require(actorRole, RESOURCE, PermissionAction.WRITE);
    String normalizedEmail = AuthService.normalizeEmail(email);
    AuthService.validateEmail(normalizedEmail);
    AuthService.validateDisplayName(displayName);
    AuthService.validatePassword(password);
    if (accounts.existsByEmail(normalizedEmail)) {
      throw new IllegalArgumentException("An account already exists for " + normalizedEmail);
    }

    UserAccount account =
        UserAccount.create(
            normalizedEmail,
            passwordEncoder.encode(password),
            displayName.trim(),
            OWNER_DEPARTMENT,
            OWNER_SENIORITY,
            CLOCK.instant());
    account.setPassword(account.passwordHash(), true, CLOCK.instant());
    accounts.save(account);
    return summary(account, actorId);
  }

  @Transactional
  public ManagedUserSummary update(
      UserRole actorRole, UUID actorId, UUID targetId, String displayName, Boolean active) {
    permissions.require(actorRole, RESOURCE, PermissionAction.WRITE);
    if (displayName == null && active == null) {
      throw new IllegalArgumentException("Nothing to update");
    }
    UserAccount account = load(targetId);
    if (displayName != null) {
      AuthService.validateDisplayName(displayName);
      account.rename(displayName.trim(), CLOCK.instant());
    }
    if (active != null) {
      if (!active && targetId.equals(actorId)) {
        throw new IllegalArgumentException("You cannot deactivate your own account");
      }
      if (!active && account.active() && isOwner(account) && lastActiveOwner()) {
        throw new IllegalArgumentException("The last active owner cannot be deactivated");
      }
      account.setActive(active, CLOCK.instant());
      if (!active) {
        revokeSessions(targetId);
      }
    }
    return summary(account, actorId);
  }

  @Transactional
  public void resetPassword(UserRole actorRole, UUID targetId, String password) {
    permissions.require(actorRole, RESOURCE, PermissionAction.WRITE);
    AuthService.validatePassword(password);
    UserAccount account = load(targetId);
    account.setPassword(passwordEncoder.encode(password), true, CLOCK.instant());
    revokeSessions(targetId);
  }

  private UserAccount load(UUID targetId) {
    return accounts
        .findById(targetId)
        .orElseThrow(() -> new IllegalArgumentException("Account not found"));
  }

  private boolean isOwner(UserAccount account) {
    return OWNER_DEPARTMENT.equals(account.department())
        && OWNER_SENIORITY.equals(account.seniority());
  }

  private boolean lastActiveOwner() {
    return accounts.countByDepartmentAndSeniorityAndActiveTrue(OWNER_DEPARTMENT, OWNER_SENIORITY)
        <= 1;
  }

  /** Expire cached session principals so a deactivated or reset user cannot keep working. */
  private void revokeSessions(UUID accountId) {
    for (Object principal : sessionRegistry.getAllPrincipals()) {
      if (principal instanceof AccountUserDetails user && user.id().equals(accountId)) {
        for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
          session.expireNow();
        }
      }
    }
  }

  private static ManagedUserSummary summary(UserAccount account, UUID actorId) {
    return new ManagedUserSummary(
        account.id(),
        account.email(),
        account.displayName(),
        account.active(),
        account.createdAt(),
        account.id().equals(actorId));
  }
}
