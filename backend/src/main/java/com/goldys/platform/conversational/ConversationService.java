package com.goldys.platform.conversational;

import com.goldys.platform.auth.AccessDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestration-agnostic persistence + context manager for Ask Goldy's conversations. It is the
 * bridge between the controller and {@link ChatClientAssistantService}: it loads/creates a thread,
 * appends the user message, builds the model context (running summary + recent turns), persists the
 * assistant message and tool trace, and owns thread CRUD.
 *
 * <p>Server-authoritative: the only client input is the new user message; history is always rebuilt
 * from the store, never trusted from the client. Every method is scoped to the authenticated {@code
 * userId} and throws {@link AccessDeniedException} for another user's thread.
 */
@Service
public class ConversationService {

  private static final int SUMMARIZE_THRESHOLD_TOKENS = 4000;
  private static final int KEEP_RECENT = 6;
  private static final int MAX_TITLE_LENGTH = 200;
  private static final int PREVIEW_LENGTH = 120;
  private static final Clock CLOCK = Clock.systemUTC();

  private final ConversationThreadRepository threads;
  private final ConversationMessageRepository messages;
  private final ObjectProvider<ChatModel> model;

  public ConversationService(
      ConversationThreadRepository threads,
      ConversationMessageRepository messages,
      ObjectProvider<ChatModel> model) {
    this.threads = threads;
    this.messages = messages;
    this.model = model;
  }

  /**
   * Loads the thread (or creates one when {@code threadId} is {@code null}), appends the user
   * message, and returns the resolved thread id together with the model-ready message list: the
   * running summary (if any) followed by the most recent turns ending in the new user message.
   * Compacts older turns into {@link ConversationThread#summary()} when the thread exceeds {@link
   * #KEEP_RECENT} turns or {@link #SUMMARIZE_THRESHOLD_TOKENS} tokens; messages are never deleted
   * from the store.
   *
   * <p>The resolved {@code threadId} is returned so the caller can later persist the assistant
   * message against the same (possibly newly-created) thread via {@link #complete}.
   */
  @Transactional
  public PreparedTurn contextFor(UUID userId, UUID threadId, String message) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(message, "message");
    Instant now = CLOCK.instant();

    List<ConversationMessage> prior = List.of();
    ConversationThread thread;
    if (threadId == null) {
      thread = threads.save(ConversationThread.create(userId, titleFor(message), null, now));
    } else {
      thread = requireThread(userId, threadId);
      prior = messages.findByThreadIdOrderByCreatedAtAsc(threadId);
    }

    ConversationMessage userMessage =
        messages.save(ConversationMessage.create(thread.id(), "user", message, List.of(), now));
    thread.touch(now);
    threads.save(thread);

