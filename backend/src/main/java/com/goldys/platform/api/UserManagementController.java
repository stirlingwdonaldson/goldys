package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.ManagedUserSummary;
import com.goldys.platform.auth.UserManagementService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only account administration. Every call is authorized in the application service. */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {
  private final UserManagementService users;
  private final CurrentUserService currentUser;

  public UserManagementController(UserManagementService users, CurrentUserService currentUser) {
    this.users = users;
    this.currentUser = currentUser;
  }

  @GetMapping
  List<ManagedUserSummary> list(@AuthenticationPrincipal AccountUserDetails user) {
    return users.list(currentUser.roleOf(user), user.id());
  }

  @PostMapping
  ResponseEntity<ManagedUserSummary> create(
      @RequestBody CreateUserRequest body, @AuthenticationPrincipal AccountUserDetails user) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            users.create(
                currentUser.roleOf(user),
                user.id(),
                body.email(),
                body.displayName(),
                body.password()));
  }

  @PatchMapping("/{id}")
  ManagedUserSummary update(
      @PathVariable UUID id,
      @RequestBody UpdateUserRequest body,
      @AuthenticationPrincipal AccountUserDetails user) {
    return users.update(currentUser.roleOf(user), user.id(), id, body.displayName(), body.active());
  }

  @PostMapping("/{id}/password-reset")
  ResponseEntity<Void> resetPassword(
      @PathVariable UUID id,
      @RequestBody ResetPasswordRequest body,
      @AuthenticationPrincipal AccountUserDetails user) {
    users.resetPassword(currentUser.roleOf(user), id, body.password());
    return ResponseEntity.noContent().build();
  }

  record CreateUserRequest(String email, String displayName, String password) {}

  record UpdateUserRequest(String displayName, Boolean active) {}

  record ResetPasswordRequest(String password) {}
}
