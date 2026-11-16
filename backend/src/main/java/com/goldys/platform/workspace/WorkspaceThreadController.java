package com.goldys.platform.workspace;

import com.goldys.platform.auth.AccountUserDetails;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace/threads")
@ConditionalOnProperty(name = "app.workspace.enabled", havingValue = "true")
public class WorkspaceThreadController {
  private final WorkspaceThreads threads;

  public WorkspaceThreadController(WorkspaceThreads threads) {
    this.threads = threads;
  }

  @DeleteMapping("/{id}")
  void delete(@AuthenticationPrincipal AccountUserDetails user, @PathVariable UUID id) {
    threads.delete(user.id(), id);
  }

  @GetMapping
  WorkspaceThreads.Page list(
      @AuthenticationPrincipal AccountUserDetails user,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "25") int limit) {
    return threads.list(user.id(), before, limit);
  }
}
