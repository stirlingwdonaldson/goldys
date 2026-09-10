package com.goldys.platform.raw;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionFailureRepository extends JpaRepository<IngestionFailure, UUID> {
  List<IngestionFailure> findBySourceSystemOrderByOccurredAtDesc(SourceSystem sourceSystem);
}
