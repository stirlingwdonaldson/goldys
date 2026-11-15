package com.goldys.platform.workspace;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkspaceHistoryTest {
  @Test
  void onlyActiveBranchUserAndAssistantTextReachBrowser() throws Exception {
    var snapshot =
        new ObjectMapper()
            .readTree(
                """
        {"leafId":"answer","entries":[
          {"type":"session","id":"session"},
          {"type":"message","id":"question","parentId":null,"message":{"role":"user","content":"Question"}},
          {"type":"message","id":"other","parentId":"question","message":{"role":"assistant","content":"Inactive branch"}},
          {"type":"compaction","id":"summary","parentId":"question","summary":"Private internal summary"},
          {"type":"message","id":"tool","parentId":"summary","message":{"role":"toolResult","content":[{"type":"text","text":"Internal tool payload"}]}},
          {"type":"message","id":"answer","parentId":"tool","message":{"role":"assistant","content":[{"type":"thinking","thinking":"Private reasoning"},{"type":"text","text":"Visible answer"},{"type":"toolCall","arguments":{"secret":"internal"}}]}}
        ]}
        """);
    assertThat(WorkspaceHistory.project(snapshot, new ObjectMapper()))
        .extracting(WorkspaceHistory.Message::text)
        .containsExactly(
            "Question", "Reporting result unavailable or incomplete.", "Visible answer");
    assertThat(WorkspaceHistory.project(snapshot, new ObjectMapper()))
        .extracting(WorkspaceHistory.Message::role)
        .containsExactly("user", "tool", "assistant");
    ((com.fasterxml.jackson.databind.node.ObjectNode) snapshot).putNull("leafId");
    assertThat(WorkspaceHistory.project(snapshot, new ObjectMapper())).isEmpty();
  }

  @Test
  void malformedBranchFailsClosed() throws Exception {
    var snapshot =
        new ObjectMapper()
            .readTree(
                """
        {"leafId":"loop","entries":[{"type":"message","id":"loop","parentId":"loop"}]}
        """);
    assertThatThrownBy(() -> WorkspaceHistory.project(snapshot, new ObjectMapper()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void governedWidgetKeepsMissingValuesNoticesAndQueryConfiguration() throws Exception {
    var json = new ObjectMapper();
    var snapshot =
        json.readTree(
            """
        {"leafId":"tool","entries":[{"type":"message","id":"tool","parentId":null,
          "message":{"role":"toolResult","details":{
            "widget":{"type":"stat","schemaVersion":2,"id":"net","title":"Net sales","value":null},
            "notices":["No source coverage"],"provenance":[],"relatedMetrics":[]}}}]}
        """);
    var result = WorkspaceHistory.project(snapshot, json).getFirst().reporting();
    assertThat(result).isNotNull();
    assertThat(((com.goldys.platform.widget.StatWidgetSpec) result.widget()).value()).isNull();
    assertThat(result.notices()).containsExactly("No source coverage");
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            snapshot.path("entries").get(0).path("message").path("details").path("widget"))
        .put("type", "javascript");
    assertThat(WorkspaceHistory.project(snapshot, json).getFirst().reporting()).isNull();
  }

  @Test
  void deniedToolResultIsExplicitRatherThanMissingData() throws Exception {
    var json = new ObjectMapper();
    var snapshot =
        json.readTree(
            """
        {"leafId":"tool","entries":[{"type":"message","id":"tool","parentId":null,
          "message":{"role":"toolResult","details":{"status":"NOT_PERMITTED"}}}]}
        """);
    var message = WorkspaceHistory.project(snapshot, json).getFirst();
    assertThat(message.text()).contains("Not permitted");
    assertThat(message.reporting()).isNull();
  }
}
