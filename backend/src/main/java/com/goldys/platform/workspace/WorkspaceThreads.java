package com.goldys.platform.workspace;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.WorkspaceAuthorization;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owned investigation discovery; restricted rows never disclose prompts or attempt metadata. */
@Service
public class WorkspaceThreads {
  public record Summary(
      UUID id, Instant createdAt, String access, UUID latestJobId, String title, String state) {}

  public record Page(List<Summary> threads, UUID nextCursor) {}

  private final JdbcTemplate jdbc;
  private final WorkspaceAuthorization authorization;

  public WorkspaceThreads(JdbcTemplate jdbc, WorkspaceAuthorization authorization) {
    this.jdbc = jdbc;
    this.authorization = authorization;
  }

  /** Deletion checks write access and ownership without disclosing restricted history. */
  @Transactional
  public void delete(UUID owner, UUID id) {
    authorization.require(owner, PermissionAction.WRITE);
    var owned =
        jdbc.queryForList(
            "select id from workspace_thread where id=? and user_account_id=? for update",
            UUID.class,
            id,
            owner);
    if (owned.isEmpty()) throw AccessDeniedException.forResource("workspace.investigations");
    // Cascades remove snapshots, attempts, queue and file bytes. Daily charges remain reserved.
    jdbc.update("delete from workspace_thread where id=?", id);
  }

  @Transactional
  public Page list(UUID owner, UUID before, int limit) {
    if (limit < 1 || limit > 50) throw new IllegalArgumentException("Page size must be 1–50");
    String policy = authorization.require(owner, PermissionAction.READ);
    boolean recovery = authorization.canRecoverArchive(owner);
    Instant createdBefore = null;
    if (before != null) {
      var anchors =
          jdbc.query(
              "select created_at from workspace_thread where id=? and user_account_id=?",
              (row, index) -> row.getTimestamp(1).toInstant(),
              before,
              owner);
      if (anchors.isEmpty()) throw AccessDeniedException.forResource("workspace.investigations");
      createdBefore = anchors.getFirst();
    }
    var rows =
        jdbc.query(
            """
        select t.id,t.created_at,t.policy_stamp,j.id as latest_job_id,left(j.prompt,200) as prompt,j.state
        from workspace_thread t
        left join lateral (select id,prompt,state from workspace_job where thread_id=t.id
          order by created_at desc,id desc limit 1) j on true
        where t.user_account_id=?
          and (?::uuid is null or (t.created_at,t.id)<(?::timestamptz,?::uuid))
        order by t.created_at desc,t.id desc limit ?
        """,
            (row, index) -> {
              boolean permitted = policy.equals(row.getString("policy_stamp")) || recovery;
              return new Summary(
                  row.getObject("id", UUID.class),
                  row.getTimestamp("created_at").toInstant(),
                  permitted ? "AVAILABLE" : "NOT_PERMITTED",
                  permitted ? row.getObject("latest_job_id", UUID.class) : null,
                  permitted ? row.getString("prompt") : null,
                  permitted ? row.getString("state") : null);
            },
            owner,
            before,
            createdBefore == null ? null : java.sql.Timestamp.from(createdBefore),
            before,
            limit + 1);
    boolean more = rows.size() > limit;
    var page = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
    return new Page(page, more ? page.getLast().id() : null);
  }
}
