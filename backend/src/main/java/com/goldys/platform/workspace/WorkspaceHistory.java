package com.goldys.platform.workspace;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.reporting.ToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * A disposable display projection, never model context or an independently persisted transcript.
 */
final class WorkspaceHistory {
  record Message(String id, String role, String text, ToolResult reporting) {}

  private WorkspaceHistory() {}

  static List<Message> project(JsonNode snapshot, ObjectMapper json) {
    if (snapshot == null) return List.of();
    var entries = new HashMap<String, JsonNode>();
    for (JsonNode entry : snapshot.path("entries")) {
      if (!"session".equals(entry.path("type").asText()))
        entries.put(entry.path("id").asText(), entry);
    }
    var messages = new ArrayList<Message>();
    var visited = new HashSet<String>();
    JsonNode leaf = snapshot.path("leafId");
    while (leaf.isTextual()) {
      String id = leaf.asText();
      JsonNode entry = entries.get(id);
      if (entry == null || !visited.add(id))
        throw new IllegalStateException("Invalid stored Pi branch");
      Message message = projectMessage(id, entry, json);
      if (message != null) messages.add(message);
      leaf = entry.path("parentId");
    }
    Collections.reverse(messages);
    return List.copyOf(messages);
  }

  private static Message projectMessage(String id, JsonNode entry, ObjectMapper json) {
    if (!"message".equals(entry.path("type").asText())) return null;
    JsonNode message = entry.path("message");
    String role = message.path("role").asText();
    if ("toolResult".equals(role)) return reporting(id, message, json);
    if (!"user".equals(role) && !"assistant".equals(role)) return null;
    String text = text(message.path("content"));
    return text.isEmpty() ? null : new Message(id, role, text, null);
  }

  private static Message reporting(String id, JsonNode message, ObjectMapper json) {
    JsonNode details = message.path("details");
    if ("NOT_PERMITTED".equals(details.path("status").asText()))
      return new Message(id, "tool", "Not permitted to access this reporting result.", null);
    if (message.path("isError").asBoolean()
        || details.path("widget").path("schemaVersion").asInt() != 2)
      return new Message(id, "tool", "Reporting result unavailable or incomplete.", null);
    try {
      ToolResult result =
          json.readerFor(ToolResult.class)
              .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .readValue(details);
      return new Message(id, "tool", "", result);
    } catch (java.io.IOException | IllegalArgumentException invalid) {
      return new Message(id, "tool", "Reporting result unavailable or incomplete.", null);
    }
  }

  private static String text(JsonNode content) {
    if (content.isTextual()) return content.asText();
    var parts = new ArrayList<String>();
    if (content.isArray()) {
      for (JsonNode block : content) {
        if ("text".equals(block.path("type").asText()) && block.path("text").isTextual())
          parts.add(block.path("text").asText());
      }
    }
    return String.join("\n", parts);
  }
}
