package com.goldys.platform.conversational;

import com.goldys.platform.auth.UserRole;
import reactor.core.publisher.Flux;

/**
 * Runs a conversation over the fixed tool set for a given role, streaming {@link
 * ConversationEvent}s. The controller depends only on this port, so a future agent-harness
 * implementation can replace it without touching tools or the controller.
 */
public interface AssistantService {
  Flux<ConversationEvent> stream(ChatRequest request, UserRole role);
}
