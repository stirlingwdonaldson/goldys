package com.goldys.platform.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.goldys.platform.semantic.DeletedSaleMetricsQuery;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import com.goldys.platform.semantic.InvoiceLineMetricsQuery;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.PaymentMetricsQuery;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import com.goldys.platform.semantic.RecipeIngredientMetricsQuery;
import com.goldys.platform.semantic.RecipeMetricsQuery;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReviewMetricsQuery;
import com.goldys.platform.semantic.SaleItemMetricsQuery;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Machine-checks the backend dependency direction:
 *
 * <pre>
 *   api / conversational   (delivery)
 *        ↓
 *   application            (use-case composition + authorization)
 *        ↓
 *   semantic / reconciliation / ingestion / auth   (business reads + domain + persistence)
 * </pre>
 *
 * Delivery and conversational code depend on application/semantic contracts, never directly on
 * canonical source reads or persistence internals. Reconciliation internals are free to read
 * canonical — the rule is that <em>final business consumers</em> must not derive truth from
 * canonical observations.
 */
@AnalyzeClasses(
    packages = "com.goldys.platform",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundariesTest {

  /** The semantic layer is a leaf: interfaces and metric records with no platform dependencies. */
  @ArchTest
  static final ArchRule semanticIsALeaf =
      noClasses()
          .that()
          .resideInAPackage("..semantic..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..api..",
              "..application..",
              "..reporting..",
              "..conversational..",
              "..reconciliation..",
              "..canonical..",
              "..ingestion..",
              "..auth..",
              "..connectors..",
              "..widget..",
              "..dashboard..");

  /** The metric catalogue is a leaf: no in-platform dependencies beyond the semantic interfaces. */
  @ArchTest
  static final ArchRule catalogIsALeaf =
      noClasses()
          .that()
          .resideInAPackage("..semantic.catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..api..",
              "..application..",
              "..reporting..",
              "..conversational..",
              "..reconciliation..",
              "..canonical..",
              "..ingestion..",
              "..auth..",
              "..connectors..",
              "..widget..",
              "..dashboard..");

  /** The widget contract is a leaf: schema records only, no platform dependencies. */
  @ArchTest
  static final ArchRule widgetContractIsALeaf =
      noClasses()
          .that()
          .resideInAPackage("..widget..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..api..",
              "..application..",
              "..reporting..",
              "..conversational..",
              "..reconciliation..",
              "..canonical..",
              "..ingestion..",
              "..auth..",
              "..connectors..",
              "..semantic..",
              "..dashboard..");

  /** REST controllers must not read canonical source facts directly. */
  @ArchTest
  static final ArchRule apiDoesNotReadCanonical =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..canonical..")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("com.goldys.platform.ingestion.RawLedgerQuery");

  /** Reporting tools consume the semantic layer, not canonical or reconciliation internals. */
  @ArchTest
  static final ArchRule reportingConsumesSemanticOnly =
      noClasses()
          .that()
          .resideInAPackage("..reporting..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..canonical..", "..reconciliation..");

  /** Conversational BI reaches tools + auth, never persistence. */
  @ArchTest
  static final ArchRule conversationalDoesNotReachPersistence =
      noClasses()
          .that()
          .resideInAPackage("..conversational..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..canonical..", "..reconciliation..", "..ingestion..");

  /** The reservation controller must not read canonical source facts directly. */
  @ArchTest
  static final ArchRule reservationControllerDoesNotReadCanonical =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.api.ReservationController")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..canonical..");

  /** The reservation reporting tool consumes the semantic layer, not canonical/reconciliation. */
  @ArchTest
  static final ArchRule reservationToolConsumesSemanticOnly =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.reporting.GetReservationSummaryTool")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..canonical..", "..reconciliation..");

  /** The reservation semantic interface is implemented only in the reconciliation package. */
  @ArchTest
  static final ArchRule reservationMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(ReservationMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /** The labour controller must not read canonical source facts directly. */
  @ArchTest
  static final ArchRule labourControllerDoesNotReadCanonical =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.api.LabourController")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..canonical..");

  /** The labour reporting tool consumes the semantic layer, not canonical/reconciliation. */
  @ArchTest
  static final ArchRule labourToolConsumesSemanticOnly =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.reporting.GetLabourVarianceTool")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..canonical..", "..reconciliation..");

  /** The labour semantic interface is implemented only in the reconciliation package. */
  @ArchTest
  static final ArchRule labourMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(LabourMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /** The inventory controller must not read canonical source facts directly. */
  @ArchTest
  static final ArchRule inventoryControllerDoesNotReadCanonical =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.api.InventoryController")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..canonical..");

  /** The inventory reporting tool consumes the semantic layer, not canonical/reconciliation. */
  @ArchTest
  static final ArchRule inventoryToolConsumesSemanticOnly =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.reporting.GetInventorySummaryTool")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..canonical..", "..reconciliation..");

  /** The inventory semantic interface is implemented only in the reconciliation package. */
  @ArchTest
  static final ArchRule inventoryMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(InventoryMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /**
   * The invoice line-metrics semantic interface is implemented only in the reconciliation package.
   */
  @ArchTest
  static final ArchRule invoiceLineMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(InvoiceLineMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /** The payment semantic interface is implemented only in the reconciliation package. */
  @ArchTest
  static final ArchRule paymentMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(PaymentMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /** The deleted-sale semantic interface is implemented only in the reconciliation package. */
  @ArchTest
  static final ArchRule deletedSaleMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(DeletedSaleMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /** The sale-item semantic interface is implemented only in the reconciliation package. */
  @ArchTest
  static final ArchRule saleItemMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(SaleItemMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /** The recipe controller must not read canonical source facts directly. */
  @ArchTest
  static final ArchRule recipeControllerDoesNotReadCanonical =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.api.RecipeController")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..canonical..");

  /** The recipe reporting tool consumes the semantic layer, not canonical/reconciliation. */
  @ArchTest
  static final ArchRule recipeToolConsumesSemanticOnly =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.reporting.GetRecipeCostingTool")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..canonical..", "..reconciliation..");

  /** The recipe semantic interface is implemented only in the reconciliation package. */
  @ArchTest
  static final ArchRule recipeMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(RecipeMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /** The recipe-ingredient controller must not read canonical source facts directly. */
  @ArchTest
  static final ArchRule recipeIngredientControllerDoesNotReadCanonical =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.api.RecipeIngredientController")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..canonical..");

  /**
   * The recipe-ingredient reporting tool consumes the semantic layer, not canonical/reconciliation.
   */
  @ArchTest
  static final ArchRule recipeIngredientToolConsumesSemanticOnly =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.reporting.GetRecipeIngredientsTool")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..canonical..", "..reconciliation..");

  /** The recipe-ingredient semantic interface is implemented only in the reconciliation package. */
  @ArchTest
  static final ArchRule recipeIngredientMetricsImplementedInReconciliation =
      classes()
          .that()
          .implement(RecipeIngredientMetricsQuery.class)
          .should()
          .resideInAPackage("..reconciliation..");

  /**
   * The dashboard layer is a read-side leaf: query configuration, template catalogue and its own
   * JPA repositories. It must not reach reconciliation, canonical, ingestion, or the REST `api`
   * delivery layer.
   */
  @ArchTest
  static final ArchRule dashboardLayerDoesNotReachPersistenceOrApi =
      noClasses()
          .that()
          .resideInAPackage("..dashboard..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..reconciliation..", "..canonical..", "..ingestion..", "..api..");

  /**
   * Conversational AI may reference dashboard value types (the {@code SavedWidget} / {@code
   * DashboardFilters} records) to validate a draft, but never the dashboard repositories.
   */
  @ArchTest
  static final ArchRule conversationalDoesNotReachDashboardRepositories =
      noClasses()
          .that()
          .resideInAPackage("..conversational..")
          .should()
          .dependOnClassesThat(
              resideInAPackage("..dashboard..").and(simpleNameEndingWith("Repository")));

  /**
   * The platform map is composed from catalogue metadata, config and connector health; it never
   * reads canonical facts or reconciliation internals.
   */
  @ArchTest
  static final ArchRule platformMapDoesNotReadCanonicalOrReconciliation =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.application.PlatformMapService")
          .or()
          .haveFullyQualifiedName("com.goldys.platform.api.PlatformMapController")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..canonical..", "..reconciliation..");

  /** The network graph wire records are semantic contracts, shared by every graph endpoint. */
  @ArchTest
  static final ArchRule networkGraphRecordsLiveInSemantic =
      classes()
          .that()
          .haveSimpleNameStartingWith("Network")
          .and()
          .haveSimpleNameEndingWith("Dto")
          .should()
          .resideInAPackage("..semantic..");

  /**
   * The forecasting domain is a leaf: it composes the semantic catalogue and auth with its own JPA
   * persistence, and reaches nothing else in the platform — not the reconciliation/canonical/
   * ingestion internals, the API / application delivery layers, nor the dashboard/reporting/
   * connectors/widget/config/metrics packages.
   */
  @ArchTest
  static final ArchRule forecastingIsALeaf =
      noClasses()
          .that()
          .resideInAPackage("..forecasting..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..reconciliation..",
              "..canonical..",
              "..ingestion..",
              "..api..",
              "..application..",
              "..dashboard..",
              "..reporting..",
              "..connectors..",
              "..widget..",
              "..config..",
              "..metrics..");

  /** The forecasting application service is the single bridge into the forecasting domain. */
  @ArchTest
  static final ArchRule forecastingApplicationServiceBridgesForecasting =
      classes()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.application.ForecastingApplicationService")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..forecasting..");

  /**
   * The OpenTable live view is reached only through {@code OpenTableSessionApplicationService},
   * which authorizes every call: delivery code never touches the sidecar browser or CDP directly.
   */
  @ArchTest
  static final ArchRule openTableBrowserIsReachedOnlyThroughTheApplicationService =
      noClasses()
          .that()
          .resideInAnyPackage("..api..", "..conversational..", "..reporting..", "..dashboard..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.goldys.platform.connectors.opentable.session..",
              "com.goldys.platform.connectors.opentable.cdp..");

  /** The forecasting controller must not read canonical source facts or the raw ledger directly. */
  @ArchTest
  static final ArchRule forecastingControllerDoesNotReadCanonical =
      noClasses()
          .that()
          .haveFullyQualifiedName("com.goldys.platform.api.ForecastingController")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..canonical..")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("com.goldys.platform.ingestion.RawLedgerQuery");

  /**
   * The classification domain is a leaf: it composes {@code canonical} (feature extraction), {@code
   * semantic} (implements {@code ProductCategoryMetricsQuery} and returns the category DTOs) and
   * {@code auth} (permission checks) with its own JPA persistence, and reaches nothing else in the
   * platform — not the API / application delivery layers, nor reporting / conversational /
   * reconciliation / ingestion / connectors / dashboard / widget / forecasting / config / metrics.
   */
  @ArchTest
  static final ArchRule classificationDoesNotReachHigherPackages =
      noClasses()
          .that()
          .resideInAPackage("..classification..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..api..",
              "..application..",
              "..reporting..",
              "..conversational..",
              "..reconciliation..",
              "..ingestion..",
              "..connectors..",
              "..dashboard..",
              "..widget..",
              "..forecasting..",
              "..config..",
              "..metrics..");

  /** REST delivery reaches the classification domain only through the application service. */
  @ArchTest
  static final ArchRule apiDoesNotReachClassification =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..classification..");

  /** Reporting tools consume the semantic contracts, never the classification implementation. */
  @ArchTest
  static final ArchRule reportingDoesNotReachClassification =
      noClasses()
          .that()
          .resideInAPackage("..reporting..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..classification..");

  /** The product-category semantic interface is implemented only in the classification package. */
  @ArchTest
  static final ArchRule productCategoryMetricsImplementedInClassification =
      classes()
          .that()
          .implement(ProductCategoryMetricsQuery.class)
          .should()
          .resideInAPackage("..classification..");

  /** The review semantic interface is implemented only in the classification package. */
  @ArchTest
  static final ArchRule reviewMetricsQueryImplementedInClassification =
      classes()
          .that()
          .implement(ReviewMetricsQuery.class)
          .should()
          .resideInAPackage("..classification..");
}
