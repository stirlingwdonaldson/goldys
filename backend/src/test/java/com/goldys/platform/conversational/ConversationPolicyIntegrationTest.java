package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ConversationPolicyIntegrationTest {
  @Autowired ConversationService conversations;
  @Autowired JdbcTemplate jdbc;
  UUID user;

  @BeforeEach
  void account() {
    recovery(false);
    user = UUID.randomUUID();
    jdbc.update(
        """
        insert into user_account(id,email,password_hash,display_name,department,seniority,active,created_at,updated_at)
        values (?,?,'synthetic','Synthetic','ALL','OWNER',true,now(),now())
        """,
        user,
        user + "@example.test");
  }

  @AfterEach
  void restoreRecoveryGrant() {
    recovery(true);
  }

  private void recovery(boolean allowed) {
    jdbc.update(
        "update permission set can_read=? where department='ALL' and seniority='OWNER' and resource='conversational.archive-recovery'",
        allowed);
  }

  @Test
  void explicitRecoveryReadsOwnedArchivesButCannotResumeOrExposeAnotherOwnersHistory() {
    UUID thread = conversations.contextFor(user, null, "Archived question").threadId();
    conversations.complete(user, thread, "Archived answer", List.of());
    jdbc.update("update conversation_thread set policy_stamp=null where id=?", thread);
    recovery(true);
    assertThat(conversations.list(user).getFirst().title()).isEqualTo("Archived question");
    assertThat(conversations.get(user, thread).messages()).hasSize(2);
    assertThatThrownBy(() -> conversations.contextFor(user, thread, "Continue"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> conversations.get(UUID.randomUUID(), thread))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(
            jdbc.queryForObject(
                "select policy_stamp from conversation_thread where id=?", String.class, thread))
        .isNull();
    jdbc.update("update user_account set active=false where id=?", user);
    assertThatThrownBy(() -> conversations.get(user, thread))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update("update user_account set active=true where id=?", user);
    recovery(false);
    assertRestricted(thread);
  }

  @Test
  void newLegacyThreadsWorkUntilPolicyChangesAndRegrantCannotReviveThem() {
    UUID thread = conversations.contextFor(user, null, "Synthetic question").threadId();
    conversations.complete(user, thread, "Synthetic answer", List.of());
    assertThat(conversations.get(user, thread).messages()).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "select policy_stamp from conversation_thread where id=?", String.class, thread))
        .hasSize(64);
    // Restore the grant before reading, to prove the retained revision prevents revival.
    jdbc.update(
        "update permission set can_read=false where department='ALL' and seniority='OWNER' and resource='reconciliation.sales'");
    jdbc.update(
        "update permission set can_read=true where department='ALL' and seniority='OWNER' and resource='reconciliation.sales'");
    assertRestricted(thread);
    assertThat(conversations.contextFor(user, null, "Fresh question").messages()).hasSize(1);
  }

  @Test
  void unknownHistoricalPolicyIsNotBackfilledByReadingOrContinuing() {
    UUID thread = conversations.contextFor(user, null, "Private title").threadId();
    jdbc.update(
        "update conversation_thread set policy_stamp=null,summary='Private summary' where id=?",
        thread);
    assertRestricted(thread);
    assertThat(
            jdbc.queryForObject(
                "select policy_stamp from conversation_thread where id=?", String.class, thread))
        .isNull();
    conversations.delete(user, thread);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from conversation_thread where id=?", Integer.class, thread))
        .isZero();
  }

  @Test
  void disableReactivateAlsoPermanentlyInvalidatesLegacyContext() {
    UUID thread = conversations.contextFor(user, null, "Synthetic question").threadId();
    jdbc.update("update user_account set active=false where id=?", user);
    assertThatThrownBy(() -> conversations.list(user)).isInstanceOf(AccessDeniedException.class);
    jdbc.update("update user_account set active=true where id=?", user);
    assertRestricted(thread);
  }

  private void assertRestricted(UUID thread) {
    assertThat(conversations.list(user))
        .allSatisfy(
            summary -> {
              assertThat(summary.title()).isEqualTo("Restricted conversation");
              assertThat(summary.lastPreview()).doesNotContain("Private", "Synthetic");
            });
    assertThatThrownBy(() -> conversations.get(user, thread))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> conversations.contextFor(user, thread, "Continue"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> conversations.complete(user, thread, "Late answer", List.of()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> conversations.authorizeOutput(user, thread))
        .isInstanceOf(AccessDeniedException.class);
  }
}
