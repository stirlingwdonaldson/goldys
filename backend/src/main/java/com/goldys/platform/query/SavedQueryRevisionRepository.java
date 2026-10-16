package com.goldys.platform.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SavedQueryRevisionRepository extends JpaRepository<SavedQueryRevision, UUID> {
  List<SavedQueryRevision> findByQueryIdOrderByRevisionDesc(UUID queryId);

  Optional<SavedQueryRevision> findByQueryIdAndRevision(UUID queryId, int revision);
}
