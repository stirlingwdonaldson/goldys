package com.goldys.platform.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class OidcAccessIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void unknownOidcIdentityIsDeniedExplicitly() throws Exception {
    mvc.perform(
            get("/api/me")
                .with(
                    oidcLogin()
                        .idToken(token -> token.issuer("https://id.example").subject("unknown"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void inactiveProfileIsDeniedExplicitly() throws Exception {
    insertProfile("https://id.example", "inactive-user", "Alex", "FOH", "MANAGER", false);

    mvc.perform(
            get("/api/me")
                .with(
                    oidcLogin()
                        .idToken(
                            token -> token.issuer("https://id.example").subject("inactive-user"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void activeProfileIsReturned() throws Exception {
    insertProfile("https://id.example", "active-user", "Alex", "FOH", "MANAGER", true);

    mvc.perform(
            get("/api/me")
                .with(
                    oidcLogin()
                        .idToken(
                            token -> token.issuer("https://id.example").subject("active-user"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Alex"))
        .andExpect(jsonPath("$.department").value("FOH"))
        .andExpect(jsonPath("$.seniority").value("MANAGER"));
  }

  private void insertProfile(
      String issuer,
      String subject,
      String name,
      String department,
      String seniority,
      boolean active) {
    jdbc.update(
        "insert into staff_profile "
            + "(id,oidc_issuer,oidc_subject,display_name,department,seniority,active,"
            + "created_at,updated_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, now(), now())",
        UUID.randomUUID(),
        issuer,
        subject,
        name,
        department,
        seniority,
        active);
  }
}
