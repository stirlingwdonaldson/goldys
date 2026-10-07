package com.goldys.platform.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** A role (department × seniority) granted view access to a {@code SHARED} dashboard. */
@Entity
@Table(name = "saved_dashboard_share")
public class SavedDashboardShare {
  @Id private UUID id;

  @Column(name = "dashboard_id", nullable = false)
  private UUID dashboardId;

  @Column(name = "department", nullable = false, length = 64)
  private String department;

  @Column(name = "seniority", nullable = false, length = 64)
  private String seniority;

  protected SavedDashboardShare() {}

  private SavedDashboardShare(UUID id, UUID dashboardId, String department, String seniority) {
    this.id = Objects.requireNonNull(id, "id");
    this.dashboardId = Objects.requireNonNull(dashboardId, "dashboardId");
    this.department = Objects.requireNonNull(department, "department");
    this.seniority = Objects.requireNonNull(seniority, "seniority");
  }

  public static SavedDashboardShare create(UUID dashboardId, String department, String seniority) {
    return new SavedDashboardShare(UUID.randomUUID(), dashboardId, department, seniority);
  }

  public UUID id() {
    return id;
  }

  public UUID dashboardId() {
    return dashboardId;
  }

  public String department() {
    return department;
  }

  public String seniority() {
    return seniority;
  }
}
