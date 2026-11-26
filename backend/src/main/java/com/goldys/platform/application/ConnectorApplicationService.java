package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.connectors.lightspeed.LightspeedProductCatalogIngestService;
import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
import com.goldys.platform.connectors.opentable.OpenTableMenuItemsCsvIngestService;
import com.goldys.platform.connectors.opentable.OpenTableReservationsCsvIngestService;
import com.goldys.platform.connectors.opentable.OpenTableReviewsCsvIngestService;
import com.goldys.platform.ingestion.CtbSftpPull;
import com.goldys.platform.ingestion.FailureDetail;
import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** The connectors screen's read model and run/upload actions. */
@Service
public class ConnectorApplicationService {
  private static final ResourceKey RESOURCE = new ResourceKey("connectors");

  /**
   * Phase 1 sources in a stable display order. Sources with no ingestion run yet are reported as
   * {@code never_run} so the connectors screen can offer their actions (e.g. the OpenTable CSV
   * upload) before the first data arrives.
   */
  private static final List<ConnectorStatus> KNOWN_SOURCES =
      List.of(
          new ConnectorStatus(
              "LIGHTSPEED", "lightspeed-insights", null, "never_run", 0, null, false),
          new ConnectorStatus(
              "LIGHTSPEED", "lightspeed-product-catalog", null, "never_run", 0, null, false),
          new ConnectorStatus("CTB", "ctb-revenue", null, "never_run", 0, null, false),
          // The thin 8-column drop is deprecated in favour of the three rich exports below; it
          // remains registered (raw-only) so its historical run status still surfaces.
          new ConnectorStatus("OPENTABLE", "opentable-csv-drop", null, "never_run", 0, null, false),
          new ConnectorStatus(
              "OPENTABLE", "opentable-reservations", null, "never_run", 0, null, false),
          new ConnectorStatus("OPENTABLE", "opentable-reviews", null, "never_run", 0, null, false),
          new ConnectorStatus(
              "OPENTABLE", "opentable-menu-items", null, "never_run", 0, null, false),
          new ConnectorStatus("TANDA", "tanda-api", null, "never_run", 0, null, false));

  private final IngestionService ingestion;
  private final OpenTableCsvIngestService openTableCsvIngest;
  private final OpenTableReservationsCsvIngestService openTableReservationsIngest;
  private final OpenTableReviewsCsvIngestService openTableReviewsIngest;
  private final OpenTableMenuItemsCsvIngestService openTableMenuItemsIngest;
  private final LightspeedProductCatalogIngestService lightspeedProductCatalogIngest;
  private final PermissionService permissions;
  private final ObjectProvider<CtbSftpPull> sftpPull;

  public ConnectorApplicationService(
      IngestionService ingestion,
      OpenTableCsvIngestService openTableCsvIngest,
      OpenTableReservationsCsvIngestService openTableReservationsIngest,
      OpenTableReviewsCsvIngestService openTableReviewsIngest,
      OpenTableMenuItemsCsvIngestService openTableMenuItemsIngest,
      LightspeedProductCatalogIngestService lightspeedProductCatalogIngest,
      PermissionService permissions,
      ObjectProvider<CtbSftpPull> sftpPull) {
    this.ingestion = ingestion;
    this.openTableCsvIngest = openTableCsvIngest;
    this.openTableReservationsIngest = openTableReservationsIngest;
    this.openTableReviewsIngest = openTableReviewsIngest;
    this.openTableMenuItemsIngest = openTableMenuItemsIngest;
    this.lightspeedProductCatalogIngest = lightspeedProductCatalogIngest;
    this.permissions = permissions;
    this.sftpPull = sftpPull;
  }

  public List<ConnectorStatus> connectors(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    Map<String, ConnectorStatus> latest = new LinkedHashMap<>();
    for (ConnectorStatus known : KNOWN_SOURCES) {
      latest.put(
          known.connectorName(),
          new ConnectorStatus(
              known.source(),
              known.connectorName(),
              null,
              "never_run",
              0,
              null,
              ingestion.isRunnable(known.source())));
    }
    for (IngestionRunSummary run : ingestion.connectorHealthRuns()) {
      latest.put(run.connectorName(), toStatus(run));
    }
    return List.copyOf(latest.values());
  }

  public ConnectorStatus run(UserRole role, String source) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    return toStatus(ingestion.runConnector(source));
  }

  /** Ingests a manually-exported GuestCenter reservations CSV for the OpenTable source. */
  public void uploadOpenTableCsv(UserRole role, byte[] bytes) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    openTableCsvIngest.ingest(bytes);
  }

  /** Ingests the rich GuestCenter reservations export for the OpenTable source. */
  public void uploadOpenTableReservationsCsv(UserRole role, byte[] bytes) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    openTableReservationsIngest.ingest(bytes);
  }

  /** Ingests the GuestCenter reviews export for the OpenTable source. */
  public void uploadOpenTableReviewsCsv(UserRole role, byte[] bytes) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    openTableReviewsIngest.ingest(bytes);
  }

  /** Ingests the GuestCenter menu export for the OpenTable source. */
  public void uploadOpenTableMenuItemsCsv(UserRole role, byte[] bytes) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    openTableMenuItemsIngest.ingest(bytes);
  }

  /** Ingests the Lightspeed product-catalog CSV drop. */
  public void uploadLightspeedProductsCsv(UserRole role, byte[] bytes) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    lightspeedProductCatalogIngest.ingest(bytes);
  }

  /** Triggers the CTB SFTP drop pull on demand (requires {@code ctb.sftp.enabled=true}). */
  public void runSftpPull(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    CtbSftpPull pull = sftpPull.getIfAvailable();
    if (pull == null) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "SFTP ingestion is not enabled (ctb.sftp.enabled=false)");
    }
    pull.pullNow();
  }

  private ConnectorStatus toStatus(IngestionRunSummary run) {
    return new ConnectorStatus(
        run.sourceSystem(),
        run.connectorName(),
        run.startedAt().toString(),
        status(run.status()),
        failureCount(run.failureSummary()),
        failure(run.failure()),
        ingestion.isRunnable(run.sourceSystem()));
  }

  private static Failure failure(FailureDetail f) {
    return f == null ? null : new Failure(f.type(), f.message(), f.at().toString(), f.stackTrace());
  }

  private static String status(String s) {
    return switch (s) {
      case "SUCCESS" -> "success";
      case "PARTIAL" -> "partial";
      case "FAILED" -> "failed";
      case "NO_NEW_DATA" -> "no_new_data";
      case "RUNNING" -> "running";
      default ->
          "failed"; // a RUNNING run that never completed (e.g. a crash) is a failure to finish
    };
  }

  private static int failureCount(String summary) {
    if (summary == null || summary.isBlank()) {
      return 0;
    }
    try {
      var count =
          java.util.regex.Pattern.compile("^(\\d+) failure\\(s\\) recorded$").matcher(summary);
      return count.matches() ? Integer.parseInt(count.group(1)) : 1;
    } catch (NumberFormatException e) {
      return 1;
    }
  }

  public record ConnectorStatus(
      String source,
      String connectorName,
      String lastRunAt,
      String status,
      int failureCount,
      Failure failure,
      boolean runnable) {}

  public record Failure(String type, String message, String at, String stackTrace) {}
}
