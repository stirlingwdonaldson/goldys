package com.goldys.platform.conversational;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One message in a conversation thread: the {@code role} ({@code user}/{@code assistant}), the text
 * {@code content}, and — for assistant messages — the optional {@code tool_trace} captured from the
 * answer's {@link AnswerPayload.TraceEntry} provenance list.
 */
@Entity
@Table(name = "conversation_message")
public class ConversationMessage {
  @Id private UUID id;

  @Column(name = "thread_id", nullable = false)
  private UUID threadId;

  @Column(name = "role", nullable = false, length = 16)
  private String role;

  @Column(name = "content", nullable = false)
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_trace", columnDefinition = "jsonb")
  private List<AnswerPayload.TraceEntry> toolTrace;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ConversationMessage() {}

  private ConversationMessage(
      UUID id,
      UUID threadId,
      String role,
      String content,
      List<AnswerPayload.TraceEntry> toolTrace,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.threadId = Objects.requireNonNull(threadId, "threadId");
    this.role = Objects.requireNonNull(role, "role");
    this.content = Objects.requireNonNull(content, "content");
    this.toolTrace = toolTrace == null ? List.of() : List.copyOf(toolTrace);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static ConversationMessage create(
      UUID threadId,
      String role,
      String content,
      List<AnswerPayload.TraceEntry> toolTrace,
      Instant createdAt) {
    return new ConversationMessage(
        UUID.randomUUID(), threadId, role, content, toolTrace, createdAt);
  }

  public UUID id() {
    return id;
  }

  public UUID threadId() {
    return threadId;
  }

  public String role() {
    return role;
  }

  public String content() {
    return content;
  }

  public List<AnswerPayload.TraceEntry> toolTrace() {
    return toolTrace;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
