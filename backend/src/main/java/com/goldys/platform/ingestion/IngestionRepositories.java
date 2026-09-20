package com.goldys.platform.ingestion;

import java.util.List;
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
}

interface RawRecordRepository extends JpaRepository<RawRecord, UUID> {
  List<RawRecord> findByIngestionRunId(UUID ingestionRunId);
}
