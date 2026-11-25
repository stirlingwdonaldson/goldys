package com.goldys.platform.workspace;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.conversational.DashboardDraft;
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
  record Message(
      String id,
      String role,
      String text,
      ToolResult reporting,
      DashboardDraft draft,
      String provider,
      String model) {
    Message(String id, String role, String text, ToolResult reporting, DashboardDraft draft) {
      this(id, role, text, reporting, draft, null, null);
    }
  }

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
    if ("toolResult".equals(role)) {
      if (java.util.Set.of(
              "goldys_list_files",
              "goldys_extract_files",
              "goldys_analyze_python",
              "goldys_public_research",
              "goldys_export_sales_dataset")
          .contains(message.path("toolName").asText())) return exploration(id, message);
      return reporting(id, message, json);
    }
    if (!"user".equals(role) && !"assistant".equals(role)) return null;
    String text = text(message.path("content"));
    return text.isEmpty()
        ? null
        : new Message(
            id,
            role,
            text,
            null,
            null,
            message.path("provider").asText(null),
            message.path("model").asText(null));
  }

  private static Message exploration(String id, JsonNode message) {
    var details = message.path("details");
    String status =
        message.path("isError").asBoolean() ? "FAILED" : details.path("status").asText("COMPLETED");
    StringBuilder display =
        new StringBuilder("Exploratory tool: ")
            .append(status)
            .append(". Not a governed reporting result.");
    for (String field : List.of("notice", "extraction", "stdout")) {
      String value = details.path(field).asText("");
      if (!value.isBlank())
        display
            .append("\n")
            .append(value.substring(0, Math.min(value.length(), 8000)))
            .append(
                value.length() > 8000
                    ? "\n[Display shortened; consult the artifact or ask for a bounded extract.]"
                    : "");
    }
    if (details.has("sources")) {
      display
          .append("\nPublic sources retrieved ")
          .append(details.path("retrievedAt").asText("unknown"));
      for (JsonNode source : details.path("sources"))
        display
            .append("\n")
            .append(source.path("title").asText())
            .append(" — ")
            .append(source.path("url").asText());
    }
    return new Message(id, "tool", display.toString(), null, null);
  }

  private static Message reporting(String id, JsonNode message, ObjectMapper json) {
    JsonNode details = message.path("details");
    if ("NOT_PERMITTED".equals(details.path("status").asText()))
      return new Message(id, "tool", "Not permitted to access this reporting result.", null, null);
    if (message.path("isError").asBoolean()
        || details.path("widget").path("schemaVersion").asInt() != 2)
      return new Message(id, "tool", "Reporting result unavailable or incomplete.", null, null);
    try {
      ToolResult result =
          json.readerFor(ToolResult.class)
              .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .readValue(details);
      DashboardDraft draft =
          details.hasNonNull("draft")
              ? json.treeToValue(details.path("draft"), DashboardDraft.class)
              : null;
      return new Message(id, "tool", "", result, draft);
    } catch (java.io.IOException | IllegalArgumentException invalid) {
      return new Message(id, "tool", "Reporting result unavailable or incomplete.", null, null);
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
