package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.conversational.ConversationService.ThreadSummary;
import com.goldys.platform.conversational.ConversationService.ThreadView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class ConversationServiceTest {

  private static final UUID USER = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  private ConversationThreadRepository threads;
  private ConversationMessageRepository messages;
  private ObjectProvider<ChatModel> modelProvider;
  private ChatModel model;
  private ConversationService service;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    threads = mock(ConversationThreadRepository.class);
    messages = mock(ConversationMessageRepository.class);
    modelProvider = mock(ObjectProvider.class);
    model = mock(ChatModel.class);

    when(threads.save(any(ConversationThread.class))).thenAnswer(inv -> inv.getArgument(0));
    when(messages.save(any(ConversationMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    when(modelProvider.getIfAvailable()).thenReturn(model);

    service = new ConversationService(threads, messages, modelProvider);
  }

  @Test
  void contextForCreatesThreadAndAppendsUserMessage() {
    when(messages.findByThreadIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

    List<Message> context = service.contextFor(USER, null, "Why were sales down?");

    assertThat(context).hasSize(1);
    assertThat(context.get(0)).isInstanceOf(UserMessage.class);
    assertThat(context.get(0).getText()).isEqualTo("Why were sales down?");

    ArgumentCaptor<ConversationThread> threadCaptor =
        ArgumentCaptor.forClass(ConversationThread.class);
    verify(threads, atLeastOnce()).save(threadCaptor.capture());
    ConversationThread saved = threadCaptor.getValue();
    assertThat(saved.userAccountId()).isEqualTo(USER);
    assertThat(saved.title()).isEqualTo("Why were sales down?");
  }

  @Test
  void contextForLoadsExistingThreadAndAppendsUserMessage() {
    ConversationThread thread =
        ConversationThread.create(USER, "Sales review", null, Instant.now());
    when(threads.findByIdAndUserAccountId(thread.id(), USER)).thenReturn(Optional.of(thread));
    when(messages.findByThreadIdOrderByCreatedAtAsc(thread.id()))
        .thenReturn(
            List.of(
                ConversationMessage.create(
                    thread.id(), "assistant", "Prior answer", List.of(), Instant.now())));

    List<Message> context = service.contextFor(USER, thread.id(), "Why were sales down?");

    assertThat(context).hasSize(2);
    assertThat(context.get(0)).isInstanceOf(AssistantMessage.class);
    assertThat(context.get(1)).isInstanceOf(UserMessage.class);
    assertThat(context.get(1).getText()).isEqualTo("Why were sales down?");
  }

  @Test
  void contextForRejectsCrossUserThread() {
    when(threads.findByIdAndUserAccountId(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.contextFor(OTHER, UUID.randomUUID(), "hello"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void contextForCompactsWhenMoreThanKeepRecentMessages() {
    ConversationThread thread = ConversationThread.create(USER, "Long review", null, Instant.now());
    when(threads.findByIdAndUserAccountId(thread.id(), USER)).thenReturn(Optional.of(thread));

    List<ConversationMessage> prior = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      prior.add(
          ConversationMessage.create(
              thread.id(), "user", "question " + i, List.of(), Instant.now()));
      prior.add(
          ConversationMessage.create(
              thread.id(), "assistant", "answer " + i, List.of(), Instant.now()));
    }
    when(messages.findByThreadIdOrderByCreatedAtAsc(thread.id())).thenReturn(prior);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("Compacted summary")))));

    List<Message> context = service.contextFor(USER, thread.id(), "Why were sales down?");

    // summary + the 6 most recent messages (5 prior + the appended user message).
    assertThat(context).hasSize(7);
    assertThat(context.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(context.get(0).getText()).isEqualTo("Compacted summary");
    assertThat(context.get(context.size() - 1)).isInstanceOf(UserMessage.class);
    assertThat(thread.summary()).isEqualTo("Compacted summary");
  }

  @Test
  void summarizationUsesTemperatureZero() {
    ConversationThread thread = ConversationThread.create(USER, "Long review", null, Instant.now());
    when(threads.findByIdAndUserAccountId(thread.id(), USER)).thenReturn(Optional.of(thread));

    List<ConversationMessage> prior = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      prior.add(
          ConversationMessage.create(
              thread.id(), "user", "question " + i, List.of(), Instant.now()));
      prior.add(
          ConversationMessage.create(
              thread.id(), "assistant", "answer " + i, List.of(), Instant.now()));
    }
    when(messages.findByThreadIdOrderByCreatedAtAsc(thread.id())).thenReturn(prior);
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("Compacted summary")))));

    service.contextFor(USER, thread.id(), "Why were sales down?");

    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(model).call(promptCaptor.capture());
    assertThat(promptCaptor.getValue().getOptions().getTemperature()).isEqualTo(0.0);
  }

  @Test
  void contextForDoesNotSummarizeWithinKeepRecent() {
    ConversationThread thread =
        ConversationThread.create(USER, "Short review", null, Instant.now());
    when(threads.findByIdAndUserAccountId(thread.id(), USER)).thenReturn(Optional.of(thread));
    when(messages.findByThreadIdOrderByCreatedAtAsc(thread.id()))
        .thenReturn(
            List.of(
                ConversationMessage.create(
                    thread.id(), "assistant", "prior", List.of(), Instant.now())));

    service.contextFor(USER, thread.id(), "hello");

    verify(model, never()).call(any(Prompt.class));
  }

  @Test
  void completePersistsAssistantMessageAndBumpsTimestamps() {
    ConversationThread thread =
        ConversationThread.create(USER, "Sales review", null, Instant.now());
    when(threads.findByIdAndUserAccountId(thread.id(), USER)).thenReturn(Optional.of(thread));

    List<AnswerPayload.TraceEntry> trace =
        List.of(new AnswerPayload.TraceEntry("TOOL", "desc", List.of()));
    service.complete(USER, thread.id(), "Sales were down 4%.", trace);

    ArgumentCaptor<ConversationMessage> messageCaptor =
        ArgumentCaptor.forClass(ConversationMessage.class);
    verify(messages).save(messageCaptor.capture());
    ConversationMessage saved = messageCaptor.getValue();
    assertThat(saved.threadId()).isEqualTo(thread.id());
    assertThat(saved.role()).isEqualTo("assistant");
    assertThat(saved.content()).isEqualTo("Sales were down 4%.");
    assertThat(saved.toolTrace()).isEqualTo(trace);
    assertThat(thread.lastMessageAt()).isNotNull();
    assertThat(thread.updatedAt()).isNotNull();
  }

  @Test
  void completeRejectsCrossUserThread() {
    when(threads.findByIdAndUserAccountId(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.complete(OTHER, UUID.randomUUID(), "text", List.of()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void listReturnsSummariesForUser() {
    ConversationThread thread =
        ConversationThread.create(USER, "Sales review", null, Instant.now());
    when(threads.findByUserAccountIdOrderByUpdatedAtDesc(USER)).thenReturn(List.of(thread));
    when(messages.findByThreadIdOrderByCreatedAtAsc(thread.id()))
        .thenReturn(
            List.of(
                ConversationMessage.create(
                    thread.id(), "assistant", "Food cost is 32%.", List.of(), Instant.now())));

    List<ThreadSummary> summaries = service.list(USER);

    assertThat(summaries).hasSize(1);
    ThreadSummary summary = summaries.get(0);
    assertThat(summary.id()).isEqualTo(thread.id());
    assertThat(summary.title()).isEqualTo("Sales review");
    assertThat(summary.lastPreview()).isEqualTo("Food cost is 32%.");
  }

  @Test
  void getReturnsThreadView() {
    ConversationThread thread =
        ConversationThread.create(USER, "Sales review", null, Instant.now());
    ConversationMessage message =
        ConversationMessage.create(thread.id(), "user", "hello", List.of(), Instant.now());
    when(threads.findByIdAndUserAccountId(thread.id(), USER)).thenReturn(Optional.of(thread));
    when(messages.findByThreadIdOrderByCreatedAtAsc(thread.id())).thenReturn(List.of(message));

    ThreadView view = service.get(USER, thread.id());

    assertThat(view.id()).isEqualTo(thread.id());
    assertThat(view.title()).isEqualTo("Sales review");
    assertThat(view.messages()).containsExactly(message);
  }

  @Test
  void getRejectsCrossUserThread() {
    when(threads.findByIdAndUserAccountId(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(OTHER, UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void renameUpdatesTitle() {
    ConversationThread thread = ConversationThread.create(USER, "Old title", null, Instant.now());
    when(threads.findByIdAndUserAccountId(thread.id(), USER)).thenReturn(Optional.of(thread));

    service.rename(USER, thread.id(), "New title");

    assertThat(thread.title()).isEqualTo("New title");
    verify(threads).save(thread);
  }

  @Test
  void renameRejectsCrossUserThread() {
    when(threads.findByIdAndUserAccountId(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rename(OTHER, UUID.randomUUID(), "title"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void deleteRemovesOwnedThread() {
    ConversationThread thread = ConversationThread.create(USER, "Doomed", null, Instant.now());
    when(threads.findByIdAndUserAccountId(thread.id(), USER)).thenReturn(Optional.of(thread));

    service.delete(USER, thread.id());

    verify(threads).deleteById(thread.id());
  }

  @Test
  void deleteRejectsCrossUserThread() {
    when(threads.findByIdAndUserAccountId(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(OTHER, UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
  }
}
