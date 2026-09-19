package com.goldys.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A local staff profile keyed by OIDC issuer plus subject, carrying the department and seniority
 * that permission decisions are evaluated against.
 */
@Entity
@Table(name = "staff_profile")
class StaffProfile {
  @Id private UUID id;

  @Column(name = "oidc_issuer", nullable = false, length = 512)
  private String oidcIssuer;

  @Column(name = "oidc_subject", nullable = false, length = 255)
  private String oidcSubject;

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

  protected StaffProfile() {}

  String oidcIssuer() {
    return oidcIssuer;
  }

  String oidcSubject() {
    return oidcSubject;
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

  void touch(Instant now) {
    this.updatedAt = Objects.requireNonNull(now, "now");
  }
}
