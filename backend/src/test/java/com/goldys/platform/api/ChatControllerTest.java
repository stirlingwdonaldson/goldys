package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.conversational.ChatController;
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

@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void deniesANonOwner() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(managerRole());
    doThrow(AccessDeniedException.forResource("conversational.chat"))
        .when(permissions)
        .require(any(), any(), any());

    mvc.perform(
            post("/api/conversational/chat")
                .with(authenticated(manager()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsNotConfiguredWhenNoAssistantServiceIsPresent() throws Exception {
    // No AssistantService bean exists in this @WebMvcTest slice, so the real
    // ObjectProvider<AssistantService> is empty and the endpoint returns 503.
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(
            post("/api/conversational/chat")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
        .andExpect(status().isServiceUnavailable());
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static AccountUserDetails manager() {
    return new AccountUserDetails(
        UUID.randomUUID(), "m@example.com", "hash", "Manager", "BOH", "MANAGER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static UserRole managerRole() {
    return new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
