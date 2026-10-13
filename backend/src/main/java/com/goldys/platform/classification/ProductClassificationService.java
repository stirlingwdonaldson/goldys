package com.goldys.platform.classification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the categorisation pipeline end to end: builds each product's feature text, embeds
 * and clusters them into a dendrogram, and assigns each product to a category via the classifier.
 *
 * <p>The three entry points mirror {@code ForecastService}: {@link #runClustering()} ({@code
 * CLUSTER}), {@link #runAssignment()} ({@code ASSIGN}) and {@link #runFull()} ({@code FULL}) each
 * persist a {@code RUNNING} run and dispatch the work to the application task executor, so a slow
 * model call never blocks the submission.
 */
@Service
public class ProductClassificationService {

  /** Confidence at or above which a classification is trusted enough to auto-assign. */
  static final BigDecimal ASSIGN_CONFIDENCE = new BigDecimal("0.5");

  private final FeatureTextExtractor featureTexts;
  private final ProductCategoryRepository categories;
  private final ProductCategoryAssignmentRepository assignments;
  private final ClassificationRunRepository runs;
  private final ClassificationClusterRepository clusters;
  private final ClassificationProjectionRepository projections;
  private final EmbeddingClient embedding;
  private final ProductClassifier classifier;
  private final TaskExecutor executor;
  private final Clock clock;

  @Autowired
  public ProductClassificationService(
      FeatureTextExtractor featureTexts,
      ProductCategoryRepository categories,
      ProductCategoryAssignmentRepository assignments,
      ClassificationRunRepository runs,
      ClassificationClusterRepository clusters,
      ClassificationProjectionRepository projections,
      EmbeddingClient embedding,
      ProductClassifier classifier,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
    this(
        featureTexts,
        categories,
        assignments,
        runs,
        clusters,
        projections,
        embedding,
        classifier,
        executor,
        Clock.systemUTC());
  }

  /** Test-friendly constructor: an explicit executor and clock, no qualifier. */
  ProductClassificationService(
      FeatureTextExtractor featureTexts,
      ProductCategoryRepository categories,
      ProductCategoryAssignmentRepository assignments,
      ClassificationRunRepository runs,
      ClassificationClusterRepository clusters,
      ClassificationProjectionRepository projections,
      EmbeddingClient embedding,
      ProductClassifier classifier,
      TaskExecutor executor,
      Clock clock) {
    this.featureTexts = featureTexts;
    this.categories = categories;
    this.assignments = assignments;
    this.runs = runs;
    this.clusters = clusters;
    this.projections = projections;
    this.embedding = embedding;
    this.classifier = classifier;
    this.executor = executor;
    this.clock = clock;
  }

  /** Submits a clustering-only run. */
  public void runClustering() {
    submit(ClassificationRunKind.CLUSTER);
  }

  /** Submits an assignment-only run. */
  public void runAssignment() {
    submit(ClassificationRunKind.ASSIGN);
  }

  /** Submits a full run: clustering followed by assignment in one {@code FULL} run. */
  public void runFull() {
    submit(ClassificationRunKind.FULL);
  }

  private void submit(ClassificationRunKind kind) {
    ClassificationRun run = ClassificationRun.create(kind, clock.instant());
    runs.save(run);
    UUID runId = run.id();
    executor.execute(() -> execute(runId));
  }

  /** Runs a queued run and advances it to {@code SUCCEEDED} or {@code FAILED}. */
  public void execute(UUID runId) {
    ClassificationRun run =
        runs.findById(runId)
            .orElseThrow(() -> new IllegalStateException("unknown classification run: " + runId));
    run.markRunning();
    runs.save(run);

    Instant now = clock.instant();
    try {
      int productCount =
          switch (run.kind()) {
            case CLUSTER -> cluster(run);
            case ASSIGN -> assign(run);
            case FULL -> {
              int count = cluster(run);
              assign(run);
              yield count;
            }
          };
      run.markSucceeded(productCount, now);
    } catch (RuntimeException e) {
      run.markFailed(failureMessage(e), now);
    }
    runs.save(run);
  }

  /**
   * Embeds and clusters the current feature texts, persisting the dendrogram nodes and the 2-D
   * projection. Returns the number of products covered.
   */
  private int cluster(ClassificationRun run) {
    Map<String, String> texts = featureTexts.featureTexts();
    List<String> products = new ArrayList<>(texts.keySet());
    List<float[]> vectors = embedding.embed(new ArrayList<>(texts.values()));
    ClusterResult result = embedding.cluster(vectors);

    Map<Integer, UUID> idByNodeId = new HashMap<>();
    for (ClusterNode node : result.nodes()) {
      idByNodeId.put(node.id(), UUID.randomUUID());
    }
    for (ClusterNode node : result.nodes()) {
      boolean leaf = node.isLeaf();
      String productNameKey = leaf ? products.get(node.productIndex()) : null;
      Double mergeHeight = leaf ? null : node.height();
      Integer size = leaf ? null : node.size();
      UUID parentId = node.parentId() == null ? null : idByNodeId.get(node.parentId());
      clusters.save(
          new ClassificationCluster(
              idByNodeId.get(node.id()),
              run.id(),
              parentId,
              leaf,
              productNameKey,
              mergeHeight,
              size));
    }
    for (int i = 0; i < result.projection().size(); i++) {
      Point2d point = result.projection().get(i);
      projections.save(
          new ClassificationProjection(run.id(), products.get(i), point.x(), point.y()));
    }
    return products.size();
  }

  /**
   * Assigns every product to a category via the classifier. Returns the number of products covered.
   * Skips {@code MANUAL} overrides; low-confidence results are left for review. With no category
   * taxonomy every product is reset to {@code UNASSIGNED}.
   */
  private int assign(ClassificationRun run) {
    Map<String, String> texts = featureTexts.featureTexts();
    List<String> products = new ArrayList<>(texts.keySet());
    Instant now = clock.instant();

    List<CategoryOption> options =
        categories.findAllByOrderByGroupNameAscDisplayOrderAscNameAsc().stream()
            .map(c -> new CategoryOption(c.id(), c.name(), c.description()))
            .toList();
    if (options.isEmpty()) {
      for (String key : products) {
        ProductCategoryAssignment assignment =
            assignments.findById(key).orElseGet(() -> ProductCategoryAssignment.create(key, now));
        assignment.markUnassigned(now);
        assignments.save(assignment);
      }
      return products.size();
    }

    for (String key : products) {
      ProductCategoryAssignment existing = assignments.findById(key).orElse(null);
      if (existing != null && existing.source() == AssignmentSource.MANUAL) {
        continue;
      }
      ProductClassification classification =
          classifier.classify(new ProductClassificationRequest(texts.get(key), options));
      ProductCategoryAssignment assignment =
          existing != null ? existing : ProductCategoryAssignment.create(key, now);
      if (classification.confidence().compareTo(ASSIGN_CONFIDENCE) >= 0) {
        assignment.markAssigned(
            classification.categoryId(), classification.confidence(), AssignmentSource.JEV, now);
      } else {
        assignment.markNeedsReview(classification.categoryId(), classification.confidence(), now);
      }
      assignments.save(assignment);
    }
    return products.size();
  }

  private String failureMessage(RuntimeException e) {
    // EmbeddingException/JevException messages are already operator-facing (no payload,
    // credentials,
    // or tokens). Anything else gets a class-only label.
    if (e instanceof EmbeddingException || e instanceof JevException) {
      return e.getMessage();
    }
    return "unexpected error: " + e.getClass().getSimpleName();
  }
}
