package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AuthService;
import com.goldys.platform.auth.StaffProfileSummary;
import com.goldys.platform.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean AuthService authService;

  @Test
  void signupReturnsCreatedUser() throws Exception {
    when(authService.signup("a@b.com", "A B", "password123"))
        .thenReturn(new StaffProfileSummary("A B", "ALL", "STAFF"));

    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"a@b.com\",\"displayName\":\"A B\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.department").value("ALL"))
        .andExpect(jsonPath("$.seniority").value("STAFF"));
  }

  @Test
  void emptyBodySignupIsRejectedNotRedirectedToLogin() throws Exception {
    // A POST with no body must reach the controller (and be rejected there) rather than be
    // redirected to /login — the public permitAll paths are matched by path, not by content type.
    mvc.perform(post("/api/auth/signup")).andExpect(status().is4xxClientError());
  }

  @Test
  void errorPathIsPermitAll() throws Exception {
    // Spring Boot forwards error handling to /error as an error-dispatch that still passes the
    // AuthorizationFilter; /error must be public or the 4xx becomes a 302 to /login. (The mock MVC
    // slice renders a 500 for /error, but the point is that it is not redirected to /login.)
    mvc.perform(get("/error"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(302));
  }
}
