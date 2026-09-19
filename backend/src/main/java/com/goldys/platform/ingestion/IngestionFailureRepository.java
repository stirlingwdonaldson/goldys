package com.goldys.platform.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IngestionFailureRepository extends JpaRepository<IngestionFailure, UUID> {
  List<IngestionFailure> findByIngestionRunIdOrderByOccurredAtAsc(UUID ingestionRunId);

  long countByIngestionRunId(UUID ingestionRunId);
}
