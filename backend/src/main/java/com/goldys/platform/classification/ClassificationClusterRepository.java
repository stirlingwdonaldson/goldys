package com.goldys.platform.classification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClassificationClusterRepository extends JpaRepository<ClassificationCluster, UUID> {

  List<ClassificationCluster> findByRunIdOrderByNodeOrderAsc(UUID runId);
}
