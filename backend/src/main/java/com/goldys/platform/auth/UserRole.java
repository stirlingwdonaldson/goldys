package com.goldys.platform.auth;

import java.util.Objects;

/**
 * A user's effective role: the combination of their department and seniority (spec Requirement 3 -
 * "effective role is the combination"). Not a JPA entity itself; this is what gets checked against
 * Permission rows.
 */
public final class UserRole {
  private final Department department;
  private final Seniority seniority;

  public UserRole(Department department, Seniority seniority) {
    this.department = Objects.requireNonNull(department);
    this.seniority = Objects.requireNonNull(seniority);
  }

  public Department department() {
    return department;
  }

  public Seniority seniority() {
    return seniority;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserRole)) return false;
    UserRole that = (UserRole) o;
    return department == that.department && seniority == that.seniority;
  }

  @Override
  public int hashCode() {
    return Objects.hash(department, seniority);
  }

  @Override
  public String toString() {
    return department + " x " + seniority;
  }
}
