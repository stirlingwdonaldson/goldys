package com.goldys.platform.workspace;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.workspace.enabled", havingValue = "true")
public class WorkspaceFileExpiry {
  private final WorkspaceFiles files;

  public WorkspaceFileExpiry(WorkspaceFiles files) {
    this.files = files;
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
  public void expire() {
    files.expire();
  }
}
