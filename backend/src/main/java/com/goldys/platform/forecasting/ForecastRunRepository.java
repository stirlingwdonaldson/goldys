package com.goldys.platform.forecasting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persisted forecast runs, listed newest-first and filterable by lifecycle status. */
public interface ForecastRunRepository extends JpaRepository<ForecastRun, UUID> {
  List<ForecastRun> findAllByOrderByCreatedAtDesc();

  List<ForecastRun> findByStatus(ForecastRunStatus status);
}
