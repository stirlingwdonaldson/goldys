package com.goldys.platform.conversational;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted Ask Goldy's conversation thread: the user-owned grouping that messages belong to. The
 * title and summary are derived surface metadata; {@link #lastMessageAt()} is the ordering hook for
 * "most recently active" listing.
 */
@Entity
@Table(name = "conversation_thread")
public class ConversationThread {
  @Id private UUID id;

  @Column(name = "user_account_id", nullable = false)
  private UUID userAccountId;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "summary")
  private String summary;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_message_at")
  private Instant lastMessageAt;

  protected ConversationThread() {}

  private ConversationThread(
      UUID id,
      UUID userAccountId,
      String title,
      String summary,
      Instant createdAt,
      Instant updatedAt,
      Instant lastMessageAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.userAccountId = Objects.requireNonNull(userAccountId, "userAccountId");
    this.title = Objects.requireNonNull(title, "title");
    this.summary = summary;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.lastMessageAt = lastMessageAt;
  }

  public static ConversationThread create(
      UUID userAccountId, String title, String summary, Instant now) {
    return new ConversationThread(UUID.randomUUID(), userAccountId, title, summary, now, now, null);
  }

  public UUID id() {
    return id;
  }

  public UUID userAccountId() {
    return userAccountId;
  }

  public String title() {
    return title;
  }

  public String summary() {
    return summary;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Instant lastMessageAt() {
    return lastMessageAt;
  }

  /** Renames this thread; used by the owner via the conversation service. */
  public void rename(String title) {
    this.title = Objects.requireNonNull(title, "title");
  }

  /** Replaces the running summary produced by compaction. Never deletes stored messages. */
  public void setSummary(String summary) {
    this.summary = summary;
  }

  /** Bumps {@link #updatedAt()} and {@link #lastMessageAt()} to {@code now}. */
  public void touch(Instant now) {
    this.updatedAt = Objects.requireNonNull(now, "now");
    this.lastMessageAt = now;
  }
}
