package com.goldys.platform.api;

import com.goldys.platform.application.SavedQueryApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.QueryResult;
import com.goldys.platform.semantic.query.SaveQueryRequest;
import com.goldys.platform.semantic.query.SavedQueryDetail;
import com.goldys.platform.semantic.query.SavedQueryRevisionView;
import com.goldys.platform.semantic.query.SavedQuerySummary;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Saved query definitions: owner-scoped CRUD plus a re-validated, re-authorized run. */
@RestController
@RequestMapping("/api/query/saved")
public class SavedQueryController {
  private final SavedQueryApplicationService queries;
  private final CurrentUserService currentUser;

  public SavedQueryController(
      SavedQueryApplicationService queries, CurrentUserService currentUser) {
    this.queries = queries;
    this.currentUser = currentUser;
  }

  @GetMapping
  List<SavedQuerySummary> list(@AuthenticationPrincipal AccountUserDetails user) {
    return queries.list(user.getUsername());
  }

  @PostMapping
  SavedQueryDetail create(
      @RequestBody SaveQueryRequest body, @AuthenticationPrincipal AccountUserDetails user) {
    return queries.create(user.getUsername(), body.title(), body.plan());
  }

  @GetMapping("/{id}")
  SavedQueryDetail get(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return queries.get(user.getUsername(), id);
  }

  @PutMapping("/{id}")
  SavedQueryDetail update(
      @PathVariable UUID id,
      @RequestBody SaveQueryRequest body,
      @AuthenticationPrincipal AccountUserDetails user) {
    return queries.update(user.getUsername(), id, body.title(), body.plan());
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    queries.delete(user.getUsername(), id);
  }

  @GetMapping("/{id}/revisions")
  List<SavedQueryRevisionView> revisions(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return queries.revisions(user.getUsername(), id);
  }

  @PostMapping("/{id}/restore")
  SavedQueryDetail restore(
      @PathVariable UUID id,
      @RequestParam int revision,
      @AuthenticationPrincipal AccountUserDetails user) {
    return queries.restore(user.getUsername(), id, revision);
  }

  @PostMapping("/{id}/run")
  QueryResult run(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    return queries.run(role, user.getUsername(), id);
  }
}
