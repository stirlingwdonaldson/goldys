package com.goldys.platform.workspace;

import com.goldys.platform.auth.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Private exploratory bytes inherit the entire thread's conservative policy stamp. */
@Service
public class WorkspaceFiles {
  public record FileInfo(
      UUID id,
      String name,
      String kind,
      long byteLength,
      boolean kept,
      Instant expiresAt,
      boolean available) {}

  public record FileBytes(FileInfo info, byte[] bytes) {}

  private final WorkspaceJobs jobs;
  private final WorkspaceAuthorization authorization;
  private final PermissionService permissions;
  private final JdbcTemplate jdbc;
  private final boolean enabled;

  public WorkspaceFiles(
      WorkspaceJobs jobs,
      WorkspaceAuthorization authorization,
      PermissionService permissions,
      JdbcTemplate jdbc,
      @org.springframework.beans.factory.annotation.Value("${app.workspace.files-enabled:true}")
          boolean enabled) {
    this.jobs = jobs;
    this.authorization = authorization;
    this.permissions = permissions;
    this.jdbc = jdbc;
    this.enabled = enabled;
  }

  private void requireEnabled() {
    if (!enabled) throw new WorkspaceConflict("Private files are not enabled for this deployment");
  }

  private void authorize(UUID owner, UUID job, PermissionAction action) {
    requireEnabled();
    jobs.read(owner, job);
    permissions.require(
        authorization.role(owner), new ResourceKey("workspace.private-files"), action);
    // Unknown file content may include pay and account/person information. A file
    // grant never bypasses the existing sensitive-resource checks.
    permissions.require(
        authorization.role(owner), new ResourceKey("labour.wages"), PermissionAction.READ);
    permissions.require(authorization.role(owner), new ResourceKey("users"), PermissionAction.READ);
  }

  @Transactional
  public List<FileInfo> list(UUID owner, UUID job) {
    authorize(owner, job, PermissionAction.READ);
    expire();
    return infos(job);
  }

  private List<FileInfo> infos(UUID job) {
    return jdbc.query(
        "select id,name,kind,byte_length,kept,expires_at,payload is not null from workspace_file where job_id in (select id from workspace_job where thread_id=(select thread_id from workspace_job where id=?)) order by created_at,id",
        (row, index) ->
            new FileInfo(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getLong(4),
                row.getBoolean(5),
                row.getTimestamp(6).toInstant(),
                row.getBoolean(7)),
        job);
  }

  @Transactional
  public FileInfo upload(UUID owner, UUID job, String name, byte[] bytes) {
    authorize(owner, job, PermissionAction.WRITE);
    return store(job, name, bytes, "UPLOAD");
  }

  @Transactional
  public List<FileBytes> inputs(UUID job, UUID fence) {
    return inputs(job, fence, null);
  }

