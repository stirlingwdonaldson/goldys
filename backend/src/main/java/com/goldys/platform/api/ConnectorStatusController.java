package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.ingestion.IngestionRunSummary;
import com.goldys.platform.ingestion.IngestionService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connector run status, from the ingestion ledger's latest run per source. */
@RestController
@RequestMapping("/api")
public class ConnectorStatusController {
  private static final ResourceKey RESOURCE = new ResourceKey("connectors");

  private final IngestionService ingestion;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public ConnectorStatusController(
      IngestionService ingestion, CurrentUserService currentUser, PermissionService permissions) {
    this.ingestion = ingestion;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/connectors")
  List<ConnectorStatusDto> connectors(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
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
