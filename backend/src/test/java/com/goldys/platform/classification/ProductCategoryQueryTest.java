package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DendrogramNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage for the read facades' run selection and null-safety. */
class ProductCategoryQueryTest {

  private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
  private final ProductCategoryAssignmentRepository assignments =
      mock(ProductCategoryAssignmentRepository.class);
  private final ClassificationRunRepository runs = mock(ClassificationRunRepository.class);
  private final ClassificationClusterRepository clusters =
      mock(ClassificationClusterRepository.class);
  private final ClassificationProjectionRepository projections =
      mock(ClassificationProjectionRepository.class);

  private final ProductCategoryQuery query =
      new ProductCategoryQuery(categories, assignments, runs, clusters, projections);

  @Test
  void dendrogram_prefers_succeeded_run_over_newer_running_run() {
    Instant older = Instant.parse("2026-10-13T00:00:00Z");
    Instant newer = Instant.parse("2026-10-13T01:00:00Z");

    ClassificationRun succeeded = ClassificationRun.create(ClassificationRunKind.CLUSTER, older);
    succeeded.markSucceeded(1, older);
    ClassificationRun running = ClassificationRun.create(ClassificationRunKind.CLUSTER, newer);

    ClassificationCluster leaf =
        new ClassificationCluster(
            UUID.randomUUID(), succeeded.id(), null, true, "kids fish", null, null, 0);

    when(runs.findTopByKindAndStatusOrderByStartedAtDesc(
            ClassificationRunKind.CLUSTER, ClassificationRunStatus.SUCCEEDED))
        .thenReturn(Optional.of(succeeded));
    when(runs.findTopByKindAndStatusOrderByStartedAtDesc(
            ClassificationRunKind.FULL, ClassificationRunStatus.SUCCEEDED))
        .thenReturn(Optional.empty());
    when(clusters.findByRunIdOrderByNodeOrderAsc(succeeded.id())).thenReturn(List.of(leaf));

    List<DendrogramNode> result = query.dendrogram();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).productNameKey()).isEqualTo("kids fish");
    assertThat(result.get(0).isLeaf()).isTrue();
    assertThat(result.get(0).mergeHeight()).isEqualTo(0.0);
    assertThat(result.get(0).size()).isEqualTo(1);
  }
}
