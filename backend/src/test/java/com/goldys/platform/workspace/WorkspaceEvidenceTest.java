package com.goldys.platform.workspace;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class WorkspaceEvidenceTest {
  final ObjectMapper json = new ObjectMapper();
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final WorkspaceEvidence evidence = new WorkspaceEvidence(jdbc, json);
  final UUID job = UUID.randomUUID();
  final UUID id = UUID.randomUUID();

  ObjectNode details() {
    return json.createObjectNode().put("evidenceId", id.toString()).put("value", 12.5);
  }

  ObjectNode snapshot(ObjectNode details) {
    var message = json.createObjectNode().put("role", "toolResult").put("toolName", "goldys_test");
    message.set("details", details);
    message.set(
        "content",
        json.createArrayNode()
            .add(json.createObjectNode().put("type", "text").put("text", details.toString())));
    return json.createObjectNode()
        .set(
            "entries",
            json.createArrayNode()
                .add(json.createObjectNode().put("id", "result").set("message", message)));
  }

  @SuppressWarnings("unchecked")
  void bind(ObjectNode details) {
    when(jdbc.query(anyString(), any(RowMapper.class), eq(id), eq(job), eq("goldys_test")))
        .thenReturn(List.of(WorkspaceEvidence.digest(details)));
  }

  @Test
  void bindingSurvivesObjectOrderAndDecimalScaleButNotValueChanges() throws Exception {
    var first = json.readTree("{\"b\":[1.00,0.010],\"a\":{\"c\":2},\"evidenceId\":\"first\"}");
    var reordered = json.readTree("{\"a\":{\"c\":2.0},\"b\":[1,1e-2],\"evidenceId\":\"second\"}");
    assertThat(WorkspaceEvidence.digest(first)).isEqualTo(WorkspaceEvidence.digest(reordered));
    assertThat(WorkspaceEvidence.digest(first))
        .isNotEqualTo(WorkspaceEvidence.digest(json.readTree("{\"a\":{\"c\":2},\"b\":[1,0]}")));
  }

  @Test
  void validatesBothModelTextAndBrowserDetails() {
    var details = details();
    bind(details);
    var saved = snapshot(details);
    evidence.validate(job, null, saved);
    ((ObjectNode) saved.at("/entries/0/message/content/0")).put("text", "{\"value\":999}");
    assertThatThrownBy(() -> evidence.validate(job, null, saved))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> evidence.validate(job, null, snapshot(details.deepCopy().put("value", 999))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cannotBorrowEvidenceFromAnotherJobOrTool() {
    var details = details();
    bind(details);
    assertThatThrownBy(() -> evidence.validate(UUID.randomUUID(), null, snapshot(details)))
        .isInstanceOf(IllegalArgumentException.class);
    var saved = snapshot(details);
    ((ObjectNode) saved.at("/entries/0/message")).put("toolName", "goldys_other");
    assertThatThrownBy(() -> evidence.validate(job, null, saved))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unchangedLegacyEntriesRemainUnknownButCannotBeRelabelledOrAltered() {
    var details = json.createObjectNode().set("widget", json.createObjectNode().put("value", 1));
    var saved = snapshot((ObjectNode) details);
    evidence.validate(job, saved, saved.deepCopy());
    verifyNoInteractions(jdbc);
    assertThatThrownBy(() -> evidence.validate(job, null, saved))
        .isInstanceOf(IllegalArgumentException.class);
    var altered = saved.deepCopy();
    ((ObjectNode) altered.at("/entries/0/message/details/widget")).put("value", 2);
    assertThatThrownBy(() -> evidence.validate(job, saved, altered))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
