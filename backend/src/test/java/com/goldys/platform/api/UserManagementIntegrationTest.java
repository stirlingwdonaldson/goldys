package com.goldys.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.support.PostgresContainerConfiguration;
import com.goldys.platform.support.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class UserManagementIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TestAccounts testAccounts;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table user_account cascade");
  }

  @Test
  void anonymousRequestsAreRejected() throws Exception {
    // Session auth redirects browser requests to the login page; the frontend maps that to
    // AUTH_REQUIRED. Do not change the app-wide entry point for this one endpoint.
    mvc.perform(get("/api/users")).andExpect(status().is3xxRedirection());
  }

  @Test
  void anAccountWithoutTheUsersGrantIsDenied() throws Exception {
    testAccounts.createStaff("staff@example.com", "Staff", "password123");
    Login staff = login("staff@example.com", "password123");

    mvc.perform(get("/api/users").session(staff.session()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void anOwnerListsCreatesRenamesAndDeactivatesAccounts() throws Exception {
    testAccounts.createOwner("owner@example.com", "Owner", "password123");
    Login owner = login("owner@example.com", "password123");

    mvc.perform(get("/api/users").session(owner.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("owner@example.com"))
        .andExpect(jsonPath("$[0].self").value(true));

    MvcResult created =
        mvc.perform(
                post("/api/users")
                    .session(owner.session())
                    .cookie(owner.xsrf())
                    .header("X-XSRF-TOKEN", owner.xsrf().getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"New@Example.com\",\"displayName\":\"New User\","
                            + "\"password\":\"password123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("new@example.com"))
            .andReturn();
    String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

    mvc.perform(
            patch("/api/users/{id}", id)
                .session(owner.session())
                .cookie(owner.xsrf())
                .header("X-XSRF-TOKEN", owner.xsrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Renamed"));

    mvc.perform(
            patch("/api/users/{id}", id)
                .session(owner.session())
                .cookie(owner.xsrf())
                .header("X-XSRF-TOKEN", owner.xsrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    loginExpecting("new@example.com", "password123", 401);
  }

  @Test
  void duplicateEmailIsRejectedRegardlessOfCase() throws Exception {
    testAccounts.createOwner("owner@example.com", "Owner", "password123");
    Login owner = login("owner@example.com", "password123");

    mvc.perform(
            post("/api/users")
                .session(owner.session())
                .cookie(owner.xsrf())
                .header("X-XSRF-TOKEN", owner.xsrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"OWNER@example.com\",\"displayName\":\"Duplicate\","
                        + "\"password\":\"password123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void unknownAccountIsAValidationError() throws Exception {
    testAccounts.createOwner("owner@example.com", "Owner", "password123");
    Login owner = login("owner@example.com", "password123");

    mvc.perform(
            patch("/api/users/{id}", UUID.randomUUID())
                .session(owner.session())
                .cookie(owner.xsrf())
                .header("X-XSRF-TOKEN", owner.xsrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Nobody\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void anOwnerCannotDeactivateThemselves() throws Exception {
    testAccounts.createOwner("owner@example.com", "Owner", "password123");
    Login owner = login("owner@example.com", "password123");
    String id =
        jdbc.queryForObject(
            "select id::text from user_account where email = 'owner@example.com'", String.class);

    mvc.perform(
            patch("/api/users/{id}", id)
                .session(owner.session())
                .cookie(owner.xsrf())
                .header("X-XSRF-TOKEN", owner.xsrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("You cannot deactivate your own account"));
  }

  private Login login(String email, String password) throws Exception {
    MockHttpSession session = new MockHttpSession();
    MvcResult result = loginExpecting(email, password, 200, session);
    return new Login(session, result.getResponse().getCookie("XSRF-TOKEN"));
  }

  private void loginExpecting(String email, String password, int status) throws Exception {
    loginExpecting(email, password, status, new MockHttpSession());
  }

  private MvcResult loginExpecting(
      String email, String password, int status, MockHttpSession session) throws Exception {
    return mvc.perform(
            post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("password", password))
        .andExpect(status().is(status))
        .andReturn();
  }

  private record Login(MockHttpSession session, Cookie xsrf) {}
}
