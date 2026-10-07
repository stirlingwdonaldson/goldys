package com.goldys.platform.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.SavedDashboardApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.DashboardTemplateCatalog;
import com.goldys.platform.dashboard.Visibility;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SavedDashboardController.class)
@Import(SecurityConfig.class)
class SavedDashboardControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean SavedDashboardApplicationService dashboards;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void listTemplates() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboards.templates()).thenReturn(new DashboardTemplateCatalog().templates());

    mvc.perform(get("/api/dashboards/templates").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(9)));
  }

  @Test
  void fromTemplateInstantiatesAsPrivateDashboard() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboards.instantiate(any(), any(), any())).thenReturn(document("daily"));

    mvc.perform(
            post("/api/dashboards/from-template/daily").with(authenticated(owner())).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("daily"));
  }

  @Test
  void revisionsDelegatesWithVisibility() throws Exception {
    var id = UUID.randomUUID();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboards.revisions(any(), any(), any()))
        .thenReturn(
            List.of(
                new SavedDashboardApplicationService.DashboardRevisionSummary(
                    2, "owner@example.com", Instant.EPOCH)));

    mvc.perform(get("/api/dashboards/" + id + "/revisions").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].revision").value(2));
  }

  @Test
  void restoreDelegatesWithRevision() throws Exception {
    var id = UUID.randomUUID();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboards.restore(any(), any(), any(), anyInt())).thenReturn(document("original"));

    mvc.perform(
            post("/api/dashboards/" + id + "/revisions/1/restore")
                .with(authenticated(owner()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("original"));
  }

  @Test
  void pinReturnsToggledDocument() throws Exception {
    var id = UUID.randomUUID();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboards.pin(any(), any(), any())).thenReturn(document("sales", true));

    mvc.perform(put("/api/dashboards/" + id + "/pin").with(authenticated(owner())).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pinned").value(true));
  }

  @Test
  void getSharingReturnsVisibilityAndRoles() throws Exception {
    var id = UUID.randomUUID();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboards.sharing(any(), any(), any()))
        .thenReturn(
            new SavedDashboardApplicationService.DashboardSharing(
                Visibility.SHARED,
                List.of(new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER")))));

    mvc.perform(get("/api/dashboards/" + id + "/sharing").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("SHARED"))
        .andExpect(jsonPath("$.roles", hasSize(1)));
  }

  @Test
  void setSharingUpdatesVisibilityAndRoles() throws Exception {
    var id = UUID.randomUUID();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboards.setSharing(any(), any(), any(), any(), any()))
        .thenReturn(
            new SavedDashboardApplicationService.DashboardSharing(
                Visibility.SHARED,
                List.of(new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER")))));

    mvc.perform(
            put("/api/dashboards/" + id + "/sharing")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"visibility\":\"SHARED\",\"roles\":[{\"department\":{\"value\":\"BOH\"},\"seniority\":{\"value\":\"MANAGER\"}}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("SHARED"))
        .andExpect(jsonPath("$.roles", hasSize(1)));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of());
    return authentication(auth);
  }

  private static SavedDashboardApplicationService.DashboardDocument document(String title) {
    return document(title, false);
  }

  private static SavedDashboardApplicationService.DashboardDocument document(
      String title, boolean pinned) {
    return new SavedDashboardApplicationService.DashboardDocument(
        UUID.randomUUID(),
        2,
        title,
        null,
        "grid",
        List.of(),
        DashboardFilters.empty(),
        Visibility.PRIVATE,
        pinned,
        "owner@example.com",
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
