package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ConversationRepositoryTest {

  @Autowired ConversationThreadRepository threads;
  @Autowired ConversationMessageRepository messages;
  @Autowired JdbcTemplate jdbc;

  private UUID userId;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table conversation_message, conversation_thread, user_account");
    userId = UUID.randomUUID();
    jdbc.update(
        "insert into user_account "
            + "(id, email, password_hash, display_name, department, seniority, active, "
            + "created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        userId,
        "owner@example.com",
        "hash",
        "Owner",
        "ALL",
        "OWNER",
        true,
        OffsetDateTime.now(ZoneOffset.UTC),
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void threadRoundTripsAndQueriesByUser() {
    ConversationThread thread = threads.save(thread("Sales today"));

    ConversationThread byId = threads.findById(thread.id()).orElseThrow();
    assertThat(byId.userAccountId()).isEqualTo(userId);
    assertThat(byId.title()).isEqualTo("Sales today");
    assertThat(byId.summary()).isEqualTo("summary");
    assertThat(byId.lastMessageAt()).isNull();

    assertThat(threads.findByIdAndUserAccountId(thread.id(), userId)).isPresent();
    assertThat(threads.findByIdAndUserAccountId(thread.id(), UUID.randomUUID())).isEmpty();

    List<ConversationThread> forUser = threads.findByUserAccountIdOrderByUpdatedAtDesc(userId);
    assertThat(forUser).hasSize(1);
    assertThat(forUser.get(0).id()).isEqualTo(thread.id());
  }

  @Test
  void messageRoundTripsToolTrace() {
    ConversationThread thread = threads.save(thread("Inventory review"));
    List<AnswerPayload.TraceEntry> trace =
        List.of(
            new AnswerPayload.TraceEntry(
                "GET_INVENTORY_SUMMARY",
                "Food cost % and stock on hand",
                List.of(
                    new MetricProvenance(
                        MetricId.INVENTORY_FOOD_COST_PERCENT,
                        "1",
                        new TimeRange(
                            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), Calendar.CALENDAR),
                        TimeGrain.DAY,
                        "inventory",
                        Instant.parse("2026-09-07T12:00:00Z"),
                        List.of(LocalDate.of(2026, 9, 5)),
                        "1"))));

    ConversationMessage message =
        messages.save(
            ConversationMessage.create(
                thread.id(), "assistant", "Food cost is 32% this week.", trace, Instant.now()));

    List<ConversationMessage> reread = messages.findByThreadIdOrderByCreatedAtAsc(thread.id());
    assertThat(reread).hasSize(1);
    assertThat(reread.get(0).id()).isEqualTo(message.id());
    assertThat(reread.get(0).role()).isEqualTo("assistant");
    assertThat(reread.get(0).content()).isEqualTo("Food cost is 32% this week.");
    // The JSONB tool_trace round-trips to the exact TraceEntry list that went in.
    assertThat(reread.get(0).toolTrace()).isEqualTo(trace);
  }

  @Test
  void deleteByIdCascadesToMessages() {
    ConversationThread thread = threads.save(thread("Doomed"));
    messages.save(
        ConversationMessage.create(thread.id(), "user", "hello", List.of(), Instant.now()));

    threads.deleteById(thread.id());

    assertThat(threads.findById(thread.id())).isEmpty();
    assertThat(messages.findByThreadIdOrderByCreatedAtAsc(thread.id())).isEmpty();
  }

  private ConversationThread thread(String title) {
    return ConversationThread.create(userId, title, "summary", Instant.now());
  }
}
