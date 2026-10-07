package com.goldys.platform.api;

import com.goldys.platform.application.SavedDashboardApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.dashboard.DashboardTemplate;
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
import org.springframework.web.bind.annotation.RestController;

/** Saved dashboards: declarative documents rendered through the shared widget runtime. */
@RestController
@RequestMapping("/api/dashboards")
public class SavedDashboardController {
  private final SavedDashboardApplicationService dashboards;
  private final CurrentUserService currentUser;

  public SavedDashboardController(
      SavedDashboardApplicationService dashboards, CurrentUserService currentUser) {
    this.dashboards = dashboards;
    this.currentUser = currentUser;
  }

  @GetMapping
  List<SavedDashboardApplicationService.DashboardSummary> list(
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.list(currentUser.roleOf(user), user.email());
  }

  @PostMapping
  SavedDashboardApplicationService.DashboardDocument create(
      @RequestBody SavedDashboardApplicationService.DashboardInput body,
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.create(currentUser.roleOf(user), user.email(), body);
  }

  @GetMapping("/{id}")
  SavedDashboardApplicationService.DashboardDocument get(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.get(currentUser.roleOf(user), user.email(), id);
  }

  @PutMapping("/{id}")
  SavedDashboardApplicationService.DashboardDocument update(
      @PathVariable UUID id,
      @RequestBody SavedDashboardApplicationService.DashboardInput body,
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.update(currentUser.roleOf(user), user.email(), id, body);
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    dashboards.delete(currentUser.roleOf(user), user.email(), id);
  }

  /** Re-runs the dashboard's stored queries and returns current widget specs. */
  @GetMapping("/{id}/render")
  List<SavedDashboardApplicationService.RenderedWidget> render(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.render(currentUser.roleOf(user), user.email(), id);
  }

  /** The nine code-based starting-point templates. */
  @GetMapping("/templates")
  List<DashboardTemplate> templates(@AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.templates();
  }

  /** Copies a template into a new PRIVATE dashboard owned by the caller. */
  @PostMapping("/from-template/{templateId}")
  SavedDashboardApplicationService.DashboardDocument fromTemplate(
      @PathVariable String templateId, @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.instantiate(currentUser.roleOf(user), user.email(), templateId);
  }

  /** Revision history (restricted to viewers of the dashboard). */
  @GetMapping("/{id}/revisions")
  List<SavedDashboardApplicationService.DashboardRevisionSummary> revisions(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.revisions(currentUser.roleOf(user), user.email(), id);
  }

  /** Restores a revision (writes a new revision). */
  @PostMapping("/{id}/revisions/{rev}/restore")
  SavedDashboardApplicationService.DashboardDocument restore(
      @PathVariable UUID id,
      @PathVariable int rev,
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.restore(currentUser.roleOf(user), user.email(), id, rev);
  }

  /** Toggles the pinned (favourite) flag. */
  @PutMapping("/{id}/pin")
  SavedDashboardApplicationService.DashboardDocument pin(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.pin(currentUser.roleOf(user), user.email(), id);
  }

  /** Visibility plus the role list that can open a SHARED dashboard. */
  @GetMapping("/{id}/sharing")
  SavedDashboardApplicationService.DashboardSharing sharing(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.sharing(currentUser.roleOf(user), user.email(), id);
  }

  @PutMapping("/{id}/sharing")
  SavedDashboardApplicationService.DashboardSharing setSharing(
      @PathVariable UUID id,
      @RequestBody SavedDashboardApplicationService.DashboardSharing body,
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.setSharing(
        currentUser.roleOf(user), user.email(), id, body.visibility(), body.roles());
  }
}
