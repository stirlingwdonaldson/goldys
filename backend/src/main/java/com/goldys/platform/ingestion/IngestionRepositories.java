package com.goldys.platform.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

interface RawRecordRepository extends JpaRepository<RawRecord, UUID> {
  List<RawRecord> findByIngestionRunId(UUID ingestionRunId);

  List<RawRecord> findBySourceSystem(String sourceSystem);
}
