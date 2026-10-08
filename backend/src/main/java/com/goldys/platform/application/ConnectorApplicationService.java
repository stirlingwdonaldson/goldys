package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
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
          new ConnectorStatus("CTB", "ctb-revenue", null, "never_run", 0, null, false),
          new ConnectorStatus("OPENTABLE", "opentable-csv-drop", null, "never_run", 0, null, false),
          new ConnectorStatus("DEPUTY", "deputy-api", null, "never_run", 0, null, false));

  private final IngestionService ingestion;
  private final OpenTableCsvIngestService openTableCsvIngest;
  private final PermissionService permissions;
  private final ObjectProvider<CtbSftpPull> sftpPull;

  public ConnectorApplicationService(
      IngestionService ingestion,
      OpenTableCsvIngestService openTableCsvIngest,
      PermissionService permissions,
      ObjectProvider<CtbSftpPull> sftpPull) {
    this.ingestion = ingestion;
    this.openTableCsvIngest = openTableCsvIngest;
    this.permissions = permissions;
    this.sftpPull = sftpPull;
  }

  public List<ConnectorStatus> connectors(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    Map<String, ConnectorStatus> latest = new LinkedHashMap<>();
    for (IngestionRunSummary run : ingestion.latestRunPerSource()) {
      latest.putIfAbsent(run.sourceSystem(), toStatus(run));
    }
    return KNOWN_SOURCES.stream()
        .map(
            known -> {
              ConnectorStatus current = latest.getOrDefault(known.source(), known);
              return new ConnectorStatus(
                  current.source(),
                  current.connectorName(),
                  current.lastRunAt(),
                  current.status(),
                  current.failureCount(),
                  current.failure(),
                  ingestion.isRunnable(current.source()));
            })
        .toList();
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

  /** Triggers the CTB SFTP drop pull on demand (requires {@code ctb.sftp.enabled=true}). */
  public void runSftpPull(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    CtbSftpPull pull = sftpPull.getIfAvailable();
    if (pull == null) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "SFTP ingestion is not enabled (ctb.sftp.enabled=false)");
    }
    pull.pull();
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
      return Integer.parseInt(summary.replaceAll("[^0-9]", ""));
    } catch (NumberFormatException e) {
      return 0;
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
