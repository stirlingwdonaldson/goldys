package com.goldys.platform.classification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClassificationRunRepository extends JpaRepository<ClassificationRun, UUID> {

  List<ClassificationRun> findAllByOrderByStartedAtDesc();

  List<ClassificationRun> findByStatus(ClassificationRunStatus status);

  Optional<ClassificationRun> findTopByKindOrderByStartedAtDesc(ClassificationRunKind kind);

  Optional<ClassificationRun> findTopByKindAndStatusOrderByStartedAtDesc(
      ClassificationRunKind kind, ClassificationRunStatus status);
}
