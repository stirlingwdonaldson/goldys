package com.goldys.platform.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence boundaries for the ingestion ledger.
 *
 * <p>Package-private on purpose: other modules read ingestion state through services in this
 * package, not by holding JPA entities.
 */
interface IngestionRunRepository extends JpaRepository<IngestionRun, UUID> {
  List<IngestionRun> findAllByOrderByStartedAtDesc();

  List<IngestionRun> findByStartedAtGreaterThanEqual(Instant startedAt);

  Optional<IngestionRun> findFirstBySourceSystemOrderByStartedAtDesc(String sourceSystem);

  List<IngestionRun> findByStatus(IngestionStatus status);

  /**
   * The latest run per source system, in one query. Replaces loading the entire ledger and deduping
   * in memory (the previous behaviour, which grew with ingestion history).
   */
  @Query(
      "select r from IngestionRun r where r.startedAt = "
          + "(select max(r2.startedAt) from IngestionRun r2 where r2.sourceSystem = r.sourceSystem)")
  List<IngestionRun> latestPerSource();

  /** Status distribution for the ingestion-health completeness calculation. */
  @Query(
      "select new com.goldys.platform.ingestion.StatusCount(r.status, count(r)) "
          + "from IngestionRun r group by r.status")
  List<StatusCount> statusCounts();

  /** Mean run duration (seconds) of failed/partial runs, for the time-to-detect metric. */
  @Query(
      value =
          "select avg(extract(epoch from (completed_at - started_at))) from ingestion_run "
              + "where status in ('FAILED', 'PARTIAL') and completed_at is not null",
      nativeQuery = true)
  Double avgFailedDurationSeconds();
}

interface RawRecordRepository extends JpaRepository<RawRecord, UUID> {
  List<RawRecord> findByIngestionRunId(UUID ingestionRunId);

  List<RawRecord> findBySourceSystem(String sourceSystem);
}

/** A status → count aggregate used by the ingestion-health read model. */
record StatusCount(IngestionStatus status, long count) {}
