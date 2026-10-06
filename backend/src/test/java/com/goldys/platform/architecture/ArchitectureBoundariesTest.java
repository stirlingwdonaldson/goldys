package com.goldys.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
}
