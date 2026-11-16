package com.goldys.platform.workspace;

import com.goldys.platform.auth.AccountUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace/jobs/{job}/files")
@ConditionalOnProperty(name = "app.workspace.enabled", havingValue = "true")
public class WorkspaceFileController {
  private final WorkspaceFiles files;

  public WorkspaceFileController(WorkspaceFiles files) {
    this.files = files;
  }

  @GetMapping
  List<WorkspaceFiles.FileInfo> list(
      @AuthenticationPrincipal AccountUserDetails user, @PathVariable UUID job) {
    return files.list(user.id(), job);
  }

  @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  WorkspaceFiles.FileInfo upload(
      @AuthenticationPrincipal AccountUserDetails user,
      @PathVariable UUID job,
      @RequestParam String name,
      jakarta.servlet.http.HttpServletRequest request)
      throws java.io.IOException {
    byte[] bytes = request.getInputStream().readNBytes(25 * 1024 * 1024 + 1);
    return files.upload(user.id(), job, name, bytes);
  }

  @GetMapping("/{file}")
  ResponseEntity<byte[]> download(
      @AuthenticationPrincipal AccountUserDetails user,
      @PathVariable UUID job,
      @PathVariable UUID file) {
    var result = files.download(user.id(), job, file);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(result.info().name()).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(result.bytes());
  }

  @PutMapping("/{file}/keep")
  void keep(
      @AuthenticationPrincipal AccountUserDetails user,
      @PathVariable UUID job,
      @PathVariable UUID file,
      @RequestParam boolean keep) {
    files.keep(user.id(), job, file, keep);
  }

  @DeleteMapping("/{file}")
  void delete(
      @AuthenticationPrincipal AccountUserDetails user,
      @PathVariable UUID job,
      @PathVariable UUID file) {
    files.delete(user.id(), job, file);
  }
}
