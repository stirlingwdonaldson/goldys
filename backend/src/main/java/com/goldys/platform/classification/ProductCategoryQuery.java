package com.goldys.platform.classification;

import com.goldys.platform.semantic.AssignmentView;
import com.goldys.platform.semantic.CategoryNode;
import com.goldys.platform.semantic.CategorySuggestionView;
import com.goldys.platform.semantic.DendrogramNode;
import com.goldys.platform.semantic.ProductPoint;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read facades over the curated classification state: the category taxonomy (with per-category
 * product counts), the latest run's dendrogram, the latest run's embedding projection joined to the
 * current assignments, and the assignment queue. All mappings are in-memory (pub scale).
 */
@Service
public class ProductCategoryQuery {

  private final ProductCategoryRepository categories;
  private final ProductCategoryAssignmentRepository assignments;
  private final ClassificationRunRepository runs;
  private final ClassificationClusterRepository clusters;
  private final ClassificationProjectionRepository projections;
  private final CategorySuggestionRepository suggestions;

  public ProductCategoryQuery(
      ProductCategoryRepository categories,
      ProductCategoryAssignmentRepository assignments,
      ClassificationRunRepository runs,
      ClassificationClusterRepository clusters,
      ClassificationProjectionRepository projections,
      CategorySuggestionRepository suggestions) {
    this.categories = categories;
    this.assignments = assignments;
    this.runs = runs;
    this.clusters = clusters;
    this.projections = projections;
    this.suggestions = suggestions;
  }

  /** The category taxonomy in group/display/name order, each with its assigned product count. */
  public List<CategoryNode> taxonomy() {
    Map<UUID, Integer> countByCategory = new HashMap<>();
    for (ProductCategoryAssignment assignment : assignments.findAll()) {
      if (assignment.categoryId() != null) {
        countByCategory.merge(assignment.categoryId(), 1, Integer::sum);
      }
    }
    return categories.findAllByOrderByGroupNameAscDisplayOrderAscNameAsc().stream()
        .map(
            category ->
                new CategoryNode(
                    category.id(),
                    category.name(),
                    category.groupName(),
                    countByCategory.getOrDefault(category.id(), 0)))
        .toList();
  }

  /** The latest clustering run's dendrogram nodes, in deterministic merge/leaf order. */
  public List<DendrogramNode> dendrogram() {
    return latestRunWithClusters()
        .map(run -> clusters.findByRunIdOrderByNodeOrderAsc(run.id()))
        .orElse(List.of())
        .stream()
        .map(this::toDendrogramNode)
        .toList();
  }

  /** The latest run's embedding projection, joined to each product's current assignment. */
  public List<ProductPoint> embeddingSpace() {
    Optional<ClassificationRun> run = latestRunWithClusters();
    if (run.isEmpty()) {
      return List.of();
    }
    Map<String, ProductCategoryAssignment> assignmentByProduct = new HashMap<>();
    for (ProductCategoryAssignment assignment : assignments.findAll()) {
      assignmentByProduct.put(assignment.productNameKey(), assignment);
    }
    UUID runId = run.get().id();
    return projections.findAll().stream()
        .filter(projection -> projection.runId().equals(runId))
        .sorted(Comparator.comparing(ClassificationProjection::productNameKey))
        .map(
            projection -> {
              ProductCategoryAssignment assignment =
                  assignmentByProduct.get(projection.productNameKey());
              return new ProductPoint(
                  projection.productNameKey(),
                  projection.x(),
                  projection.y(),
                  assignment == null ? null : assignment.categoryId(),
                  assignment == null ? null : assignment.confidence());
            })
        .toList();
  }

  /** The assignment queue, optionally filtered to a single status ({@code null} = all). */
  public List<AssignmentView> assignments(AssignmentStatus status) {
    Map<UUID, ProductCategory> categoryById = new HashMap<>();
    for (ProductCategory category : categories.findAll()) {
      categoryById.put(category.id(), category);
    }
    return assignments.findAll().stream()
        .filter(assignment -> status == null || assignment.status() == status)
        .sorted(Comparator.comparing(ProductCategoryAssignment::productNameKey))
        .map(
            assignment -> {
              ProductCategory category =
                  assignment.categoryId() == null
                      ? null
                      : categoryById.get(assignment.categoryId());
              return new AssignmentView(
                  assignment.productNameKey(),
                  assignment.categoryId(),
                  category == null ? null : category.name(),
                  category == null ? null : category.groupName(),
                  assignment.confidence(),
                  assignment.source() == null ? null : assignment.source().name(),
                  assignment.status().name());
            })
        .toList();
  }

  /**
   * The pending taxonomy draft, in {@code display_order}. There is one draft at a time — applying
   * (or a new run) clears it — so this reads all rows rather than filtering by run.
   */
  public List<CategorySuggestionView> suggestions() {
    return suggestions.findAllByOrderByDisplayOrderAsc().stream()
        .map(
            s ->
                new CategorySuggestionView(
                    s.id(), s.runId(), s.name(), s.groupName(), s.displayOrder(), s.hint()))
        .toList();
  }

  /**
   * The most recent {@code SUCCEEDED} {@code CLUSTER} or {@code FULL} run. Status is filtered so a
   * newer {@code RUNNING}/{@code FAILED}/{@code PARTIAL} run never blanks the artifacts of the last
   * run that actually produced them.
   */
  private Optional<ClassificationRun> latestRunWithClusters() {
    ClassificationRun cluster =
        runs.findTopByKindAndStatusOrderByStartedAtDesc(
                ClassificationRunKind.CLUSTER, ClassificationRunStatus.SUCCEEDED)
            .orElse(null);
    ClassificationRun full =
        runs.findTopByKindAndStatusOrderByStartedAtDesc(
                ClassificationRunKind.FULL, ClassificationRunStatus.SUCCEEDED)
            .orElse(null);
    if (cluster == null) {
      return Optional.ofNullable(full);
    }
    if (full == null) {
      return Optional.of(cluster);
    }
    return Optional.of(cluster.startedAt().isAfter(full.startedAt()) ? cluster : full);
  }

  private DendrogramNode toDendrogramNode(ClassificationCluster cluster) {
    // Leaves store mergeHeight/size as NULL in the entity (Ruling D); a leaf is a single product at
    // distance zero, so map null -> 0.0 / 1 for the primitive record fields.
    double mergeHeight = cluster.mergeHeight() == null ? 0.0 : cluster.mergeHeight();
    int size = cluster.size() == null ? 1 : cluster.size();
    return new DendrogramNode(
        cluster.id(),
        cluster.parentId(),
        cluster.isLeaf(),
        cluster.productNameKey(),
        mergeHeight,
        size);
  }
}
