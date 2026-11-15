package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.workspace.WorkspaceConflict;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WorkspaceConflictResponseTest {
  @Test
  void expectedQueueRacesHaveAStableRecoverableResponse() {
    var response =
        new ApiExceptionHandler()
            .handleWorkspaceConflict(
                new WorkspaceConflict("Follow-up is no longer editable"),
                new MockHttpServletRequest());
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().code()).isEqualTo("WORKSPACE_CONFLICT");
    assertThat(response.getBody().message()).isEqualTo("Follow-up is no longer editable");
  }
}
