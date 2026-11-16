package com.goldys.platform.workspace;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.AccessDeniedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceWorkerControllerTest {
  @Test
  void everyWorkerOperationRejectsMissingOrWrongTokenBeforeServiceAccess() {
    var jobs = mock(WorkspaceJobs.class);
    var reporting = mock(WorkspaceReporting.class);
    var controller =
        new WorkspaceWorkerController(
            jobs,
            reporting,
            mock(WorkspaceFiles.class),
            mock(WorkspaceDatasets.class),
            "synthetic-token-at-least-32-characters");
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> controller.health(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.health("wrong")).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.ready(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.claim(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.claim("wrong")).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.heartbeat(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.publish(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.reporting(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.tools(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.settle(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.interrupt(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.files(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.artifact(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.fail(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.dataset(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.fileList(null, id, null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(jobs, reporting);
    controller.claim("synthetic-token-at-least-32-characters");
    verify(jobs).claim();
  }

  @Test
  void unsetOrShortConfiguredTokenFailsClosed() {
    var jobs = mock(WorkspaceJobs.class);
    var reporting = mock(WorkspaceReporting.class);
    assertThatThrownBy(
            () ->
                new WorkspaceWorkerController(
                        jobs,
                        reporting,
                        mock(WorkspaceFiles.class),
                        mock(WorkspaceDatasets.class),
                        "")
                    .claim(""))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                new WorkspaceWorkerController(
                        jobs,
                        reporting,
                        mock(WorkspaceFiles.class),
                        mock(WorkspaceDatasets.class),
                        "short")
                    .claim("short"))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(jobs, reporting);
  }
}
