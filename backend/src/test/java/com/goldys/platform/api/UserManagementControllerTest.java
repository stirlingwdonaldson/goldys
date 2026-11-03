package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.ManagedUserSummary;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserManagementService;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
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

@WebMvcTest(UserManagementController.class)
@Import(SecurityConfig.class)
class UserManagementControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean UserManagementService users;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void listReturnsUsers() throws Exception {
    AccountUserDetails owner = owner();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(users.list(any(), any()))
        .thenReturn(
            List.of(
                new ManagedUserSummary(
                    UUID.randomUUID(),
                    "a@b.com",
                    "A B",
                    true,
                    Instant.parse("2026-11-03T00:00:00Z"),
                    false)));

    mvc.perform(get("/api/users").with(authenticated(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("a@b.com"))
        .andExpect(jsonPath("$[0].active").value(true));
  }

  @Test
  void createReturnsCreatedUser() throws Exception {
    AccountUserDetails owner = owner();
    ManagedUserSummary created =
        new ManagedUserSummary(
            UUID.randomUUID(),
            "new@example.com",
            "New User",
            true,
            Instant.parse("2026-11-03T00:00:00Z"),
            false);
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(users.create(any(), any(), eq("new@example.com"), eq("New User"), eq("password123")))
        .thenReturn(created);

    mvc.perform(
            post("/api/users")
                .with(authenticated(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"new@example.com\",\"displayName\":\"New User\","
                        + "\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("new@example.com"));
  }

  @Test
  void updateReturnsTheUpdatedUser() throws Exception {
    AccountUserDetails owner = owner();
    UUID id = UUID.randomUUID();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(users.update(any(), any(), eq(id), eq("Renamed"), eq(false)))
        .thenReturn(
            new ManagedUserSummary(
                id, "a@b.com", "Renamed", false, Instant.parse("2026-11-03T00:00:00Z"), false));

    mvc.perform(
            patch("/api/users/{id}", id)
                .with(authenticated(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Renamed\",\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Renamed"))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void resetPasswordReturnsNoContent() throws Exception {
    AccountUserDetails owner = owner();
    UUID id = UUID.randomUUID();
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(
            post("/api/users/{id}/password-reset", id)
                .with(authenticated(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newpassword123\"}"))
        .andExpect(status().isNoContent());

    verify(users).resetPassword(any(), eq(id), eq("newpassword123"));
  }

  @Test
  void anonymousRequestsAreRejected() throws Exception {
    // Session auth redirects browser requests to the login page; the frontend maps that to
    // AUTH_REQUIRED. Do not change the app-wide entry point for this one endpoint.
    mvc.perform(get("/api/users")).andExpect(status().is3xxRedirection());
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true, false);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
