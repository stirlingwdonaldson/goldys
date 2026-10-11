package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.PlatformMapService;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.NetworkEdgeDto;
import com.goldys.platform.semantic.NetworkGraphDto;
import com.goldys.platform.semantic.NetworkNodeDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PlatformMapController.class)
@Import(SecurityConfig.class)
class PlatformMapControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean PlatformMapService platformMap;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void returnsTheGraphForTheCallersRoleAndEmail() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(platformMap.map(ownerRole(), "owner@example.com"))
        .thenReturn(
            new NetworkGraphDto(
                List.of(
                    new NetworkNodeDto(
                        "source:CTB",
                        "source",
                        "Cooking the Books",
                        "Last run failed",
                        "fail",
                        6,
                        "source",
                        "/logs")),
                List.of(new NetworkEdgeDto("source:CTB", "domain:x", 1, "feeds"))));

    mvc.perform(get("/api/platform-map").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes[0].id").value("source:CTB"))
        .andExpect(jsonPath("$.nodes[0].tone").value("fail"))
        .andExpect(jsonPath("$.edges[0].label").value("feeds"));
  }

  @Test
  void deniedCallersGetNotPermitted() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(platformMap.map(any(), eq("owner@example.com")))
        .thenThrow(AccessDeniedException.forResource("connectors"));

    mvc.perform(get("/api/platform-map").with(authenticated(owner())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
