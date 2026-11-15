package com.goldys.platform.workspace;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class WorkspaceJobsIntegrationTest {
  @Autowired WorkspaceJobs jobs;
  @Autowired WorkspaceReporting reporting;
  @Autowired WorkspaceThreads threads;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  UUID owner;

  @BeforeEach
  void setup() {
    jdbc.update(
        "truncate workspace_follow_up,workspace_thread,workspace_job,workspace_daily_budget");
    owner = account("OWNER");
  }

  UUID account(String seniority) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into user_account(id,email,password_hash,display_name,department,seniority,active,created_at,updated_at)
        values (?,?,'not-a-password','Synthetic owner','ALL',?,true,now(),now())
        """,
        id,
        id + "@example.test",
        seniority);
    return id;
  }

  WorkspaceJobs.Job submit(UUID thread, UUID request) {
    return jobs.submit(owner, thread, request, "Synthetic question");
  }

  com.fasterxml.jackson.databind.JsonNode snapshot() {
    return json.createObjectNode()
        .put("piVersion", "1.5.2")
        .putNull("leafId")
        .set(
            "entries",
            json.createArrayNode()
                .add(json.createObjectNode().put("type", "session").put("id", "synthetic")));
  }

  @Test
  void discoveryPaginatesTiedTimestampsWithoutLeakingOtherOwners() {
    var first = submit(UUID.randomUUID(), UUID.randomUUID());
    var second = submit(UUID.randomUUID(), UUID.randomUUID());
    jdbc.update("update workspace_thread set created_at='2026-11-15T00:00:00Z'");
    UUID other = account("OWNER");
    var foreign = jobs.submit(other, UUID.randomUUID(), UUID.randomUUID(), "Private question");
    var page = threads.list(owner, null, 1);
    assertThat(page.threads()).hasSize(1);
    assertThat(page.nextCursor()).isEqualTo(page.threads().getFirst().id());
    var next = threads.list(owner, page.nextCursor(), 1);
    assertThat(next.nextCursor()).isNull();
    assertThat(
            java.util.stream.Stream.concat(page.threads().stream(), next.threads().stream())
                .map(WorkspaceThreads.Summary::latestJobId)
                .toList())
        .containsExactlyInAnyOrder(first.id(), second.id());
    assertThatThrownBy(() -> threads.list(owner, foreign.threadId(), 1))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> threads.list(owner, UUID.randomUUID(), 1))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> threads.list(owner, null, 51))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(threads.list(account("OWNER"), null, 25).threads()).isEmpty();
  }

  @Test
  void discoveryMarksRevokedHistoryAndAllowsFreshThreads() {
    var old = submit(UUID.randomUUID(), UUID.randomUUID());
    jdbc.update("update user_account set updated_at=now()+interval '1 second' where id=?", owner);
    var restricted = threads.list(owner, null, 25).threads().getFirst();
    assertThat(restricted.id()).isEqualTo(old.threadId());
    assertThat(restricted.access()).isEqualTo("NOT_PERMITTED");
    assertThat(restricted.latestJobId()).isNull();
    assertThatThrownBy(() -> jobs.read(owner, old.id())).isInstanceOf(AccessDeniedException.class);
    var fresh = submit(UUID.randomUUID(), UUID.randomUUID());
    assertThat(threads.list(owner, null, 25).threads())
        .filteredOn(item -> item.access().equals("AVAILABLE"))
        .extracting(WorkspaceThreads.Summary::latestJobId)
        .containsExactly(fresh.id());
    assertThatThrownBy(() -> threads.list(account("MANAGER"), null, 25))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update("update user_account set active=false where id=?", owner);
    assertThatThrownBy(() -> threads.list(owner, null, 25))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void discoveryFindsLatestAttemptAndRecoversPromptBeforeFirstCheckpoint() {
    UUID thread = UUID.randomUUID();
    var first = submit(thread, UUID.randomUUID());
    jobs.stop(owner, first.id());
    var retry = jobs.submit(owner, thread, UUID.randomUUID(), "Explicit retry question");
    assertThat(threads.list(owner, null, 25).threads().getFirst().latestJobId())
        .isEqualTo(retry.id());
    var recovered = jobs.read(owner, retry.id());
    assertThat(recovered.prompt()).isEqualTo("Explicit retry question");
    assertThat(recovered.messages()).isEmpty();
  }

  @Test
  void permissionChangeRevokesSavedContextAndToolAccess() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    jobs.publish(job.id(), claim.fence(), 0, snapshot(), false);
    Boolean prior =
        jdbc.queryForObject(
            "select can_read from permission where department='ALL' and seniority='OWNER' and resource='labour.cost'",
            Boolean.class);
    try {
      jdbc.update(
          "update permission set can_read=false where department='ALL' and seniority='OWNER' and resource='labour.cost'");
      assertThatThrownBy(() -> jobs.read(owner, job.id()))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> reporting.tools(job.id(), claim.fence()))
          .isInstanceOf(AccessDeniedException.class);
    } finally {
      jdbc.update(
          "update permission set can_read=? where department='ALL' and seniority='OWNER' and resource='labour.cost'",
          prior);
    }
    assertThatThrownBy(() -> jobs.read(owner, job.id())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> reporting.tools(job.id(), claim.fence()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void restoredGrantsNeverReviveContextOrWorkerPublication() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    jobs.publish(job.id(), claim.fence(), 0, snapshot(), false);
    UUID grant = UUID.randomUUID();
    // Even an added then deleted grant must leave a durable invalidation.
    jdbc.update(
        "insert into permission(id,department,seniority,resource,can_read,can_write) values (?,'ALL','OWNER','workspace.synthetic',true,false)",
        grant);
    jdbc.update("delete from permission where id=?", grant);
    assertThatThrownBy(() -> jobs.read(owner, job.id())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> jobs.heartbeat(job.id(), claim.fence()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 1, snapshot(), true))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(threads.list(owner, null, 25).threads().getFirst().access())
        .isEqualTo("NOT_PERMITTED");
    assertThat(submit(UUID.randomUUID(), UUID.randomUUID()).state()).isEqualTo("QUEUED");
  }

  @Test
  void reactivatedAccountCannotReuseOldContextEvenWithoutTimestampChange() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    jdbc.update("update user_account set active=false where id=?", owner);
    jdbc.update("update user_account set active=true where id=?", owner);
    assertThatThrownBy(() -> jobs.read(owner, job.id())).isInstanceOf(AccessDeniedException.class);
    assertThat(jobs.claim()).isNull();
    assertThat(
            jdbc.queryForObject(
                "select state from workspace_job where id=?", String.class, job.id()))
        .isEqualTo("INTERRUPTED");
    assertThat(submit(UUID.randomUUID(), UUID.randomUUID()).state()).isEqualTo("QUEUED");
  }

  @Test
  void validatedDashboardDraftSurvivesNativeCheckpointWithoutSavingIt() throws Exception {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    var result =
        reporting.execute(
            job.id(),
            claim.fence(),
            com.goldys.platform.reporting.ToolId.CREATE_DASHBOARD_DRAFT,
            json.readTree(
                """
        {"title":"Weekly sales","widgets":[{"id":"sales","renderType":"stat",
          "layout":{"w":4,"h":2},"queries":[{"metric":"sales.net",
          "range":{"from":"2026-11-02","to":"2026-11-08","calendar":"TRADING"},
          "grain":"TOTAL","dimensions":[],"comparison":null}]}]}
        """));
    var details = json.valueToTree(result);
    assertThat(details.path("widget").path("schemaVersion").asInt()).isEqualTo(2);
    var saved = (com.fasterxml.jackson.databind.node.ObjectNode) snapshot();
    saved.put("leafId", "draft");
    var message = json.createObjectNode().put("role", "toolResult").set("details", details);
    ((com.fasterxml.jackson.databind.node.ArrayNode) saved.path("entries"))
        .add(
            json.createObjectNode()
                .put("type", "message")
                .put("id", "draft")
                .putNull("parentId")
                .set("message", message));
    jobs.publish(job.id(), claim.fence(), 0, saved, true);
    var draft = jobs.read(owner, job.id()).messages().getFirst().draft();
    assertThat(draft.title()).isEqualTo("Weekly sales");
    assertThat(draft.widgets()).hasSize(1);
    assertThat(draft.widgets().getFirst().queries().getFirst().metric())
        .isEqualTo(com.goldys.platform.semantic.catalog.MetricId.SALES_NET);
    assertThat(draft.dashboardId()).isNull();
  }

  @Test
  void releasedToolSchemaRetainsClosedMetricsAndDateTypes() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    var spec =
        reporting.tools(job.id(), claim.fence()).stream()
            .filter(tool -> tool.id().equals("GET_METRIC"))
            .findFirst()
            .orElseThrow();
    assertThat(spec.name()).isEqualTo("goldys_get_metric");
    assertThat(spec.parameters().toString()).contains("sales.net", "date", "string");
  }

  @Test
  void reportingRejectsUnknownArgumentsAndRevokedWorkers() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    assertThatThrownBy(
            () ->
                reporting.execute(
                    job.id(),
                    claim.fence(),
                    com.goldys.platform.reporting.ToolId.GET_METRIC,
                    json.createObjectNode().put("sql", "select secret")))
        .isInstanceOf(IllegalArgumentException.class);
    jdbc.update("update user_account set active=false where id=?", owner);
    assertThatThrownBy(
            () ->
                reporting.execute(
                    job.id(),
                    claim.fence(),
                    com.goldys.platform.reporting.ToolId.GET_METRIC,
                    json.createObjectNode()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void duplicateSubmissionAndReconnectPreserveOneJob() throws Exception {
    UUID thread = UUID.randomUUID(), request = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> submit(thread, request));
      var second = pool.submit(() -> submit(thread, request));
      assertThat(first.get().id()).isEqualTo(second.get().id());
    }
    assertThat(jdbc.queryForObject("select count(*) from workspace_job", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForList("select reserved_aud from workspace_daily_budget", Integer.class))
        .isNotEmpty()
        .allMatch(amount -> amount == 5);
    assertThatThrownBy(() -> jobs.submit(owner, thread, request, "Different question"))
        .isInstanceOf(WorkspaceConflict.class);
    var claim = jobs.claim();
    jobs.publish(claim.id(), claim.fence(), claim.revision(), snapshot(), true);
    assertThat(jobs.read(owner, claim.id()).state()).isEqualTo("COMPLETED");
    assertThat(jobs.read(owner, claim.id()).revision()).isEqualTo(1);
  }

  @Test
  void stopFencesPublicationButKeepsCommittedHistory() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    jobs.publish(job.id(), claim.fence(), 0, snapshot(), false);
    jobs.stop(owner, job.id());
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 1, snapshot(), true))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jobs.read(owner, job.id()).state()).isEqualTo("CANCELLED");
    assertThat(jobs.read(owner, job.id()).revision()).isEqualTo(1);
  }

  @Test
  void expiredWorkerIsInterruptedAndOnlyExplicitRetryRuns() {
    UUID thread = UUID.randomUUID();
    var job = submit(thread, UUID.randomUUID());
    var claim = jobs.claim();
    jobs.publish(job.id(), claim.fence(), 0, snapshot(), false);
    jdbc.update(
        "update workspace_job set lease_until=now()-interval '1 second' where id=?", job.id());
    assertThat(jobs.read(owner, job.id()).state()).isEqualTo("INTERRUPTED");
    assertThat(jobs.claim()).isNull();
    assertThatThrownBy(() -> jobs.heartbeat(job.id(), claim.fence()))
        .isInstanceOf(IllegalStateException.class);
    var retry = submit(thread, UUID.randomUUID());
    assertThat(retry.revision()).isEqualTo(1);
    assertThat(jobs.claim().id()).isEqualTo(retry.id());
  }

  @Test
  void ownershipManagerAndRevokedHistoryAreDenied() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    assertThatThrownBy(() -> jobs.read(account("OWNER"), job.id()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> jobs.submit(account("MANAGER"), UUID.randomUUID(), UUID.randomUUID(), "test"))
        .isInstanceOf(AccessDeniedException.class);
    var claim = jobs.claim();
    jobs.publish(job.id(), claim.fence(), 0, snapshot(), false);
    jdbc.update("update user_account set updated_at=now()+interval '1 second' where id=?", owner);
    assertThatThrownBy(() -> jobs.read(owner, job.id())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> jobs.heartbeat(job.id(), claim.fence()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 1, snapshot(), true))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void dailyReservationsCannotExceedFiftyDollars() {
    for (int i = 0; i < 10; i++) {
      var job = submit(UUID.randomUUID(), UUID.randomUUID());
      jobs.stop(owner, job.id());
    }
    assertThatThrownBy(() -> submit(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("budget");
    assertThat(jdbc.queryForObject("select count(*) from workspace_job", Integer.class))
        .isEqualTo(10);
  }

  @Test
  void malformedCheckpointCannotReplaceCommittedStateOrCompleteJob() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    jobs.publish(job.id(), claim.fence(), 0, snapshot(), false);
    var broken = (com.fasterxml.jackson.databind.node.ObjectNode) snapshot();
    broken.put("leafId", "missing");
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 1, broken, true))
        .isInstanceOf(IllegalArgumentException.class);
    var saved = jobs.read(owner, job.id());
    assertThat(saved.revision()).isEqualTo(1);
    assertThat(saved.state()).isEqualTo("RUNNING");
    var entries = (com.fasterxml.jackson.databind.node.ArrayNode) broken.path("entries");
    entries.add(
        json.createObjectNode()
            .put("type", "message")
            .put("id", "missing")
            .put("parentId", "missing"));
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 1, broken, false))
        .isInstanceOf(IllegalArgumentException.class);
    ((com.fasterxml.jackson.databind.node.ObjectNode) entries.get(1)).putNull("parentId");
    entries.add(entries.get(1).deepCopy());
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 1, broken, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(jobs.read(owner, job.id()).revision()).isEqualTo(1);
  }

  @Test
  void staleRevisionRollsBackTerminalTransition() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 17, snapshot(), true))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jobs.read(owner, job.id()).state()).isEqualTo("RUNNING");
  }

  @Test
  void followUpsAreOrderedEditableIdempotentAndShareAdmission() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    jobs.enqueue(owner, job.id(), first, "First");
    jobs.enqueue(owner, job.id(), first, "First");
    jobs.enqueue(owner, job.id(), second, "Second");
    jobs.editFollowUp(owner, job.id(), first, "Edited");
    assertThat(jobs.enqueue(owner, job.id(), first, "First").prompt()).isEqualTo("Edited");
    assertThatThrownBy(() -> jobs.enqueue(owner, job.id(), first, "Different"))
        .isInstanceOf(WorkspaceConflict.class);
    var claim = jobs.claim();
    assertThat(jobs.settle(job.id(), claim.fence(), 0, snapshot()).prompt()).isEqualTo("Edited");
    assertThatThrownBy(() -> jobs.editFollowUp(owner, job.id(), first, "Too late"))
        .isInstanceOf(IllegalStateException.class);
    jobs.removeFollowUp(owner, job.id(), second);
    assertThat(jobs.enqueue(owner, job.id(), second, "Second").state()).isEqualTo("CANCELLED");
    jdbc.update(
        "update workspace_follow_up set request_prompt_sha256=null where job_id=? and request_id=?",
        job.id(),
        second);
    assertThatThrownBy(() -> jobs.enqueue(owner, job.id(), second, "Second"))
        .isInstanceOf(WorkspaceConflict.class);
    assertThat(jobs.settle(job.id(), claim.fence(), 1, snapshot())).isNull();
    assertThat(jobs.read(owner, job.id()).state()).isEqualTo("COMPLETED");
    assertThatThrownBy(() -> jobs.enqueue(owner, job.id(), UUID.randomUUID(), "Late"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForList("select reserved_aud from workspace_daily_budget", Integer.class))
        .isNotEmpty()
        .allMatch(amount -> amount == 5);
  }

  @Test
  void stoppedAndInterruptedJobsCancelPendingFollowUpsAndRejectStaleWorkers() {
    for (boolean stop : new boolean[] {true, false}) {
      var job = submit(UUID.randomUUID(), UUID.randomUUID());
      var claim = jobs.claim();
      jobs.enqueue(owner, job.id(), UUID.randomUUID(), "Pending");
      if (stop) jobs.stop(owner, job.id());
      else jobs.interrupt(job.id(), claim.fence());
      assertThat(jobs.followUps(owner, job.id()))
          .extracting(WorkspaceJobs.FollowUp::state)
          .containsExactly("CANCELLED");
      assertThatThrownBy(() -> jobs.settle(job.id(), claim.fence(), 0, snapshot()))
          .isInstanceOf(IllegalStateException.class);
      jobs.interrupt(job.id(), claim.fence());
      assertThat(jobs.read(owner, job.id()).state()).isEqualTo(stop ? "CANCELLED" : "INTERRUPTED");
    }
  }

  @Test
  void followUpsRequireOwnershipAndCurrentPolicyAndCannotBeSkippedByCompletion() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    UUID request = UUID.randomUUID();
    jobs.enqueue(owner, job.id(), request, "Pending");
    assertThatThrownBy(() -> jobs.followUps(account("OWNER"), job.id()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 0, snapshot(), true))
        .isInstanceOf(IllegalStateException.class);
    jdbc.update("update user_account set active=false where id=?", owner);
    assertThatThrownBy(() -> jobs.editFollowUp(owner, job.id(), request, "Changed"))
        .isInstanceOf(AccessDeniedException.class);
    jobs.interrupt(job.id(), claim.fence());
    assertThat(
            jdbc.queryForObject(
                "select state from workspace_follow_up where job_id=?", String.class, job.id()))
        .isEqualTo("CANCELLED");
  }

  @Test
  void racingCompletionAndEnqueueCannotStrandAnAcceptedMessage() throws Exception {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var completion =
          pool.submit(
              () -> {
                start.await();
                return jobs.settle(job.id(), claim.fence(), 0, snapshot());
              });
      var enqueue =
          pool.submit(
              () -> {
                start.await();
                try {
                  jobs.enqueue(owner, job.id(), UUID.randomUUID(), "Racing follow-up");
                  return true;
                } catch (IllegalStateException ended) {
                  return false;
                }
              });
      start.countDown();
      var next = completion.get();
      boolean accepted = enqueue.get();
      assertThat(accepted).isEqualTo(next != null);
      assertThat(jobs.read(owner, job.id()).state()).isEqualTo(accepted ? "RUNNING" : "COMPLETED");
      assertThat(jobs.followUps(owner, job.id())).noneMatch(item -> item.state().equals("QUEUED"));
    }
  }

  @Test
  void leaseExpiryCancelsQueueWithoutReplayingTakenFollowUps() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    jobs.enqueue(owner, job.id(), UUID.randomUUID(), "Taken but not completed");
    jobs.enqueue(owner, job.id(), UUID.randomUUID(), "Never taken");
    jobs.settle(job.id(), claim.fence(), 0, snapshot());
    jdbc.update(
        "update workspace_job set lease_until=now()-interval '1 second' where id=?", job.id());
    assertThat(jobs.read(owner, job.id()).state()).isEqualTo("INTERRUPTED");
    assertThat(jobs.followUps(owner, job.id()))
        .extracting(WorkspaceJobs.FollowUp::state)
        .containsExactly("TAKEN", "CANCELLED");
    assertThat(jobs.claim()).isNull();
  }
}
