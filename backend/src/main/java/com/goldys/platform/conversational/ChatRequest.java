package com.goldys.platform.conversational;

import java.util.Objects;
import java.util.UUID;

/**
 * The request body of a chat turn: the optional {@code threadId} to continue and the new user
 * message. Server-authoritative: the client never supplies assistant history — the server loads its
 * own history via {@link ConversationService}.
 */
public record ChatRequest(UUID threadId, String message) {

  public ChatRequest {
    Objects.requireNonNull(message, "message");
    // threadId is null for a brand-new conversation.
  }
}
