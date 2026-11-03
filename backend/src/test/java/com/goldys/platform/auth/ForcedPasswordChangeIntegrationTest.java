package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.support.PostgresContainerConfiguration;
import com.goldys.platform.support.TestAccounts;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class ForcedPasswordChangeIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TestAccounts testAccounts;
  @Autowired SessionRegistry sessionRegistry;
  @Autowired UserManagementService users;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table user_account cascade");
  }

  @Test
  void aFlaggedUserIsBlockedUntilThePasswordChanges() throws Exception {
    Login login = flaggedOwnerSession("new@example.com");

    mvc.perform(get("/api/users").session(login.session()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

    mvc.perform(
            post("/api/auth/change-password")
                .session(login.session())
                .cookie(login.xsrf())
                .header("X-XSRF-TOKEN", login.xsrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword123\"}"))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/me").session(login.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false));

    mvc.perform(get("/api/users").session(login.session())).andExpect(status().isOk());
  }

  @Test
  void deactivationExpiresTheUsersLiveSessions() throws Exception {
    Login ownerLogin = flaggedOwnerSession("owner@example.com");
    UUID ownerId = idOf("owner@example.com");
    testAccounts.createOwner("second@example.com", "Second", "password123");
    UUID secondId = idOf("second@example.com");
    UserRole ownerRole = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

    users.update(ownerRole, secondId, ownerId, null, false);

    // A browser sends JSESSIONID as the requested session id; MockMvc's .session() alone does not.
    mvc.perform(get("/api/me").with(requestedSession(ownerLogin.session())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

    List<SessionInformation> ownerSessions =
        sessionRegistry.getAllPrincipals().stream()
            .filter(
                principal ->
                    principal instanceof AccountUserDetails user && user.id().equals(ownerId))
            .flatMap(principal -> sessionRegistry.getAllSessions(principal, true).stream())
            .toList();
    assertThat(ownerSessions).isNotEmpty().allMatch(SessionInformation::isExpired);
  }

  @Test
  void resettingAPasswordExpiresTheUsersLiveSessions() throws Exception {
    Login ownerLogin = flaggedOwnerSession("owner@example.com");
    UUID ownerId = idOf("owner@example.com");
    testAccounts.createOwner("second@example.com", "Second", "password123");
    UUID secondId = idOf("second@example.com");
    UserRole ownerRole = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

    users.resetPassword(ownerRole, ownerId, "newpassword123");

    // A browser sends JSESSIONID as the requested session id; MockMvc's .session() alone does not.
    mvc.perform(get("/api/me").with(requestedSession(ownerLogin.session())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    assertThat(sessionRegistry.getAllPrincipals()).isNotEmpty();
    assertThat(secondId).isNotNull();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor requestedSession(
      MockHttpSession session) {
    return request -> {
      request.setSession(session);
      request.setRequestedSessionId(session.getId());
      return request;
    };
  }

  private Login flaggedOwnerSession(String email) throws Exception {
    testAccounts.createOwner(email, "Owner", "password123");
    jdbc.update("update user_account set must_change_password = true where email = ?", email);

    MockHttpSession session = new MockHttpSession();
    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .session(session)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("email", email)
                    .param("password", "password123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn();
    return new Login(session, login.getResponse().getCookie("XSRF-TOKEN"));
  }

  private UUID idOf(String email) {
    return UUID.fromString(
        jdbc.queryForObject(
            "select id::text from user_account where email = ?", String.class, email));
  }

  private record Login(MockHttpSession session, Cookie xsrf) {}
}
