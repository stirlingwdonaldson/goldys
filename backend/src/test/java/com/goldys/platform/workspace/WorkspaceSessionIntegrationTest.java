package com.goldys.platform.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.support.PostgresContainerConfiguration;
import com.goldys.platform.support.TestAccounts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "app.workspace.enabled=true",
      "app.workspace.files-enabled=false",
      "app.workspace.worker-token=synthetic-production-bridge-token-32-chars"
    })
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class WorkspaceSessionIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired TestAccounts accounts;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  MockHttpSession login(String email) throws Exception {
    var session = new MockHttpSession();
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("password", "password123"))
        .andExpect(status().isOk());
    return session;
  }

  @Test
  void existingLoginOwnsPiJobsAndOtherAccountsCannotReadThem() throws Exception {
    String email = UUID.randomUUID() + "@example.test";
    accounts.createOwner(email, "Existing app owner", "password123");
    var session = login(email);
    String thread = UUID.randomUUID().toString();
    String body =
        json.writeValueAsString(
            java.util.Map.of(
                "threadId",
                thread,
                "requestId",
                UUID.randomUUID().toString(),
                "prompt",
                "Weekly sales"));
    mvc.perform(
            post("/api/workspace/jobs")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    var response =
        mvc.perform(
                post("/api/workspace/jobs")
                    .session(session)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    String id = json.readTree(response.getResponse().getContentAsString()).path("id").asText();
    assertThat(
            jdbc.queryForObject(
                "select u.email from workspace_thread t join user_account u on u.id=t.user_account_id where t.id=?",
                String.class,
                UUID.fromString(thread)))
        .isEqualTo(email);
    mvc.perform(get("/api/workspace/jobs/" + id).session(session)).andExpect(status().isOk());
    mvc.perform(get("/api/workspace/jobs/" + id)).andExpect(status().isUnauthorized());
    String other = UUID.randomUUID() + "@example.test";
    accounts.createOwner(other, "Other owner", "password123");
    mvc.perform(get("/api/workspace/jobs/" + id).session(login(other)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/workspace/jobs/" + id + "/files").session(session))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/workspace/jobs/" + id + "/stop").session(session).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void workerReadinessRequiresItsTokenAndDoesNotClaimJobs() throws Exception {
    Integer before =
        jdbc.queryForObject(
            "select count(*) from workspace_job where state='RUNNING'", Integer.class);
    mvc.perform(get("/api/workspace-worker/ready")).andExpect(status().isForbidden());
    mvc.perform(get("/api/workspace-worker/ready").header("X-Workspace-Token", "wrong"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/workspace-worker/ready")
                .header("X-Workspace-Token", "synthetic-production-bridge-token-32-chars"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from workspace_job where state='RUNNING'", Integer.class))
        .isEqualTo(before);
  }
}
