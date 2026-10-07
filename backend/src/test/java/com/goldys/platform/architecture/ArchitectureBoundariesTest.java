package com.goldys.platform.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.goldys.platform.semantic.InventoryMetricsQuery;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.ReservationMetricsQuery;
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
          .haveFullyQualifiedName("com.goldys.platform.reporting.GetLabourCostTool")
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
          .haveFullyQualifiedName("com.goldys.platform.reporting.GetFoodCostTool")
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
}
