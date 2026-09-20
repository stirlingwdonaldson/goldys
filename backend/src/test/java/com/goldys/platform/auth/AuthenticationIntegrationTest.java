package com.goldys.platform.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class AuthenticationIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthService authService;
  @Autowired UserAccountRepository accounts;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table user_account");
  }

  @Test
  void signupLoginAndMeAreCaseInsensitiveAndPersistTheSession() throws Exception {
    authService.signup("Stirling@Donaldson.com", "Stirling Donaldson", "password123");

    MockHttpSession session = new MockHttpSession();
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "stirling@donaldson.com")
                .param("password", "password123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Stirling Donaldson"));

    mvc.perform(get("/api/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seniority").value("STAFF"));
  }

  @Test
  void inactiveAccountCannotLogin() throws Exception {
    authService.signup("a@b.com", "A B", "password123");
    accounts
        .findByEmail("a@b.com")
        .ifPresent(a -> jdbc.update("update user_account set active = false where id = ?", a.id()));

    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "a@b.com")
                .param("password", "password123"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void freshStaffIsExplicitlyDeniedOnReconciliation() throws Exception {
    authService.signup("staff@example.com", "Staff", "password123");

    MockHttpSession session = new MockHttpSession();
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "staff@example.com")
                .param("password", "password123"))
        .andExpect(status().isOk());

    // A STAFF account has no permission grant, so the read is explicitly denied (403), not empty.
    mvc.perform(get("/api/reconciliation/exceptions").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }
}
