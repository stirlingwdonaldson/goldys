package com.goldys.platform.api;

import com.goldys.platform.application.SavedDashboardApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
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
    return dashboards.list(currentUser.roleOf(user));
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
    return dashboards.get(currentUser.roleOf(user), id);
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
    dashboards.delete(currentUser.roleOf(user), id);
  }

  /** Re-runs the dashboard's stored queries and returns current widget specs. */
  @GetMapping("/{id}/render")
  List<SavedDashboardApplicationService.RenderedWidget> render(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return dashboards.render(currentUser.roleOf(user), user.email(), id);
  }
}
