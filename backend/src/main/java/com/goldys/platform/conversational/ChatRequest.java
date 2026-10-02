package com.goldys.platform.conversational;

import java.util.List;
import java.util.Objects;

/** The request body of a chat turn: the in-session message history. */
public record ChatRequest(List<ChatMessage> messages) {

  public ChatRequest {
    messages = messages == null ? List.of() : List.copyOf(messages);
  }

  /** A single message in the conversation. Only user and assistant roles are accepted. */
  public record ChatMessage(String role, String content) {
    public ChatMessage {
      Objects.requireNonNull(role, "role");
      Objects.requireNonNull(content, "content");
      if (!role.equals("user") && !role.equals("assistant")) {
        throw new IllegalArgumentException("Unknown message role: " + role);
      }
    }
  }
}
