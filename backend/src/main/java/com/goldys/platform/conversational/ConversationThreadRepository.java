package com.goldys.platform.conversational;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for conversation threads; {@code deleteById} is inherited from {@link JpaRepository}.
 */
public interface ConversationThreadRepository extends JpaRepository<ConversationThread, UUID> {
  List<ConversationThread> findByUserAccountIdOrderByUpdatedAtDesc(UUID userAccountId);

  Optional<ConversationThread> findByIdAndUserAccountId(UUID id, UUID userAccountId);
}
