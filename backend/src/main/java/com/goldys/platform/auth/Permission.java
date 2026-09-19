package com.goldys.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * One table-driven grant: a department × seniority × resource triple with independent read and
 * write flags.
 *
 * <p>Rows ship empty (V1) and are populated only from the stakeholder-approved field-to-role
 * matrix. There is no ordinal comparison between seniorities and no owner branch anywhere.
 */
@Entity
@Table(name = "permission")
class Permission {
  @Id private UUID id;

  @Column(name = "department", nullable = false, updatable = false)
  private String department;

  @Column(name = "seniority", nullable = false, updatable = false)
  private String seniority;

  @Column(name = "resource", nullable = false, updatable = false)
  private String resource;

  @Column(name = "can_read", nullable = false, updatable = false)
  private boolean canRead;

  @Column(name = "can_write", nullable = false, updatable = false)
  private boolean canWrite;

  protected Permission() {}

  private Permission(
      String department, String seniority, String resource, boolean canRead, boolean canWrite) {
    this.id = UUID.randomUUID();
    this.department = requireCode("department", department);
    this.seniority = requireCode("seniority", seniority);
    this.resource = Objects.requireNonNull(resource, "resource");
    this.canRead = canRead;
    this.canWrite = canWrite;
  }

  static Permission grant(
      String department, String seniority, String resource, boolean canRead, boolean canWrite) {
    return new Permission(department, seniority, resource, canRead, canWrite);
  }

  private static String requireCode(String label, String value) {
    if (value == null) {
      throw new IllegalArgumentException(label + " must not be null");
    }
    return value;
  }

  String department() {
    return department;
  }

  String seniority() {
    return seniority;
  }

  String resource() {
    return resource;
  }

  boolean canRead() {
    return canRead;
  }

  boolean canWrite() {
    return canWrite;
  }
}
