package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Connector run status, from the ingestion ledger's latest run per source. */
@RestController
@RequestMapping("/api")
public class ConnectorStatusController {
  private static final ResourceKey RESOURCE = new ResourceKey("connectors");

  /**
   * Phase 1 sources in a stable display order. Sources with no ingestion run yet are reported as
   * {@code never_run} so the connectors screen can offer their actions (e.g. the OpenTable CSV
   * upload) before the first data arrives.
   */
  private static final List<ConnectorStatusDto> KNOWN_SOURCES =
      List.of(
          new ConnectorStatusDto("LIGHTSPEED", "lightspeed-insights", null, "never_run", 0),
          new ConnectorStatusDto("CTB", "ctb-revenue", null, "never_run", 0),
          new ConnectorStatusDto("OPENTABLE", "opentable-csv-drop", null, "never_run", 0),
          new ConnectorStatusDto("DEPUTY", "deputy-api", null, "never_run", 0));

  private final IngestionService ingestion;
  private final OpenTableCsvIngestService openTableCsvIngest;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public ConnectorStatusController(
      IngestionService ingestion,
      OpenTableCsvIngestService openTableCsvIngest,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.ingestion = ingestion;
    this.openTableCsvIngest = openTableCsvIngest;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/connectors")
  List<ConnectorStatusDto> connectors(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    Map<String, ConnectorStatusDto> latest = new LinkedHashMap<>();
    for (IngestionRunSummary run : ingestion.latestRunPerSource()) {
      latest.putIfAbsent(run.sourceSystem(), toDto(run));
    }
    return KNOWN_SOURCES.stream().map(known -> latest.getOrDefault(known.source(), known)).toList();
  }

  @PostMapping("/connectors/{source}/run")
  ConnectorStatusDto run(
      @PathVariable String source, @AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.WRITE);
    return toDto(ingestion.runConnector(source));
  }

  /** Uploads a manually-exported GuestCenter reservations CSV for the OpenTable source. */
  @PostMapping(
      value = "/connectors/opentable/upload",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<Void> uploadOpenTableCsv(
      @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.WRITE);
    openTableCsvIngest.ingest(readBytes(file));
    return ResponseEntity.noContent().build();
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Could not read the uploaded file", e);
    }
  }

  private ConnectorStatusDto toDto(IngestionRunSummary run) {
    return new ConnectorStatusDto(
        run.sourceSystem(),
        run.connectorName(),
        run.startedAt().toString(),
        status(run.status()),
        failureCount(run.failureSummary()));
  }

  private static String status(String s) {
    return switch (s) {
      case "SUCCESS" -> "success";
      case "PARTIAL" -> "partial";
      case "FAILED" -> "failed";
      case "NO_NEW_DATA" -> "no_new_data";
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

  record ConnectorStatusDto(
      String source, String connectorName, String lastRunAt, String status, int failureCount) {}
}
