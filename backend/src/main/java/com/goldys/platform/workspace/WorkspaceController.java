package com.goldys.platform.workspace;

import com.goldys.platform.auth.AccountUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace/jobs")
@ConditionalOnProperty(name = "app.workspace.enabled", havingValue = "true")
public class WorkspaceController {
  private final WorkspaceJobs jobs;

  public WorkspaceController(WorkspaceJobs jobs) {
    this.jobs = jobs;
  }

  record Submission(
      @NotNull UUID threadId,
      @NotNull UUID requestId,
      @NotBlank @Size(max = 16000) String prompt) {}

  @PostMapping
  WorkspaceJobs.Job submit(
      @AuthenticationPrincipal AccountUserDetails user, @Valid @RequestBody Submission body) {
    return jobs.submit(user.id(), body.threadId(), body.requestId(), body.prompt());
  }

  @GetMapping("/{id}")
  WorkspaceJobs.Job read(@AuthenticationPrincipal AccountUserDetails user, @PathVariable UUID id) {
    return jobs.read(user.id(), id);
  }

  @PostMapping("/{id}/stop")
  WorkspaceJobs.Job stop(@AuthenticationPrincipal AccountUserDetails user, @PathVariable UUID id) {
    return jobs.stop(user.id(), id);
  }
}
