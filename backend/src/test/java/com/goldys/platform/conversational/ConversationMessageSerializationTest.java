package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goldys.platform.conversational.ConversationService.ThreadView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins that {@link ConversationMessage} serializes its fields by name. Jackson does not auto-detect
 * record-style ({@code foo()}) accessors on a plain {@code @Entity}, so without
 * {@code @JsonProperty} each message in a {@link ThreadView} would serialize as an empty {@code {}}
 * object.
 */
class ConversationMessageSerializationTest {

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void threadViewSerializesMessageContentRoleAndToolTrace() throws Exception {
    UUID threadId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-07T12:00:00Z");
    ConversationMessage message =
        ConversationMessage.create(
            threadId,
            "assistant",
            "Food cost is 32% this week.",
            List.of(
                new AnswerPayload.TraceEntry("GET_INVENTORY_SUMMARY", "Food cost %", List.of())),
            createdAt);
    ThreadView view = new ThreadView(threadId, "Inventory review", List.of(message));

    JsonNode root = mapper.readTree(mapper.writeValueAsString(view));
    JsonNode serialized = root.get("messages").get(0);

    assertThat(serialized.has("id")).isTrue();
    assertThat(serialized.has("threadId")).isTrue();
    assertThat(serialized.get("role").asText()).isEqualTo("assistant");
    assertThat(serialized.get("content").asText()).isEqualTo("Food cost is 32% this week.");
    assertThat(serialized.has("toolTrace")).isTrue();
    assertThat(serialized.get("toolTrace").get(0).get("tool").asText())
        .isEqualTo("GET_INVENTORY_SUMMARY");
    assertThat(serialized.has("createdAt")).isTrue();
  }
}
