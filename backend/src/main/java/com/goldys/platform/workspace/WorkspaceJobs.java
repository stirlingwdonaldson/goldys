package com.goldys.platform.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.WorkspaceAuthorization;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable admission and ownership; conversation semantics remain in Pi. */
@Service
public class WorkspaceJobs {
  public record Job(
      UUID id,
      UUID threadId,
      String state,
      long revision,
      List<WorkspaceHistory.Message> messages) {}

  public record FollowUp(UUID id, String prompt, String state) {}

  public record Claim(
      UUID id,
      UUID threadId,
      UUID fence,
      String prompt,
      Instant deadline,
      long revision,
      JsonNode snapshot) {}

  private record ThreadRow(UUID owner, String policy, long revision, String snapshot) {}

  private final JdbcTemplate jdbc;
  private final WorkspaceAuthorization authorization;
  private final ObjectMapper json;

  public WorkspaceJobs(JdbcTemplate jdbc, WorkspaceAuthorization authorization, ObjectMapper json) {
    this.jdbc = jdbc;
    this.authorization = authorization;
    this.json = json;
  }

  @Transactional
  public Job submit(UUID owner, UUID threadId, UUID requestId, String prompt) {
    validatePrompt(prompt);
    String stamp = authorization.require(owner, PermissionAction.WRITE);
    jdbc.update(
        "insert into workspace_thread(id,user_account_id,policy_stamp) values (?,?,?) on conflict do nothing",
        threadId,
        owner,
        stamp);
    ThreadRow thread = owned(owner, threadId, PermissionAction.WRITE);
    var existing =
        jdbc.query(
            "select id from workspace_job where thread_id=? and request_id=?",
            (row, index) -> row.getObject(1, UUID.class),
            threadId,
            requestId);
    if (!existing.isEmpty()) return read(owner, existing.getFirst());
    interruptExpired(threadId);
    Integer active =
        jdbc.queryForObject(
            "select count(*) from workspace_job where thread_id=? and state in ('QUEUED','RUNNING')",
            Integer.class,
            threadId);
    if (active != null && active > 0)
      throw new WorkspaceConflict("An investigation is already active");
    LocalDate day = LocalDate.now(ZoneId.of("Australia/Melbourne"));
    jdbc.update(
        "insert into workspace_daily_budget(venue_day,reserved_aud) values (?,0) on conflict do nothing",
        day);
    int admitted =
        jdbc.update(
            "update workspace_daily_budget set reserved_aud=reserved_aud+5 where venue_day=? and reserved_aud+5<=50",
            day);
    if (admitted != 1) throw new WorkspaceConflict("Daily investigation budget exhausted");
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into workspace_job(id,thread_id,request_id,prompt,state,fence,deadline)
        values (?,?,?,?,'QUEUED',?,now()+interval '15 minutes')
        """,
        id,
        threadId,
        requestId,
        prompt,
        UUID.randomUUID());
    return new Job(
        id,
        threadId,
        "QUEUED",
        thread.revision(),
        WorkspaceHistory.project(parse(thread.snapshot()), json));
  }

  @Transactional
  public Job read(UUID owner, UUID id) {
    UUID threadId = threadId(id);
    ThreadRow thread = owned(owner, threadId, PermissionAction.READ);
    interruptExpired(threadId);
    String state =
        jdbc.queryForObject("select state from workspace_job where id=?", String.class, id);
    return new Job(
        id,
        threadId,
        state,
        thread.revision(),
        WorkspaceHistory.project(parse(thread.snapshot()), json));
  }

  @Transactional
  public List<FollowUp> followUps(UUID owner, UUID id) {
    owned(owner, threadId(id), PermissionAction.READ);
    interruptExpired(threadId(id));
    return jdbc.query(
        "select request_id,prompt,state from workspace_follow_up where job_id=? order by sequence",
        (row, index) ->
            new FollowUp(row.getObject(1, UUID.class), row.getString(2), row.getString(3)),
        id);
  }

  @Transactional
  public FollowUp enqueue(UUID owner, UUID id, UUID requestId, String prompt) {
    validatePrompt(prompt);
    owned(owner, threadId(id), PermissionAction.WRITE);
    var existing =
        followUps(owner, id).stream().filter(item -> item.id().equals(requestId)).findFirst();
    if (existing.isPresent()) return existing.get();
    requireActive(id);
    Integer count =
        jdbc.queryForObject(
            "select count(*) from workspace_follow_up where job_id=?", Integer.class, id);
    if (count >= 20) throw new WorkspaceConflict("Investigation follow-up limit reached");
    jdbc.update(
        "insert into workspace_follow_up(job_id,request_id,prompt,state) values (?,?,?,'QUEUED')",
        id,
        requestId,
        prompt);
    return new FollowUp(requestId, prompt, "QUEUED");
  }

  @Transactional
  public void editFollowUp(UUID owner, UUID id, UUID requestId, String prompt) {
    validatePrompt(prompt);
    owned(owner, threadId(id), PermissionAction.WRITE);
    interruptExpired(threadId(id));
    requireActive(id);
    if (jdbc.update(
            "update workspace_follow_up set prompt=? where job_id=? and request_id=? and state='QUEUED'",
            prompt,
            id,
            requestId)
        != 1) throw new WorkspaceConflict("Follow-up is no longer editable");
  }

  @Transactional
  public void removeFollowUp(UUID owner, UUID id, UUID requestId) {
    owned(owner, threadId(id), PermissionAction.WRITE);
    interruptExpired(threadId(id));
    if (jdbc.update(
            "update workspace_follow_up set state='CANCELLED' where job_id=? and request_id=? and state='QUEUED'",
            id,
            requestId)
        != 1) throw new WorkspaceConflict("Follow-up is no longer editable");
  }

  /** Atomically finish or take the oldest follow-up; no arrival can fall between these actions. */
  @Transactional
  public FollowUp settle(UUID id, UUID fence, long revision, JsonNode snapshot) {
    heartbeat(id, fence);
    var pending =
        jdbc.query(
            "select request_id,prompt from workspace_follow_up where job_id=? and state='QUEUED' order by sequence limit 1",
            (row, index) -> new FollowUp(row.getObject(1, UUID.class), row.getString(2), "TAKEN"),
            id);
    publish(id, fence, revision, snapshot, pending.isEmpty());
    if (pending.isEmpty()) return null;
    FollowUp next = pending.getFirst();
    jdbc.update(
        "update workspace_follow_up set state='TAKEN' where job_id=? and request_id=?",
        id,
        next.id());
    return next;
  }

  @Transactional
  public void interrupt(UUID id, UUID fence) {
    thread(threadId(id));
    // Failure reporting carries no data and remains allowed after policy revocation.
    if (jdbc.update(
            "update workspace_job set state='INTERRUPTED',fence=gen_random_uuid(),lease_until=null where id=? and fence=? and state='RUNNING'",
            id,
            fence)
        == 1) cancelFollowUps(id);
  }

  private void requireActive(UUID id) {
    String state =
        jdbc.queryForObject("select state from workspace_job where id=?", String.class, id);
    if (!"QUEUED".equals(state) && !"RUNNING".equals(state))
      throw new WorkspaceConflict("Investigation has ended");
  }

  private void cancelFollowUps(UUID id) {
    jdbc.update(
        "update workspace_follow_up set state='CANCELLED' where job_id=? and state='QUEUED'", id);
  }

  private static void validatePrompt(String prompt) {
    if (prompt == null || prompt.isBlank() || prompt.length() > 16000)
      throw new IllegalArgumentException("Message must contain 1–16000 characters");
  }

  @Transactional
  public Job stop(UUID owner, UUID id) {
    owned(owner, threadId(id), PermissionAction.WRITE);
    jdbc.update(
        "update workspace_job set state='CANCELLED', fence=?,lease_until=null where id=? and state in ('QUEUED','RUNNING')",
        UUID.randomUUID(),
        id);
    cancelFollowUps(id);
    return read(owner, id);
  }

  @Transactional
  public Claim claim() {
    // Lock thread before job everywhere; expired attempts are interrupted, never replayed.
    List<UUID> threads =
        jdbc.query(
            """
        select t.id from workspace_thread t where exists
        (select 1 from workspace_job j where j.thread_id=t.id and (j.state='QUEUED' or (j.state='RUNNING' and (j.lease_until<=now() or j.deadline<=now()))))
        order by t.created_at,t.id for update skip locked limit 1
        """,
            (row, index) -> row.getObject(1, UUID.class));
    if (threads.isEmpty()) return null;
    UUID threadId = threads.getFirst();
    ThreadRow thread = thread(threadId);
    interruptExpired(threadId);
    List<UUID> ids =
        jdbc.query(
            "select id from workspace_job where thread_id=? and state='QUEUED'",
            (row, index) -> row.getObject(1, UUID.class),
            threadId);
    if (ids.isEmpty()) return null;
    UUID id = ids.getFirst();
    try {
      checkPolicy(thread, PermissionAction.READ);
    } catch (AccessDeniedException denied) {
      jdbc.update(
          "update workspace_job set state='INTERRUPTED',fence=? where id=?", UUID.randomUUID(), id);
      cancelFollowUps(id);
      return null;
    }
    UUID fence = UUID.randomUUID();
    jdbc.update(
        "update workspace_job set state='RUNNING',fence=?,lease_until=now()+interval '30 seconds' where id=?",
        fence,
        id);
    return jdbc.queryForObject(
        "select prompt,deadline from workspace_job where id=?",
        (row, index) ->
            new Claim(
                id,
                threadId,
                fence,
                row.getString(1),
                row.getTimestamp(2).toInstant(),
                thread.revision(),
                parse(thread.snapshot())),
        id);
  }

  @Transactional
  public void heartbeat(UUID id, UUID fence) {
    ThreadRow thread = thread(threadId(id));
    checkPolicy(thread, PermissionAction.READ);
    int changed =
        jdbc.update(
            """
        update workspace_job set lease_until=least(now()+interval '30 seconds',deadline)
        where id=? and fence=? and state='RUNNING' and lease_until>now() and deadline>now()
        """,
            id,
            fence);
    if (changed != 1) throw new WorkspaceConflict("Investigation lease ended");
  }

  @Transactional
  public com.goldys.platform.auth.UserRole authorizedRole(UUID id, UUID fence) {
    heartbeat(id, fence);
    return authorization.role(thread(threadId(id)).owner());
  }

  @Transactional
  public void publish(UUID id, UUID fence, long revision, JsonNode snapshot, boolean completed) {
    validateSnapshot(snapshot);
    UUID threadId = threadId(id);
    ThreadRow thread = thread(threadId);
    checkPolicy(thread, PermissionAction.WRITE);
    if (completed
        && jdbc.queryForObject(
                "select count(*) from workspace_follow_up where job_id=? and state='QUEUED'",
                Integer.class,
                id)
            > 0) throw new WorkspaceConflict("Queued follow-ups must be settled");
    int valid =
        jdbc.update(
            """
        update workspace_job set state=?,lease_until=least(now()+interval '30 seconds',deadline)
        where id=? and fence=? and state='RUNNING' and lease_until>now() and deadline>now()
        """,
            completed ? "COMPLETED" : "RUNNING",
            id,
            fence);
    if (valid != 1) throw new WorkspaceConflict("Investigation lease ended");
    int changed =
        jdbc.update(
            "update workspace_thread set snapshot=?::jsonb,revision=revision+1 where id=? and revision=?",
            snapshot.toString(),
            threadId,
            revision);
    if (changed != 1) throw new WorkspaceConflict("Stale conversation revision");
  }

  private ThreadRow owned(UUID owner, UUID id, PermissionAction action) {
    ThreadRow thread = thread(id);
    if (!owner.equals(thread.owner()))
      throw AccessDeniedException.forResource("workspace.investigations");
    checkPolicy(thread, action);
    return thread;
  }

  private void checkPolicy(ThreadRow thread, PermissionAction action) {
    if (!authorization.require(thread.owner(), action).equals(thread.policy())) {
      throw new AccessDeniedException("Conversation access changed. Start a new conversation.");
    }
  }

  private ThreadRow thread(UUID id) {
    var rows =
        jdbc.query(
            "select user_account_id,policy_stamp,revision,snapshot::text from workspace_thread where id=? for update",
            (row, index) ->
                new ThreadRow(
                    row.getObject(1, UUID.class),
                    row.getString(2),
                    row.getLong(3),
                    row.getString(4)),
            id);
    if (rows.isEmpty()) throw AccessDeniedException.forResource("workspace.investigations");
    return rows.getFirst();
  }

  private UUID threadId(UUID id) {
    var rows =
        jdbc.query(
            "select thread_id from workspace_job where id=?",
            (row, index) -> row.getObject(1, UUID.class),
            id);
    if (rows.isEmpty()) throw AccessDeniedException.forResource("workspace.investigations");
    return rows.getFirst();
  }

  private void interruptExpired(UUID threadId) {
    jdbc.update(
        """
        update workspace_job set state='INTERRUPTED',fence=gen_random_uuid(),lease_until=null
        where thread_id=? and ((state='RUNNING' and lease_until<=now()) or (state in ('QUEUED','RUNNING') and deadline<=now()))
        """,
        threadId);
    jdbc.update(
        "update workspace_follow_up set state='CANCELLED' where state='QUEUED' and job_id in (select id from workspace_job where thread_id=? and state='INTERRUPTED')",
        threadId);
  }

  private JsonNode parse(String value) {
    if (value == null) return null;
    try {
      return json.readTree(value);
    } catch (Exception invalid) {
      throw new IllegalStateException("Invalid stored Pi snapshot", invalid);
    }
  }

  private static void validateSnapshot(JsonNode value) {
    if (value == null
        || value.toString().length() > 8_000_000
        || !"1.5.2".equals(value.path("piVersion").asText())
        || !value.path("entries").isArray()
        || !"session".equals(value.path("entries").path(0).path("type").asText())
        || !value.has("leafId")) throw new IllegalArgumentException("Invalid Pi snapshot");
  }
}
