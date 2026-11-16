package com.goldys.platform.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Binds reporting labels to actual responses; never asserts lineage for prose or compaction. */
@Service
public class WorkspaceEvidence {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public WorkspaceEvidence(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  void validate(UUID job, JsonNode previous, JsonNode next) {
    Map<String, JsonNode> committed = new HashMap<>();
    if (previous != null) {
      for (JsonNode entry : previous.path("entries"))
        committed.put(entry.path("id").asText(), entry);
    }
    for (JsonNode entry : next.path("entries")) {
      // Unchanged historical entries can retain unknown provenance. They never gain a label.
      if (entry.equals(committed.get(entry.path("id").asText()))) continue;
      JsonNode message = entry.path("message");
      if (!"toolResult".equals(message.path("role").asText())) continue;
      JsonNode details = message.path("details");
      if (!details.hasNonNull("evidenceId")) {
        if (details.hasNonNull("widget") || details.hasNonNull("draft"))
          throw new IllegalArgumentException("Reporting result requires bound evidence");
        continue;
      }
      validateResult(job, message, details);
    }
  }

  private void validateResult(UUID job, JsonNode message, JsonNode details) {
    UUID id;
    try {
      id = UUID.fromString(details.path("evidenceId").asText());
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Invalid reporting evidence");
    }
    var expected =
        jdbc.query(
            "select response_sha256 from workspace_reporting_evidence where id=? and job_id=? and tool_name=? and response_sha256 is not null",
            (row, index) -> row.getBytes(1),
            id,
            job,
            message.path("toolName").asText());
    if (expected.isEmpty() || !MessageDigest.isEqual(expected.getFirst(), digest(details)))
      throw new IllegalArgumentException("Unbound reporting evidence");
    // Pi exposes content to the model and details to the browser. Both must describe the same
    // server response; binding only details would leave model-visible text unauthenticated.
    JsonNode content = message.path("content");
    if (!content.isArray()
        || content.size() != 1
        || !"text".equals(content.path(0).path("type").asText())
        || !content.path(0).path("text").isTextual())
      throw new IllegalArgumentException("Invalid reporting content");
    try {
      JsonNode text = json.readTree(content.path(0).path("text").asText());
      if (text == null
          || !details.path("evidenceId").equals(text.path("evidenceId"))
          || !MessageDigest.isEqual(digest(details), digest(text)))
        throw new IllegalArgumentException("Reporting content differs from evidence");
    } catch (java.io.IOException invalid) {
      throw new IllegalArgumentException("Invalid reporting content");
    }
  }

  static byte[] digest(JsonNode response) {
    if (!response.isObject()) throw new IllegalArgumentException("Invalid reporting response");
    ObjectNode body = response.deepCopy();
    body.remove("evidenceId");
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(normalize(body).toString().getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  // JSON object order and decimal scale change across HTTP/JSONB/JS round trips.
  private static JsonNode normalize(JsonNode value) {
    if (value.isObject()) {
      ObjectNode result = JsonNodeFactory.instance.objectNode();
      var names = new java.util.ArrayList<String>();
      value.fieldNames().forEachRemaining(names::add);
      names.stream().sorted().forEach(name -> result.set(name, normalize(value.get(name))));
      return result;
    }
    if (value.isArray()) {
      var result = JsonNodeFactory.instance.arrayNode();
      value.forEach(item -> result.add(normalize(item)));
      return result;
    }
    if (value.isNumber())
      return JsonNodeFactory.instance.numberNode(value.decimalValue().stripTrailingZeros());
    return value;
  }
}
