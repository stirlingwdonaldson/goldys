package com.goldys.platform.classification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ClassificationRunRepository extends JpaRepository<ClassificationRun, UUID> {

  @Query(
      """
      select new com.goldys.platform.classification.ClassificationRunMetadata(
        r.id, r.kind, r.status, r.productCount, r.startedAt, r.finishedAt, r.errorMessage)
      from ClassificationRun r order by r.startedAt desc, r.id desc
      """)
  List<ClassificationRunMetadata> findRecentMetadata(Pageable pageable);

  List<ClassificationRun> findByStatus(ClassificationRunStatus status);

  Optional<ClassificationRun> findTopByKindOrderByStartedAtDesc(ClassificationRunKind kind);

  Optional<ClassificationRun> findTopByKindAndStatusOrderByStartedAtDesc(
      ClassificationRunKind kind, ClassificationRunStatus status);
}
