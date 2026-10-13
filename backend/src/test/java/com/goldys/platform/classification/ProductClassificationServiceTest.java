package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.canonical.CanonicalSaleRecipeLinkQuery;
import com.goldys.platform.canonical.SaleItemIdentityView;
import com.goldys.platform.canonical.SaleRecipeLinkView;
import com.goldys.platform.semantic.ClassificationRunView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

/** Orchestration coverage: embed → cluster → persist, and classify → assign (or review). */
class ProductClassificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

  private final CanonicalProductSalesQuery productSales = mock(CanonicalProductSalesQuery.class);
  private final CanonicalSaleItemQuery saleItems = mock(CanonicalSaleItemQuery.class);
  private final CanonicalSaleRecipeLinkQuery recipeLinks = mock(CanonicalSaleRecipeLinkQuery.class);

  private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
  private final ProductCategoryAssignmentRepository assignments =
      mock(ProductCategoryAssignmentRepository.class);
  private final ClassificationRunRepository runs = mock(ClassificationRunRepository.class);
  private final ClassificationClusterRepository clusters =
      mock(ClassificationClusterRepository.class);
  private final ClassificationProjectionRepository projections =
      mock(ClassificationProjectionRepository.class);

  private final Map<UUID, ClassificationRun> runStore = new HashMap<>();
  private final Map<String, ProductCategoryAssignment> assignmentStore = new HashMap<>();
  private final List<ClassificationCluster> savedClusters = new ArrayList<>();
  private final List<ClassificationProjection> savedProjections = new ArrayList<>();

  // A no-op executor: submit persists RUNNING and returns before the async execute runs, so the
  // RUNNING state is observable. The tests call execute(...) directly, synchronously.
  private final TaskExecutor executor = task -> {};

  private final FeatureTextExtractor extractor =
      new FeatureTextExtractor(productSales, saleItems, recipeLinks);

  @BeforeEach
  void setUp() {
    runStore.clear();
    assignmentStore.clear();
    savedClusters.clear();
    savedProjections.clear();

    when(runs.save(any()))
        .thenAnswer(
            inv -> {
              ClassificationRun run = inv.getArgument(0);
              runStore.put(run.id(), run);
              return run;
            });
    when(runs.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(runStore.get(inv.getArgument(0))));
    when(runs.findByStatus(any()))
        .thenAnswer(
            inv ->
                runStore.values().stream().filter(r -> r.status() == inv.getArgument(0)).toList());

    when(assignments.save(any()))
        .thenAnswer(
            inv -> {
              ProductCategoryAssignment a = inv.getArgument(0);
              assignmentStore.put(a.productNameKey(), a);
              return a;
            });
    when(assignments.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(assignmentStore.get(inv.getArgument(0))));

    when(clusters.save(any()))
        .thenAnswer(
            inv -> {
              ClassificationCluster c = inv.getArgument(0);
              savedClusters.add(c);
              return c;
            });
    when(projections.save(any()))
        .thenAnswer(
            inv -> {
              ClassificationProjection p = inv.getArgument(0);
              savedProjections.add(p);
              return p;
            });
  }

  private ProductClassificationService service(
      EmbeddingClient embedding, ProductClassifier classifier) {
    return service(
        embedding,
        classifier,
        new EmbeddingProperties(true, "http://localhost:8002"),
        new JevProperties(true, "https://api.typesafe.ai", ""));
  }

  private ProductClassificationService service(
      EmbeddingClient embedding,
      ProductClassifier classifier,
      EmbeddingProperties embeddingProperties,
      JevProperties jevProperties) {
    return new ProductClassificationService(
        extractor,
        categories,
        assignments,
        runs,
        clusters,
        projections,
        embedding,
        classifier,
        embeddingProperties,
        jevProperties,
        executor,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void clustering_persists_nodes_and_projection() {
    when(productSales.distinctProductNameKeys()).thenReturn(List.of("beef burger", "kids fish"));
    when(saleItems.currentSaleItemIdentities())
        .thenReturn(
            List.of(
                new SaleItemIdentityView("Beef Burger", "Burgers"),
                new SaleItemIdentityView("Lemonade", "Drinks")));
    when(recipeLinks.currentLinks())
        .thenReturn(
            List.of(
                new SaleRecipeLinkView(
                    1, "Classic Beef Burger", "Mains", "Beef Burger", true, 1, null)));

    ProductClassificationService svc =
        service(new FakeEmbeddingClient(), new FakeClassifier(req -> null));
    svc.runClustering();
    ClassificationRun run = runStore.values().iterator().next();
    svc.execute(run.id());

    assertThat(run.status()).isEqualTo(ClassificationRunStatus.SUCCEEDED);
    assertThat(run.kind()).isEqualTo(ClassificationRunKind.CLUSTER);
    assertThat(run.productCount()).isEqualTo(3);

    List<ClassificationCluster> leaves =
        savedClusters.stream().filter(ClassificationCluster::isLeaf).toList();
    assertThat(leaves).hasSize(3);
    assertThat(leaves).allSatisfy(n -> assertThat(n.mergeHeight()).isNull());
    assertThat(leaves).allSatisfy(n -> assertThat(n.size()).isNull());
    assertThat(leaves)
        .extracting(ClassificationCluster::productNameKey)
        .containsExactlyInAnyOrder("beef burger", "kids fish", "lemonade");

    List<ClassificationCluster> internal = savedClusters.stream().filter(n -> !n.isLeaf()).toList();
    assertThat(internal).hasSize(2);
    assertThat(internal).allSatisfy(n -> assertThat(n.productNameKey()).isNull());
    assertThat(internal).allSatisfy(n -> assertThat(n.mergeHeight()).isNotNull());
    assertThat(internal).allSatisfy(n -> assertThat(n.size()).isNotNull());

    // The node's integer id (merge order / leaf position) is preserved for deterministic render.
    assertThat(savedClusters)
        .extracting(ClassificationCluster::nodeOrder)
        .containsExactlyInAnyOrder(0, 1, 2, 3, 4);

    assertThat(savedProjections).hasSize(3);
    assertThat(savedProjections)
        .anySatisfy(
            p -> {
              assertThat(p.productNameKey()).isEqualTo("beef burger");
              assertThat(p.x()).isEqualTo(0.1);
              assertThat(p.y()).isEqualTo(0.2);
            });
    assertThat(savedProjections)
        .anySatisfy(
            p -> {
              assertThat(p.productNameKey()).isEqualTo("lemonade");
              assertThat(p.x()).isEqualTo(0.5);
              assertThat(p.y()).isEqualTo(0.6);
            });
  }

  @Test
  void feature_text_falls_back_to_name_only() {
    when(productSales.distinctProductNameKeys()).thenReturn(List.of("house red wine"));
    when(saleItems.currentSaleItemIdentities())
        .thenReturn(List.of(new SaleItemIdentityView("Kids Fish & Chips", "Mains")));
    when(recipeLinks.currentLinks()).thenReturn(List.of());

    Map<String, String> texts = extractor.featureTexts();

    assertThat(texts).containsKey("house red wine");
    assertThat(texts.get("house red wine")).isEqualTo("house red wine");
    assertThat(texts.get("house red wine")).isNotBlank();
  }

  @Test
  void assignment_skips_manual_overrides() {
    when(productSales.distinctProductNameKeys())
        .thenReturn(List.of("beef burger", "house salad", "kids fish"));
    when(saleItems.currentSaleItemIdentities()).thenReturn(List.of());
    when(recipeLinks.currentLinks()).thenReturn(List.of());

    ProductCategory category = ProductCategory.create("Mains", "Food", 0, "Main courses");
    when(categories.findAllByOrderByGroupNameAscDisplayOrderAscNameAsc())
        .thenReturn(List.of(category));

    ProductCategoryAssignment unassigned = ProductCategoryAssignment.create("beef burger", NOW);
    ProductCategoryAssignment manual = ProductCategoryAssignment.create("kids fish", NOW);
    manual.markAssigned(category.id(), null, AssignmentSource.MANUAL, NOW);
    ProductCategoryAssignment jev = ProductCategoryAssignment.create("house salad", NOW);
    jev.markAssigned(category.id(), new BigDecimal("0.9"), AssignmentSource.JEV, NOW);
    assignmentStore.put("beef burger", unassigned);
    assignmentStore.put("kids fish", manual);
    assignmentStore.put("house salad", jev);

    FakeClassifier classifier =
        new FakeClassifier(
            req ->
                new ProductClassification(
                    category.id(),
                    Map.of(category.id(), new BigDecimal("0.9")),
                    new BigDecimal("0.9")));
    ProductClassificationService svc = service(new FakeEmbeddingClient(), classifier);
    svc.runAssignment();
    ClassificationRun run = runStore.values().iterator().next();
    svc.execute(run.id());

    assertThat(classifier.requests())
        .extracting(ProductClassificationRequest::featureText)
        .containsExactly("beef burger", "house salad");
    assertThat(manual.source()).isEqualTo(AssignmentSource.MANUAL);
    assertThat(manual.status()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(assignmentStore.get("beef burger").status()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(assignmentStore.get("beef burger").source()).isEqualTo(AssignmentSource.JEV);
  }

  @Test
  void low_confidence_marks_needs_review() {
    when(productSales.distinctProductNameKeys()).thenReturn(List.of("beef burger"));
    when(saleItems.currentSaleItemIdentities()).thenReturn(List.of());
    when(recipeLinks.currentLinks()).thenReturn(List.of());

    ProductCategory category = ProductCategory.create("Mains", "Food", 0, "Main courses");
    when(categories.findAllByOrderByGroupNameAscDisplayOrderAscNameAsc())
        .thenReturn(List.of(category));

    FakeClassifier classifier =
        new FakeClassifier(
            req ->
                new ProductClassification(
                    category.id(),
                    Map.of(category.id(), new BigDecimal("0.2")),
                    new BigDecimal("0.2")));
    ProductClassificationService svc = service(new FakeEmbeddingClient(), classifier);
    svc.runAssignment();
    ClassificationRun run = runStore.values().iterator().next();
    svc.execute(run.id());

    ProductCategoryAssignment saved = assignmentStore.get("beef burger");
    assertThat(saved.status()).isEqualTo(AssignmentStatus.NEEDS_REVIEW);
    assertThat(saved.source()).isEqualTo(AssignmentSource.JEV);
    assertThat(saved.confidence()).isEqualByComparingTo("0.2");
    assertThat(saved.categoryId()).isEqualTo(category.id());
  }

  @Test
  void empty_taxonomy_marks_all_unassigned() {
    when(productSales.distinctProductNameKeys()).thenReturn(List.of("beef burger", "kids fish"));
    when(saleItems.currentSaleItemIdentities()).thenReturn(List.of());
    when(recipeLinks.currentLinks()).thenReturn(List.of());
    when(categories.findAllByOrderByGroupNameAscDisplayOrderAscNameAsc()).thenReturn(List.of());

    ProductCategory category = ProductCategory.create("Mains", "Food", 0, "Main courses");
    ProductCategoryAssignment jev = ProductCategoryAssignment.create("beef burger", NOW);
    jev.markAssigned(category.id(), new BigDecimal("0.9"), AssignmentSource.JEV, NOW);
    assignmentStore.put("beef burger", jev);

    FakeClassifier classifier = new FakeClassifier(req -> null);
    ProductClassificationService svc = service(new FakeEmbeddingClient(), classifier);
    svc.runAssignment();
    ClassificationRun run = runStore.values().iterator().next();
    svc.execute(run.id());

    assertThat(classifier.requests()).isEmpty();
    assertThat(assignmentStore.get("beef burger").status()).isEqualTo(AssignmentStatus.UNASSIGNED);
    assertThat(assignmentStore.get("beef burger").categoryId()).isNull();
    assertThat(assignmentStore.get("kids fish").status()).isEqualTo(AssignmentStatus.UNASSIGNED);
    assertThat(run.status()).isEqualTo(ClassificationRunStatus.SUCCEEDED);
    assertThat(run.productCount()).isEqualTo(2);
  }

  @Test
  void classifier_failure_marks_run_failed() {
    when(productSales.distinctProductNameKeys()).thenReturn(List.of("beef burger"));
    when(saleItems.currentSaleItemIdentities()).thenReturn(List.of());
    when(recipeLinks.currentLinks()).thenReturn(List.of());

    ProductCategory category = ProductCategory.create("Mains", "Food", 0, "Main courses");
    when(categories.findAllByOrderByGroupNameAscDisplayOrderAscNameAsc())
        .thenReturn(List.of(category));
    ProductCategoryAssignment unassigned = ProductCategoryAssignment.create("beef burger", NOW);
    assignmentStore.put("beef burger", unassigned);

    FakeClassifier classifier =
        new FakeClassifier(
            req -> {
              throw new JevException("Jev service returned HTTP 500");
            });
    ProductClassificationService svc = service(new FakeEmbeddingClient(), classifier);
    svc.runAssignment();
    ClassificationRun run = runStore.values().iterator().next();
    svc.execute(run.id());

    assertThat(run.status()).isEqualTo(ClassificationRunStatus.FAILED);
    assertThat(run.errorMessage()).isEqualTo("Jev service returned HTTP 500");
    verify(assignments, never()).save(any());
    assertThat(assignmentStore.get("beef burger").status()).isEqualTo(AssignmentStatus.UNASSIGNED);
  }

  @Test
  void disabled_embedding_fails_cluster_run_with_operator_message() {
    ProductClassificationService svc =
        service(
            new FakeEmbeddingClient(),
            new FakeClassifier(req -> null),
            new EmbeddingProperties(false, "http://localhost:8002"),
            new JevProperties(true, "https://api.typesafe.ai", ""));

    svc.runClustering();
    ClassificationRun run = runStore.values().iterator().next();
    svc.execute(run.id());

    assertThat(run.status()).isEqualTo(ClassificationRunStatus.FAILED);
    assertThat(run.errorMessage()).contains("CLASSIFICATION_EMBEDDING_ENABLED=true");
  }

  @Test
  void disabled_jev_fails_assign_run_with_operator_message() {
    ProductClassificationService svc =
        service(
            new FakeEmbeddingClient(),
            new FakeClassifier(req -> null),
            new EmbeddingProperties(true, "http://localhost:8002"),
            new JevProperties(false, "https://api.typesafe.ai", ""));

    svc.runAssignment();
    ClassificationRun run = runStore.values().iterator().next();
    svc.execute(run.id());

    assertThat(run.status()).isEqualTo(ClassificationRunStatus.FAILED);
    assertThat(run.errorMessage()).contains("CLASSIFICATION_JEV_ENABLED=true");
  }

  @Test
  void listRuns_maps_most_recent_first() {
    ClassificationRun older =
        ClassificationRun.create(ClassificationRunKind.CLUSTER, NOW.minusSeconds(60));
    older.markSucceeded(3, NOW.minusSeconds(30));
    ClassificationRun newer = ClassificationRun.create(ClassificationRunKind.FULL, NOW);
    newer.markFailed("Jev service returned HTTP 500", NOW.plusSeconds(10));
    when(runs.findAllByOrderByStartedAtDesc()).thenReturn(List.of(newer, older));

    List<ClassificationRunView> result =
        service(new FakeEmbeddingClient(), new FakeClassifier(req -> null)).listRuns();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).kind()).isEqualTo("FULL");
    assertThat(result.get(0).status()).isEqualTo("FAILED");
    assertThat(result.get(0).productCount()).isNull();
    assertThat(result.get(0).errorMessage()).isEqualTo("Jev service returned HTTP 500");
    assertThat(result.get(1).kind()).isEqualTo("CLUSTER");
    assertThat(result.get(1).status()).isEqualTo("SUCCEEDED");
    assertThat(result.get(1).productCount()).isEqualTo(3);
  }

  @Test
  void listRuns_caps_to_limit() {
    List<ClassificationRun> many = new ArrayList<>();
    for (int i = 0; i < ProductClassificationService.RUN_LIST_LIMIT + 5; i++) {
      many.add(ClassificationRun.create(ClassificationRunKind.CLUSTER, NOW.plusSeconds(i)));
    }
    when(runs.findAllByOrderByStartedAtDesc()).thenReturn(many);

    List<ClassificationRunView> result =
        service(new FakeEmbeddingClient(), new FakeClassifier(req -> null)).listRuns();

    assertThat(result).hasSize(ProductClassificationService.RUN_LIST_LIMIT);
  }

  @Test
  void failInterruptedRuns_marks_running_failed() {
    ClassificationRun run = ClassificationRun.create(ClassificationRunKind.CLUSTER, NOW);
    runStore.put(run.id(), run);

    service(new FakeEmbeddingClient(), new FakeClassifier(req -> null)).failInterruptedRuns();

    assertThat(run.status()).isEqualTo(ClassificationRunStatus.FAILED);
    assertThat(run.errorMessage()).isEqualTo("service restarted before completion");
    assertThat(run.finishedAt()).isEqualTo(NOW);
  }

  /** A fake classifier that records each request it sees and delegates to a handler. */
  private static final class FakeClassifier implements ProductClassifier {
    private final List<ProductClassificationRequest> requests = new ArrayList<>();
    private final Function<ProductClassificationRequest, ProductClassification> handler;

    FakeClassifier(Function<ProductClassificationRequest, ProductClassification> handler) {
      this.handler = handler;
    }

    @Override
    public ProductClassification classify(ProductClassificationRequest request) {
      requests.add(request);
      return handler.apply(request);
    }

    List<ProductClassificationRequest> requests() {
      return requests;
    }
  }

  /** A fake embedding client returning one trivial vector per text and a fixed cluster result. */
  private static final class FakeEmbeddingClient implements EmbeddingClient {
    @Override
    public List<float[]> embed(List<String> texts) {
      List<float[]> vectors = new ArrayList<>(texts.size());
      for (int i = 0; i < texts.size(); i++) {
        vectors.add(new float[] {i});
      }
      return vectors;
    }

    @Override
    public ClusterResult cluster(List<float[]> vectors) {
      // Three leaves ("beef burger", "kids fish", "lemonade" in candidate order) merged into two
      // internal nodes; labels are ignored for v1.
      return new ClusterResult(
          List.of(
              new ClusterNode(0, 4, true, 0, 0.0, 1),
              new ClusterNode(1, 4, true, 1, 0.0, 1),
              new ClusterNode(2, 3, true, 2, 0.0, 1),
              new ClusterNode(4, 3, false, null, 0.8, 2),
              new ClusterNode(3, null, false, null, 1.5, 3)),
          List.of(0, 0, 1),
          List.of(new Point2d(0.1, 0.2), new Point2d(0.3, 0.4), new Point2d(0.5, 0.6)));
    }
  }
}
