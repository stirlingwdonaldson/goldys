package com.goldys.platform.conversational;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for conversation messages, ordered oldest-first within a thread. */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {
  List<ConversationMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}
