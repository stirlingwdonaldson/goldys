package com.goldys.platform.auth;

import java.time.Clock;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service credential changes: validate, hash, and clear the forced-change flag. */
@Service
public class AuthService {
  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Clock CLOCK = Clock.systemUTC();

  private final UserAccountRepository accounts;
  private final PasswordEncoder passwordEncoder;

  public AuthService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public AccountUserDetails changePassword(
      AccountUserDetails principal, String currentPassword, String newPassword) {
    UserAccount account =
        accounts
            .findById(principal.id())
            .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    if (currentPassword == null
        || !passwordEncoder.matches(currentPassword, account.passwordHash())) {
      throw new IllegalArgumentException("Current password is incorrect");
    }
    validatePassword(newPassword);
    if (passwordEncoder.matches(newPassword, account.passwordHash())) {
      throw new IllegalArgumentException("Choose a password you have not used");
    }

    account.setPassword(passwordEncoder.encode(newPassword), false, CLOCK.instant());
    return AccountUserDetails.of(account);
  }

  public static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  static void validateEmail(String email) {
    if (email == null || !EMAIL.matcher(email).matches()) {
      throw new IllegalArgumentException("Enter a valid email address");
    }
  }

  static void validateDisplayName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("Display name is required");
    }
  }

  static void validatePassword(String password) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
  }
}
