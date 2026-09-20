package com.goldys.platform.auth;

import java.time.Clock;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service sign-up: validate, hash, and insert an account at the lowest role. */
@Service
public class AuthService {
  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final String DEFAULT_DEPARTMENT = "ALL";
  private static final String DEFAULT_SENIORITY = "STAFF";
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Clock CLOCK = Clock.systemUTC();

  private final UserAccountRepository accounts;
  private final PasswordEncoder passwordEncoder;

  public AuthService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public StaffProfileSummary signup(String email, String displayName, String password) {
    String normalizedEmail = normalizeEmail(email);
    if (normalizedEmail == null || !EMAIL.matcher(normalizedEmail).matches()) {
      throw new IllegalArgumentException("Enter a valid email address");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("Display name is required");
    }
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    if (accounts.existsByEmail(normalizedEmail)) {
      throw new IllegalArgumentException("An account already exists for " + normalizedEmail);
    }

    UserAccount account =
        UserAccount.create(
            normalizedEmail,
            passwordEncoder.encode(password),
            displayName.trim(),
            DEFAULT_DEPARTMENT,
            DEFAULT_SENIORITY,
            CLOCK.instant());
    accounts.save(account);
    return new StaffProfileSummary(
        account.displayName(), account.department(), account.seniority());
  }

  public static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
