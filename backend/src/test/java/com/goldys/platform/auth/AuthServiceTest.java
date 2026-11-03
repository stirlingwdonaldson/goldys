package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final AuthService service = new AuthService(accounts, encoder);

  @Test
  void normalizesEmail() {
    assertThat(AuthService.normalizeEmail("  A@B.com  ")).isEqualTo("a@b.com");
    assertThat(AuthService.normalizeEmail(null)).isNull();
  }

  @Test
  void changePasswordRejectsTheWrongCurrentPassword() {
    UserAccount account = accountWithPassword("password123", false);
    when(accounts.findById(account.id())).thenReturn(Optional.of(account));

    assertThatThrownBy(
            () -> service.changePassword(details(account), "wrong-password", "newpassword123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Current password");
  }

  @Test
  void changePasswordRejectsAShortNewPassword() {
    UserAccount account = accountWithPassword("password123", false);
    when(accounts.findById(account.id())).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> service.changePassword(details(account), "password123", "short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 8");
  }

  @Test
  void changePasswordRejectsReusingTheCurrentPassword() {
    UserAccount account = accountWithPassword("password123", false);
    when(accounts.findById(account.id())).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> service.changePassword(details(account), "password123", "password123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not used");
  }

  @Test
  void changePasswordClearsTheForcedFlagOnSuccess() {
    UserAccount account = accountWithPassword("password123", true);
    when(accounts.findById(account.id())).thenReturn(Optional.of(account));

    AccountUserDetails refreshed =
        service.changePassword(details(account), "password123", "newpassword123");

    assertThat(refreshed.mustChangePassword()).isFalse();
    assertThat(encoder.matches("newpassword123", account.passwordHash())).isTrue();
    assertThat(account.mustChangePassword()).isFalse();
  }

  private UserAccount accountWithPassword(String password, boolean mustChangePassword) {
    UserAccount account =
        UserAccount.create(
            "a@b.com", encoder.encode(password), "A B", "ALL", "OWNER", Instant.now());
    account.setPassword(encoder.encode(password), mustChangePassword, Instant.now());
    return account;
  }

  private static AccountUserDetails details(UserAccount account) {
    return new AccountUserDetails(
        account.id(),
        account.email(),
        account.passwordHash(),
        account.displayName(),
        account.department(),
        account.seniority(),
        account.active(),
        account.mustChangePassword());
  }
}
