package com.goldys.platform.dashboard;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedDashboardShareRepository extends JpaRepository<SavedDashboardShare, UUID> {
  List<SavedDashboardShare> findByDashboardId(UUID dashboardId);
}
