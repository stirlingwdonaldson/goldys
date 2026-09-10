package com.goldys.platform.raw;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IngestionFailureRepository extends JpaRepository<IngestionFailure, UUID> {
    List<IngestionFailure> findBySourceSystemOrderByOccurredAtDesc(SourceSystem sourceSystem);
}
