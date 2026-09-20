package com.goldys.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A staff account: email + password hash + the department/seniority pair permissions key on. */
@Entity
@Table(name = "user_account")
class UserAccount {
  @Id private UUID id;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 255)
  private String displayName;

  @Column(name = "department", nullable = false, length = 100)
  private String department;

  @Column(name = "seniority", nullable = false, length = 100)
  private String seniority;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserAccount() {}

  private UserAccount(
      String email,
      String passwordHash,
      String displayName,
      String department,
      String seniority,
      Instant now) {
    this.id = UUID.randomUUID();
    this.email = Objects.requireNonNull(email, "email");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.department = Objects.requireNonNull(department, "department");
    this.seniority = Objects.requireNonNull(seniority, "seniority");
    this.active = true;
    this.createdAt = now;
    this.updatedAt = now;
  }

  static UserAccount create(
      String email,
      String passwordHash,
      String displayName,
      String department,
      String seniority,
      Instant now) {
    return new UserAccount(email, passwordHash, displayName, department, seniority, now);
  }

  UUID id() {
    return id;
  }

  String email() {
    return email;
  }

  String passwordHash() {
    return passwordHash;
  }

  String displayName() {
    return displayName;
  }

  String department() {
    return department;
  }

  String seniority() {
    return seniority;
  }

  boolean active() {
    return active;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }
}
