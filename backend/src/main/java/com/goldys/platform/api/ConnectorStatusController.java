package com.goldys.platform.api;

import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connector run status, from the ingestion ledger's latest run per source. */
@RestController
@RequestMapping("/api")
public class ConnectorStatusController {
  private final IngestionService ingestion;

  public ConnectorStatusController(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @GetMapping("/connectors")
  List<ConnectorStatusDto> connectors() {
    return ingestion.latestRunPerSource().stream().map(this::toDto).toList();
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
      default -> "never_run";
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
