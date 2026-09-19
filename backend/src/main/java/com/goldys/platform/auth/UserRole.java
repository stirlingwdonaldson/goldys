package com.goldys.platform.auth;

import java.util.Objects;

/** The department × seniority pair that a permission decision is evaluated against. */
public record UserRole(DepartmentCode department, SeniorityCode seniority) {
  public UserRole {
    Objects.requireNonNull(department, "department");
    Objects.requireNonNull(seniority, "seniority");
  }
}