  @Transactional
  public List<FileBytes> inputs(UUID job, UUID fence, List<UUID> selected) {
    requireEnabled();
    var role = jobs.authorizedRole(job, fence);
    permissions.require(role, new ResourceKey("workspace.private-files"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("labour.wages"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("users"), PermissionAction.READ);
    expire();
    var available = infos(job).stream().filter(FileInfo::available).toList();
    if (selected != null
        && (selected.size() > 5
            || selected.stream().distinct().count() != selected.size()
            || selected.stream()
                .anyMatch(id -> available.stream().noneMatch(info -> info.id().equals(id)))))
      throw AccessDeniedException.forResource("workspace.private-files");
    if (selected == null && available.size() > 5)
      throw new WorkspaceConflict("Choose at most five input file IDs from the file list");
    var result =
        available.stream()
            .filter(info -> selected == null || selected.contains(info.id()))
            .filter(info -> info.available())
            .map(
                info ->
                    new FileBytes(
                        info,
                        jdbc.queryForObject(
                            "select payload from workspace_file where id=?",
                            byte[].class,
                            info.id())))
            .toList();
    jobs.authorizedRole(job, fence);
    return result;
  }

  @Transactional
  public List<FileInfo> workerList(UUID job, UUID fence) {
    requireEnabled();
    var role = jobs.authorizedRole(job, fence);
    permissions.require(role, new ResourceKey("workspace.private-files"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("labour.wages"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("users"), PermissionAction.READ);
    expire();
    return infos(job);
  }

  @Transactional
  public FileInfo artifact(UUID job, UUID fence, String name, byte[] bytes) {
    requireEnabled();
    var role = jobs.authorizedRole(job, fence);
    permissions.require(role, new ResourceKey("workspace.private-files"), PermissionAction.WRITE);
    FileInfo info = store(job, name, bytes, "ARTIFACT");
    jobs.authorizedRole(job, fence);
    return info;
  }

  private FileInfo store(UUID job, String name, byte[] bytes, String kind) {
    if (name == null
        || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")
        || !name.toLowerCase(java.util.Locale.ROOT)
            .matches(".*\\.(pdf|png|jpg|jpeg|docx|xlsx|csv|tsv|txt)"))
      throw new IllegalArgumentException(
          "Use PDF, PNG/JPEG, DOCX, XLSX, CSV/TSV or text with a simple filename");
    if (bytes == null || bytes.length == 0 || bytes.length > 25 * 1024 * 1024)
      throw new IllegalArgumentException("Files must contain 1 byte to 25 MB");
    // Serialize global storage admission. This pilot keeps bytes and manifests in
    // one PostgreSQL transaction, so restore cannot split their generations.
    jdbc.execute("select pg_advisory_xact_lock(7142121)");
    expire();
    Long count =
        jdbc.queryForObject(
            "select count(*) from workspace_file where job_id in (select id from workspace_job where thread_id=(select thread_id from workspace_job where id=?)) and kind=?",
            Long.class,
            job,
            kind);
    Long total =
        jdbc.queryForObject(
            "select coalesce(sum(byte_length),0) from workspace_file where job_id in (select id from workspace_job where thread_id=(select thread_id from workspace_job where id=?)) and kind=?",
            Long.class,
            job,
            kind);
    Long stored =
        jdbc.queryForObject(
            "select coalesce(sum(byte_length),0) from workspace_file where payload is not null",
            Long.class);
    if (count >= 5 || total + bytes.length > 100 * 1024 * 1024L)
      throw new WorkspaceConflict("Investigation file limit reached (five files, 100 MB)");
    if (stored + bytes.length > 1024 * 1024 * 1024L)
      throw new WorkspaceConflict("Private workspace storage is full");
    if (infos(job).stream().anyMatch(info -> info.available() && info.name().equals(name)))
      throw new WorkspaceConflict(
          "A retained file already uses this name; choose a distinct filename");
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          "insert into workspace_file(id,job_id,name,kind,payload,byte_length,sha256) values (?,?,?,?,?,?,?)",
          id,
          job,
          name,
          kind,
          bytes,
          bytes.length,
          MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    return infos(job).stream().filter(info -> info.id().equals(id)).findFirst().orElseThrow();
  }

  @Transactional
  public FileBytes download(UUID owner, UUID job, UUID file) {
    authorize(owner, job, PermissionAction.READ);
    expire();
    FileInfo info =
        infos(job).stream()
            .filter(item -> item.id().equals(file))
            .findFirst()
            .orElseThrow(() -> AccessDeniedException.forResource("workspace.private-files"));
    if (!info.available()) throw new WorkspaceConflict("This file has expired or was deleted");
    byte[] bytes =
        jdbc.queryForObject("select payload from workspace_file where id=?", byte[].class, file);
    return new FileBytes(info, bytes);
  }

  @Transactional
  public void keep(UUID owner, UUID job, UUID file, boolean keep) {
    authorize(owner, job, PermissionAction.WRITE);
    expire();
    int changed =
        jdbc.update(
            "update workspace_file set kept=? where id=? and job_id in (select id from workspace_job where thread_id=(select thread_id from workspace_job where id=?)) and payload is not null",
            keep,
            file,
            job);
    if (changed != 1) throw new WorkspaceConflict("File is unavailable");
  }

  @Transactional
  public void delete(UUID owner, UUID job, UUID file) {
    authorize(owner, job, PermissionAction.WRITE);
    jdbc.update(
        "update workspace_file set payload=null,kept=false where id=? and job_id in (select id from workspace_job where thread_id=(select thread_id from workspace_job where id=?))",
        file,
        job);
  }

  @Transactional
  public void expire() {
    jdbc.update(
        "update workspace_file set payload=null where payload is not null and not kept and expires_at<=clock_timestamp()");
  }
}
