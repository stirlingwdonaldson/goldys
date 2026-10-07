package com.goldys.platform.conversational;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * The request body of a chat turn: the optional {@code threadId} to continue and the new user
 * message. Server-authoritative: the client never supplies assistant history — the server loads its
 * own history via {@link ConversationService}.
 */
public record ChatRequest(UUID threadId, @NotBlank String message) {}
