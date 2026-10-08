package com.goldys.platform.conversational;

import com.goldys.platform.auth.UserRole;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

/**
 * Runs a conversation over the fixed tool set for a given role, streaming {@link
 * ConversationEvent}s. The {@code context} is the server-authoritative model message list built by
 * {@link ConversationService#contextFor}; the controller depends only on this port, so a future
 * agent-harness implementation can replace it without touching tools or the controller.
 */
public interface AssistantService {
  Flux<ConversationEvent> stream(List<Message> context, UserRole role);
}