    List<ConversationMessage> history = new ArrayList<>(prior.size() + 1);
    history.addAll(prior);
    history.add(userMessage);
    return new PreparedTurn(thread.id(), buildContext(thread, history));
  }

  /** Persists the assistant message and its tool trace, bumping the thread's timestamps. */
  @Transactional
  public void complete(
      UUID userId, UUID threadId, String assistantText, List<AnswerPayload.TraceEntry> trace) {
    Objects.requireNonNull(assistantText, "assistantText");
    ConversationThread thread = requireThread(userId, threadId);
    Instant now = CLOCK.instant();
    messages.save(ConversationMessage.create(thread.id(), "assistant", assistantText, trace, now));
    thread.touch(now);
    threads.save(thread);
  }

  /** Lists the user's threads, newest first, with a preview of the last message. */
  public List<ThreadSummary> list(UUID userId) {
    return threads.findByUserAccountIdOrderByUpdatedAtDesc(userId).stream()
        .map(this::toSummary)
        .toList();
  }

  /** Loads a thread with its full message history for the owning user. */
  public ThreadView get(UUID userId, UUID threadId) {
    ConversationThread thread = requireThread(userId, threadId);
    return new ThreadView(
        thread.id(), thread.title(), messages.findByThreadIdOrderByCreatedAtAsc(threadId));
  }

  /** Renames a thread owned by the user. */
  @Transactional
  public void rename(UUID userId, UUID threadId, String title) {
    Objects.requireNonNull(title, "title");
    ConversationThread thread = requireThread(userId, threadId);
    thread.rename(title);
    threads.save(thread);
  }

  /** Deletes a thread (and its messages, cascaded) owned by the user. */
  @Transactional
  public void delete(UUID userId, UUID threadId) {
    requireThread(userId, threadId);
    threads.deleteById(threadId);
  }

  private List<Message> buildContext(ConversationThread thread, List<ConversationMessage> history) {
    String summary = thread.summary();
    List<ConversationMessage> recent = history;

    boolean overCount = history.size() > KEEP_RECENT;
    boolean overTokens = estimateTokens(history) > SUMMARIZE_THRESHOLD_TOKENS;
    if (overCount || (overTokens && history.size() > 1)) {
      // Keep the most recent KEEP_RECENT turns, but always fold at least the oldest message.
      int keep = Math.min(KEEP_RECENT, history.size() - 1);
      int compactFrom = history.size() - keep;
      List<ConversationMessage> older = history.subList(0, compactFrom);
      String newSummary = summarize(summary, older);
      // Only drop the folded turns from the returned context once a new summary actually captures
      // them; otherwise send the full history so nothing is silently lost. Stored messages are
      // never deleted either way.
      if (newSummary != null && !newSummary.isBlank()) {
        summary = newSummary;
        thread.setSummary(summary);
        threads.save(thread);
        recent = history.subList(compactFrom, history.size());
      }
    }

    List<Message> result = new ArrayList<>();
    if (summary != null && !summary.isBlank()) {
      result.add(new SystemMessage(summary));
    }
    for (ConversationMessage m : recent) {
      result.add(toMessage(m));
    }
    return result;
  }

  /** Rough token estimate: ~4 characters per token. */
  private static int estimateTokens(List<ConversationMessage> history) {
    long chars = 0;
    for (ConversationMessage m : history) {
      chars += m.content() == null ? 0 : m.content().length();
    }
    return (int) Math.ceil(chars / 4.0);
  }

  /**
   * Folds {@code older} into the prior summary via a temperature-0 call to the model. Returns the
   * new summary, or {@code null} when none can be produced (model absent, or a blank result), so
   * callers can avoid silently dropping the folded turns.
   */
  private String summarize(String existingSummary, List<ConversationMessage> older) {
    ChatModel chatModel = model.getIfAvailable();
    if (chatModel == null) {
      return null;
    }
    StringBuilder transcript = new StringBuilder();
    if (existingSummary != null && !existingSummary.isBlank()) {
      transcript.append("Prior summary:\n").append(existingSummary).append("\n\n");
    }
    transcript.append("Older turns:\n");
    for (ConversationMessage m : older) {
      transcript.append(m.role()).append(": ").append(m.content()).append('\n');
    }
    String instruction =
        "Summarize the Ask Goldy's conversation excerpt below into a concise running summary. "
            + "Preserve all key facts, figures, decisions and open questions. "
            + "Return only the summary text, with no preamble.\n\n"
            + transcript;
    ChatResponse response = chatModel.call(new Prompt(instruction, temperatureZero()));
    String text = response.getResult().getOutput().getText();
    return (text == null || text.isBlank()) ? null : text;
  }

  private static ChatOptions temperatureZero() {
    return new DefaultChatOptionsBuilder().temperature(0.0).build();
  }

  private static Message toMessage(ConversationMessage m) {
    return switch (m.role()) {
      case "user" -> new UserMessage(m.content());
      case "assistant" -> new AssistantMessage(m.content());
      default -> throw new IllegalArgumentException("Unknown message role: " + m.role());
    };
  }

  private ConversationThread requireThread(UUID userId, UUID threadId) {
    return threads
        .findByIdAndUserAccountId(threadId, userId)
        .orElseThrow(() -> AccessDeniedException.forResource("conversation.thread"));
  }

  private ThreadSummary toSummary(ConversationThread thread) {
    String preview = "";
    List<ConversationMessage> history = messages.findByThreadIdOrderByCreatedAtAsc(thread.id());
    if (!history.isEmpty()) {
      String content = history.get(history.size() - 1).content();
      preview = content == null ? "" : content;
      if (preview.length() > PREVIEW_LENGTH) {
        preview = preview.substring(0, PREVIEW_LENGTH) + "\u2026";
      }
    }
    return new ThreadSummary(thread.id(), thread.title(), thread.updatedAt(), preview);
  }

  private static String titleFor(String message) {
    String cleaned = message.strip().replaceAll("\\s+", " ");
    if (cleaned.isBlank()) {
      return "New conversation";
    }
    return cleaned.length() <= MAX_TITLE_LENGTH ? cleaned : cleaned.substring(0, MAX_TITLE_LENGTH);
  }

  /** A compact row for the thread list. */
  public record ThreadSummary(UUID id, String title, Instant updatedAt, String lastPreview) {}

  /** A thread and its full message history, for the owning user. */
  public record ThreadView(UUID id, String title, List<ConversationMessage> messages) {
    public ThreadView {
      messages = messages == null ? List.of() : List.copyOf(messages);
    }
  }

  /**
   * The result of {@link #contextFor}: the resolved {@code threadId} (created when the caller
   * passed {@code null}) plus the model-ready message list.
   */
  public record PreparedTurn(UUID threadId, List<Message> messages) {
    public PreparedTurn {
      Objects.requireNonNull(threadId, "threadId");
      messages = messages == null ? List.of() : List.copyOf(messages);
    }
  }
}
