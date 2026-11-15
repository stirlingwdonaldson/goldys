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
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  UUID owner;

  @BeforeEach
  void setup() {
    jdbc.update("truncate workspace_thread,workspace_job,workspace_daily_budget");
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
    assertThat(
            jdbc.queryForObject("select reserved_aud from workspace_daily_budget", Integer.class))
        .isEqualTo(5);
    var claim = jobs.claim();
    jobs.publish(claim.id(), claim.fence(), claim.revision(), snapshot(), true);
    assertThat(jobs.read(owner, claim.id()).state()).isEqualTo("COMPLETED");
    assertThat(jobs.read(owner, claim.id()).snapshot()).isEqualTo(snapshot());
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
    assertThat(jobs.read(owner, job.id()).snapshot()).isEqualTo(snapshot());
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
    assertThat(retry.snapshot()).isEqualTo(snapshot());
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
  void staleRevisionRollsBackTerminalTransition() {
    var job = submit(UUID.randomUUID(), UUID.randomUUID());
    var claim = jobs.claim();
    assertThatThrownBy(() -> jobs.publish(job.id(), claim.fence(), 17, snapshot(), true))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jobs.read(owner, job.id()).state()).isEqualTo("RUNNING");
  }
}
