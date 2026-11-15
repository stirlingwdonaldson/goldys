package com.goldys.platform.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldys.platform.auth.AccessDeniedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Private service protocol, never a browser capability or general Pi RPC endpoint. */
@RestController
@RequestMapping("/api/workspace-worker")
@ConditionalOnProperty(name = "app.workspace.enabled", havingValue = "true")
public class WorkspaceWorkerController {
  private final WorkspaceJobs jobs;
  private final WorkspaceReporting reporting;
  private final byte[] token;

  public WorkspaceWorkerController(
      WorkspaceJobs jobs,
      WorkspaceReporting reporting,
      @Value("${app.workspace.worker-token:}") String token) {
    this.jobs = jobs;
    this.reporting = reporting;
    this.token = token.getBytes(StandardCharsets.UTF_8);
  }

  private void authenticate(String supplied) {
    if (token.length < 32
        || supplied == null
        || !MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.UTF_8))) {
      throw AccessDeniedException.forResource("workspace.worker");
    }
  }

  record Lease(@NotNull UUID fence) {}

  record Publication(
      @NotNull UUID fence, @Min(0) long revision, @NotNull JsonNode snapshot, boolean completed) {}

  record ReportingRequest(
      @NotNull UUID fence,
      @NotNull com.goldys.platform.reporting.ToolId tool,
      @NotNull JsonNode arguments) {}

  @PostMapping("/{id}/tools")
  java.util.List<WorkspaceReporting.ToolSpec> tools(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody Lease lease) {
    authenticate(token);
    return reporting.tools(id, lease.fence());
  }

  @PostMapping("/{id}/reporting")
  com.goldys.platform.reporting.ToolResult reporting(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody ReportingRequest request) {
    authenticate(token);
    return reporting.execute(id, request.fence(), request.tool(), request.arguments());
  }

  @PostMapping("/claim")
  WorkspaceJobs.Claim claim(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token) {
    authenticate(token);
    return jobs.claim();
  }

  @PostMapping("/{id}/heartbeat")
  void heartbeat(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody Lease lease) {
    authenticate(token);
    jobs.heartbeat(id, lease.fence());
  }

  @PostMapping("/{id}/snapshot")
  void publish(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody Publication publication) {
    authenticate(token);
    jobs.publish(
        id,
        publication.fence(),
        publication.revision(),
        publication.snapshot(),
        publication.completed());
  }
}
