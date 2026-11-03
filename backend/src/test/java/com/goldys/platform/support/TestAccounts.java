package com.goldys.platform.support;

import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Seeds real {@code user_account} rows for integration tests, bypassing the removed sign-up. */
public class TestAccounts {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;

  public TestAccounts(JdbcTemplate jdbc, PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.encoder = encoder;
  }

  public void create(
      String email, String displayName, String password, String department, String seniority) {
    jdbc.update(
        "insert into user_account (id, email, password_hash, display_name, department, seniority,"
            + " active, must_change_password, created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, ?, true, false, now(), now())",
        UUID.randomUUID(),
        email.trim().toLowerCase(Locale.ROOT),
        encoder.encode(password),
        displayName,
        department,
        seniority);
  }

  public void createOwner(String email, String displayName, String password) {
    create(email, displayName, password, "ALL", "OWNER");
  }

  public void createStaff(String email, String displayName, String password) {
    create(email, displayName, password, "ALL", "STAFF");
  }
}
