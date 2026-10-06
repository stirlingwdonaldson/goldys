package com.goldys.platform.dashboard;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedDashboardRepository extends JpaRepository<SavedDashboard, UUID> {
  List<SavedDashboard> findAllByOrderByUpdatedAtDesc();
}
