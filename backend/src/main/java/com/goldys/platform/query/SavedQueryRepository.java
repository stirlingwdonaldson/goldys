package com.goldys.platform.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SavedQueryRepository extends JpaRepository<SavedQuery, UUID> {
  List<SavedQuery> findByCreatedByOrderByUpdatedAtDesc(String createdBy);

  Optional<SavedQuery> findByIdAndCreatedBy(UUID id, String createdBy);
}
