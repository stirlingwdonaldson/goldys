package com.goldys.platform.classification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AuthService;
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

/**
 * Permission boundary for the product categorisation screen: an {@code ALL × OWNER} account (seeded
 * {@code products.category} in V54) reads the taxonomy, while a fresh account with no grant is
 * explicitly denied (403) rather than served an empty list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class ProductCategoryPermissionBoundaryTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthService authService;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table user_account cascade");
  }

  @Test
  void ownerReadsTaxonomy() throws Exception {
    authService.signup("owner@example.com", "Owner", "password123");
    jdbc.update("update user_account set seniority = 'OWNER' where email = 'owner@example.com'");
    MockHttpSession session = login("owner@example.com", "password123");

    // An empty taxonomy is a valid 200 for a freshly migrated database.
    mvc.perform(get("/api/products/categories/taxonomy").session(session))
        .andExpect(status().isOk());
  }

  @Test
  void userWithoutProductCategoryGrantIsDenied() throws Exception {
    authService.signup("staff@example.com", "Staff", "password123");
    MockHttpSession session = login("staff@example.com", "password123");

    mvc.perform(get("/api/products/categories/taxonomy").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  private MockHttpSession login(String email, String password) throws Exception {
    MockHttpSession session = new MockHttpSession();
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("password", password))
        .andExpect(status().isOk());
    return session;
  }
}
