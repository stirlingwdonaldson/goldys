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
  private final WorkspaceFiles files;
  private final WorkspaceDatasets datasets;

  public WorkspaceWorkerController(
      WorkspaceJobs jobs,
      WorkspaceReporting reporting,
      WorkspaceFiles files,
      WorkspaceDatasets datasets,
      @Value("${app.workspace.worker-token:}") String token) {
    this.jobs = jobs;
    this.reporting = reporting;
    this.files = files;
    this.datasets = datasets;
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
  WorkspaceReporting.Result reporting(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody ReportingRequest request) {
    authenticate(token);
    return reporting.execute(id, request.fence(), request.tool(), request.arguments());
  }

  record ArtifactRequest(@NotNull UUID fence, @NotNull String name, @NotNull byte[] bytes) {}

  record InputRequest(@NotNull UUID fence, java.util.List<UUID> fileIds) {}

  @PostMapping("/{id}/files")
  java.util.List<WorkspaceFiles.FileBytes> files(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody InputRequest body) {
    authenticate(token);
    return files.inputs(id, body.fence(), body.fileIds());
  }

  @PostMapping("/{id}/file-list")
  java.util.List<WorkspaceFiles.FileInfo> fileList(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody Lease body) {
    authenticate(token);
    return files.workerList(id, body.fence());
  }

  @PostMapping("/{id}/artifacts")
  WorkspaceFiles.FileInfo artifact(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody ArtifactRequest body) {
    authenticate(token);
    return files.artifact(id, body.fence(), body.name(), body.bytes());
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

  @PostMapping("/{id}/settle")
  WorkspaceJobs.FollowUp settle(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody Publication publication) {
    authenticate(token);
    return jobs.settle(id, publication.fence(), publication.revision(), publication.snapshot());
  }

  record DatasetRequest(
      @NotNull UUID fence, @NotNull java.time.LocalDate from, @NotNull java.time.LocalDate to) {}

  @PostMapping("/{id}/sales-dataset")
  WorkspaceDatasets.Dataset dataset(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody DatasetRequest body) {
    authenticate(token);
    return datasets.sales(id, body.fence(), body.from(), body.to());
  }

  record Usage(
      @NotNull UUID fence,
      @NotNull UUID requestId,
      @NotNull String kind,
      @NotNull java.math.BigDecimal aud) {}

  @PostMapping("/{id}/usage")
  void usage(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody Usage body) {
    authenticate(token);
    jobs.reserveUsage(id, body.fence(), body.requestId(), body.kind(), body.aud());
  }

  record Failure(@NotNull UUID fence, @NotNull String outcome) {}

  @PostMapping("/{id}/failure")
  void fail(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody Failure failure) {
    authenticate(token);
    jobs.fail(id, failure.fence(), failure.outcome());
  }

  @PostMapping("/{id}/interrupt")
  void interrupt(
      @RequestHeader(value = "X-Workspace-Token", required = false) String token,
      @PathVariable UUID id,
      @Valid @RequestBody Lease lease) {
    authenticate(token);
    jobs.interrupt(id, lease.fence());
  }
}
