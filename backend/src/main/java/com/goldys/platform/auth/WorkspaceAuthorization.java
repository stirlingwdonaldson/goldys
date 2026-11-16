package com.goldys.platform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Fresh account and policy checks, including on worker requests without a browser session. */
@Service
public class WorkspaceAuthorization {
  private static final ResourceKey RESOURCE = new ResourceKey("workspace.investigations");
  private final JdbcTemplate jdbc;
  private final PermissionService permissions;

  public WorkspaceAuthorization(JdbcTemplate jdbc, PermissionService permissions) {
    this.jdbc = jdbc;
    this.permissions = permissions;
  }

  public UserRole role(UUID accountId) {
    require(accountId, PermissionAction.READ);
    return jdbc.queryForObject(
        "select department,seniority from user_account where id=?",
        (row, index) ->
            new UserRole(new DepartmentCode(row.getString(1)), new SeniorityCode(row.getString(2))),
        accountId);
  }

  public String require(UUID accountId, PermissionAction action) {
    return require(accountId, RESOURCE, action);
  }

  /** Shared conservative history stamp; each caller retains its own permission resource. */
  public String require(UUID accountId, ResourceKey resource, PermissionAction action) {
    var accounts =
        jdbc.query(
            """
        select department, seniority, workspace_policy_revision::text from user_account
        where id = ? and active and not must_change_password for share
        """,
            (row, index) -> new String[] {row.getString(1), row.getString(2), row.getString(3)},
            accountId);
    if (accounts.isEmpty()) throw AccessDeniedException.forResource(resource.value());
    var account = accounts.getFirst();
    var role = new UserRole(new DepartmentCode(account[0]), new SeniorityCode(account[1]));
    // shortcut: any grant/account change invalidates the whole context; replace with precise
    // dependency reauthorization only after provenance covers every summary and file.
    var policy =
        jdbc.query(
            """
        select revision::text from workspace_role_policy
        where department = ? and seniority = ? for share
        """,
            (row, index) -> row.getString(1),
            account[0],
            account[1]);
    if (policy.isEmpty()) throw AccessDeniedException.forResource(resource.value());
    permissions.require(role, resource, action);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((String.join("|", account) + policy).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
