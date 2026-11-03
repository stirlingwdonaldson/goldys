package com.goldys.platform.classification;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.goldys.platform.semantic.ClassificationPreview;
import com.goldys.platform.semantic.ClassificationRunView;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  /** The cap on {@link #listRuns()}; a single venue never needs more than the recent few. */
  static final int RUN_LIST_LIMIT = 25;

  /** How many product feature texts a single cluster contributes to the naming prompt. */
  private static final int NAMING_SAMPLE_LIMIT = 15;

  /**
   * Concurrent hosted-classifier calls during assignment; the per-product call is the bottleneck.
   */
  private static final int CLASSIFY_PARALLELISM = 16;

  private static final String INTERRUPTED_MESSAGE = "service restarted before completion";

  private static final Logger log = LoggerFactory.getLogger(ProductClassificationService.class);

  private final FeatureTextExtractor featureTexts;
  private final ProductCategoryRepository categories;
  private final ProductCategoryAssignmentRepository assignments;
  private final ClassificationRunRepository runs;
  private final ClassificationClusterRepository clusters;
  private final ClassificationProjectionRepository projections;
  private final EmbeddingClient embedding;
  private final ProductClassifier classifier;
  private final ClusterNamer namer;
  private final CategorySuggestionRepository suggestions;
  private final NamingProperties namingProperties;
  private final EmbeddingProperties embeddingProperties;
  private final JevProperties jevProperties;
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
      ClusterNamer namer,
      CategorySuggestionRepository suggestions,
      NamingProperties namingProperties,
      EmbeddingProperties embeddingProperties,
      JevProperties jevProperties,
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
        namer,
        suggestions,
        namingProperties,
        embeddingProperties,
        jevProperties,
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
      ClusterNamer namer,
      CategorySuggestionRepository suggestions,
      NamingProperties namingProperties,
      EmbeddingProperties embeddingProperties,
      JevProperties jevProperties,
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
    this.namer = namer;
    this.suggestions = suggestions;
    this.namingProperties = namingProperties;
    this.embeddingProperties = embeddingProperties;
    this.jevProperties = jevProperties;
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

    try {
      requireEnabled(run.kind());
      Map<String, FeatureTextExtractor.Candidate> candidates = featureTexts.candidates();
      List<CategoryOption> options =
          categories.findAllByOrderByGroupNameAscDisplayOrderAscNameAsc().stream()
              .map(c -> new CategoryOption(c.id(), c.name(), c.description()))
              .toList();
      var corrections =
          assignments.findAll().stream()
              .filter(a -> a.source() == AssignmentSource.MANUAL && a.categoryId() != null)
              .sorted(Comparator.comparing(ProductCategoryAssignment::productNameKey))
              .limit(100)
              .map(
                  a ->
                      Map.of(
                          "product", a.productNameKey(), "categoryId", a.categoryId().toString()))
              .toList();
      String context =
          ClassificationContext.TEXT
              + "\nVerified manual corrections (data, up to 100):\n"
              + json(corrections);
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("businessContext", context);
      snapshot.put("candidates", candidates);
      snapshot.put("categoryOptions", options);
      snapshot.put("classifier", "jev-latest");
      snapshot.put("embedding", "all-MiniLM-L6-v2; item evidence only; no shared history prefix");
      snapshot.put("mode", "PREVIEW_ONLY");
      run.snapshot(json(snapshot), null);
      runs.save(run);
      int productCount;
      if (run.kind() == ClassificationRunKind.CLUSTER) {
        Clustering clustering = cluster(run, candidates);
        nameSuggestions(run, clustering, context);
        productCount = clustering.products().size();
      } else if (run.kind() == ClassificationRunKind.ASSIGN) {
        productCount = assign(run, candidates, options, context);
      } else { // FULL
        Clustering clustering = cluster(run, candidates);
        nameSuggestions(run, clustering, context);
        productCount = clustering.products().size();
        assign(run, candidates, options, context);
      }
      run.markSucceeded(productCount, clock.instant());
    } catch (RuntimeException e) {
      run.markFailed(failureMessage(e), clock.instant());
    }
    runs.save(run);
  }

  /**
   * Rejects a run whose services are not enabled, so a run against an un-deployed or un-warmed
   * sidecar fails fast with an operator-facing message recorded in {@code error_message} rather
   * than timing out deep in the HTTP clients. {@code CLUSTER}/{@code FULL} need the self-hosted
   * embedding service; {@code ASSIGN}/{@code FULL} need the hosted Jev classifier.
   */
  private void requireEnabled(ClassificationRunKind kind) {
    if ((kind == ClassificationRunKind.CLUSTER || kind == ClassificationRunKind.FULL)
        && !embeddingProperties.enabled()) {
      throw new EmbeddingException(
          "Product embedding classification is disabled; set CLASSIFICATION_EMBEDDING_ENABLED=true"
              + " after deploying and warming the product-classify service");
    }
    if ((kind == ClassificationRunKind.ASSIGN || kind == ClassificationRunKind.FULL)
        && !jevProperties.enabled()) {
      throw new JevException(
          "Product Jev classification is disabled; set CLASSIFICATION_JEV_ENABLED=true and"
              + " CLASSIFICATION_JEV_API_KEY");
    }
  }

  /** The recent runs, newest first, capped at {@link #RUN_LIST_LIMIT}. */
  public List<ClassificationRunView> listRuns() {
    return runs.findAllByOrderByStartedAtDesc().stream()
        .limit(RUN_LIST_LIMIT)
        .map(
            run ->
                new ClassificationRunView(
                    run.id(),
                    run.kind().name(),
                    run.status().name(),
                    run.productCount(),
                    run.startedAt(),
                    run.finishedAt(),
                    run.errorMessage()))
        .toList();
  }

  /**
   * Flips any run stranded in {@code RUNNING} by a previous shutdown to {@code FAILED}, mirroring
   * {@code ForecastService.failInterruptedRuns}. An uncaught {@link Error} (e.g. an {@code
   * OutOfMemoryError}) in {@link #execute} would otherwise leave a run running forever.
   */
  @PostConstruct
  void failInterruptedRuns() {
    for (ClassificationRun run : runs.findByStatus(ClassificationRunStatus.RUNNING)) {
      run.markFailed(INTERRUPTED_MESSAGE, clock.instant());
      runs.save(run);
    }
  }

  /**
   * Embeds and clusters the current feature texts, persisting the dendrogram nodes and the 2-D
   * projection. Returns the products, their feature texts and the flat-cut label per product, so
   * the naming step can summarise the same clusters without re-reading the DB.
   */
  private Clustering cluster(
      ClassificationRun run, Map<String, FeatureTextExtractor.Candidate> candidates) {
    Map<String, String> texts = new LinkedHashMap<>();
    candidates.forEach((key, candidate) -> texts.put(key, candidate.text()));
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
              size,
              node.id()));
    }
    for (int i = 0; i < result.projection().size(); i++) {
      Point2d point = result.projection().get(i);
      projections.save(
          new ClassificationProjection(run.id(), products.get(i), point.x(), point.y()));
    }
    return new Clustering(products, texts, result.labels());
  }

  /**
   * Best-effort naming: after clustering, asks the {@link ClusterNamer} for a draft taxonomy and
   * persists it to {@code category_suggestion}. A missing/disabled namer, or one that throws,
   * leaves the draft empty and never fails the run.
   */
  private void nameSuggestions(ClassificationRun run, Clustering clustering, String context) {
    if (!namingProperties.enabled()) {
      return;
    }
    try {
      List<ClusterSummary> summaries = summarize(clustering);
      List<SuggestedCategory> named = namer.suggest(summaries, context);
      run.namingSnapshot(
          json(
              Map.of(
                  "prompt",
                  OpenAiClusterNamer.schemaPrompt(context),
                  "clusters",
                  summaries,
                  "suggestions",
                  named)));
      runs.save(run);
      suggestions.deleteAll();
      int order = 0;
      for (SuggestedCategory s : named) {
        suggestions.save(
            CategorySuggestion.create(run.id(), s.name(), s.groupName(), order++, s.hint()));
      }
    } catch (RuntimeException e) {
      log.warn("Category naming failed; continuing without suggestions", e);
    }
  }

  /** Groups products by flat-cut label and emits one capped summary per cluster, label order. */
  private List<ClusterSummary> summarize(Clustering clustering) {
    Map<Integer, List<String>> byLabel = new TreeMap<>();
    for (int i = 0; i < clustering.products().size(); i++) {
      byLabel
          .computeIfAbsent(clustering.flatLabels().get(i), k -> new ArrayList<>())
          .add(clustering.texts().get(clustering.products().get(i)));
    }
    List<ClusterSummary> result = new ArrayList<>();
    int index = 0;
    for (Map.Entry<Integer, List<String>> entry : byLabel.entrySet()) {
      List<String> texts = entry.getValue();
      List<String> sample =
          new ArrayList<>(
              IntStream.range(0, Math.min(texts.size(), NAMING_SAMPLE_LIMIT))
                  .mapToObj(
                      i ->
                          texts.get(i * texts.size() / Math.min(texts.size(), NAMING_SAMPLE_LIMIT)))
                  .toList());
      result.add(new ClusterSummary(index++, sample, texts.size()));
    }
    return result;
  }

  /**
   * The in-memory clustering output the naming step needs: products, their texts, flat-cut labels.
   */
  private record Clustering(
      List<String> products, Map<String, String> texts, List<Integer> flatLabels) {}

  /** A run saves proposals only. Approval uses the existing permission-gated manual move. */
  private int assign(
      ClassificationRun run,
      Map<String, FeatureTextExtractor.Candidate> candidates,
      List<CategoryOption> options,
      String context) {
    Map<String, String> texts = new LinkedHashMap<>();
    List<Map<String, Object>> proposed = new ArrayList<>();
    for (var entry : candidates.entrySet()) {
      String key = entry.getKey();
      var existing = assignments.findById(key).orElse(null);
      String hold =
          existing != null && existing.source() == AssignmentSource.MANUAL
              ? "MANUAL_PRESERVED"
              : entry.getValue().ambiguous()
                  ? "AMBIGUOUS_IDENTITY_OR_RECIPE"
                  : options.isEmpty() ? "NO_TAXONOMY" : null;
      if (hold != null) proposed.add(Map.of("product", key, "status", hold));
      else texts.put(key, entry.getValue().text());
    }
    List<String> products = new ArrayList<>(texts.keySet());
    List<ProductClassification> classifications =
        classifyInParallel(products, texts, options, context);
    for (int i = 0; i < products.size(); i++) {
      ProductClassification classification = classifications.get(i);
      var existing = assignments.findById(products.get(i)).orElse(null);
      Map<String, Object> proposal = new LinkedHashMap<>();
      proposal.put("product", products.get(i));
      proposal.put("previousCategoryId", existing == null ? null : existing.categoryId());
      proposal.put("categoryId", classification.categoryId());
      proposal.put("probabilities", classification.probabilities());
      proposal.put("confidence", classification.confidence());
      proposal.put("responseModel", classification.responseModel());
      proposal.put("status", classification.categoryId() == null ? "UNCLASSIFIED" : "NEEDS_REVIEW");
      proposed.add(proposal);
    }
    run.snapshot(run.contextSnapshot(), json(proposed));
    runs.save(run);
    return candidates.size();
  }

  public ClassificationPreview preview(UUID id) {
    ClassificationRun run =
        runs.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Unknown classification run"));
    return new ClassificationPreview(
        run.id(), run.contextSnapshot(), run.proposalSnapshot(), run.namingSnapshot());
  }

  private static String json(Object value) {
    try {
      return JsonMapper.builder().findAndAddModules().build().writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Cannot retain classification evidence", e);
    }
  }

  /** Classifies the products concurrently with a bounded pool, preserving input order. */
  private List<ProductClassification> classifyInParallel(
      List<String> products,
      Map<String, String> texts,
      List<CategoryOption> options,
      String context) {
    ExecutorService pool = Executors.newFixedThreadPool(CLASSIFY_PARALLELISM);
    try {
      List<Future<ProductClassification>> futures = new ArrayList<>(products.size());
      for (String key : products) {
        String text = texts.get(key);
        futures.add(
            pool.submit(
                () ->
                    classifier.classify(new ProductClassificationRequest(text, options, context))));
      }
      List<ProductClassification> results = new ArrayList<>(products.size());
      for (Future<ProductClassification> future : futures) {
        results.add(awaitClassification(future));
      }
      return results;
    } finally {
      pool.shutdown();
    }
  }

  private static ProductClassification awaitClassification(Future<ProductClassification> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JevException("Interrupted while classifying products", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new JevException("Classifying a product failed", cause);
    }
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
