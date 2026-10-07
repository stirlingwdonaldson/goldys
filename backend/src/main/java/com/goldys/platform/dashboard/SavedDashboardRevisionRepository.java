package com.goldys.platform.dashboard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedDashboardRevisionRepository
    extends JpaRepository<SavedDashboardRevision, UUID> {
  List<SavedDashboardRevision> findByDashboardIdOrderByRevisionDesc(UUID dashboardId);

  Optional<SavedDashboardRevision> findTopByDashboardIdOrderByRevisionDesc(UUID dashboardId);
}
