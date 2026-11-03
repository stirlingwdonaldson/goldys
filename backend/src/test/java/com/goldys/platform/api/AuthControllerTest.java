package com.goldys.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.AuthService;
import com.goldys.platform.config.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean AuthService authService;

  @Test
  void changePasswordDelegatesAndReturnsNoContent() throws Exception {
    AccountUserDetails user = user();
    when(authService.changePassword(any(), eq("oldpassword"), eq("newpassword"))).thenReturn(user);

    mvc.perform(
            post("/api/auth/change-password")
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(user, "hash", List.of())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"oldpassword\",\"newPassword\":\"newpassword\"}"))
        .andExpect(status().isNoContent());

    verify(authService).changePassword(user, "oldpassword", "newpassword");
  }

  @Test
  void changePasswordSavesTheRefreshedPrincipalWithoutThePasswordHash() throws Exception {
    AccountUserDetails user = user();
    when(authService.changePassword(any(), eq("oldpassword"), eq("newpassword"))).thenReturn(user);

    MvcResult result =
        mvc.perform(
                post("/api/auth/change-password")
                    .with(
                        authentication(
                            new UsernamePasswordAuthenticationToken(user, "hash", List.of())))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"currentPassword\":\"oldpassword\",\"newPassword\":\"newpassword\"}"))
            .andExpect(status().isNoContent())
            .andReturn();

    SecurityContext saved =
        (SecurityContext)
            result
                .getRequest()
                .getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertThat(saved.getAuthentication().getCredentials()).isNull();
  }

  @Test
  void errorPathIsPermitAll() throws Exception {
    // Spring Boot forwards error handling to /error as an error-dispatch that still passes the
    // AuthorizationFilter; /error must be public or the 4xx becomes a 302 to /login. (The mock MVC
    // slice renders a 500 for /error, but the point is that it is not redirected to /login.)
    mvc.perform(get("/error"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(302));
  }

  private static AccountUserDetails user() {
    return new AccountUserDetails(
        UUID.randomUUID(), "a@b.com", "hash", "A B", "ALL", "OWNER", true, false);
  }
}
