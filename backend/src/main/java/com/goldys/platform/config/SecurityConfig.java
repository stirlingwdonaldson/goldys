package com.goldys.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.api.ApiErrorResponse;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.StaffProfileSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Email + password session auth. Only the health, webhook, signup and login endpoints are public.
 */
@Configuration
public class SecurityConfig {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/health"),
                    AntPathRequestMatcher.antMatcher("/api/ingest/lightspeed"),
                    AntPathRequestMatcher.antMatcher("/api/ingest/lightspeed-products"),
                    AntPathRequestMatcher.antMatcher("/api/auth/signup"),
                    AntPathRequestMatcher.antMatcher("/api/auth/login"),
                    // Spring Boot forwards error handling (e.g. a bodyless POST to a
                    // @RequestBody endpoint) to /error as a separate error-dispatch. That
                    // dispatch runs through the AuthorizationFilter, so /error must be public
                    // or the 4xx it is trying to report becomes a 302 redirect to /login.
                    AntPathRequestMatcher.antMatcher("/error"))
                .permitAll()
                .anyRequest()
                .authenticated());

    // The server-to-server webhook and the unauthenticated auth endpoints are CSRF-exempt; browser
    // sessions keep CSRF via a cookie the frontend reads back into the X-XSRF-TOKEN header.
    http.csrf(
        csrf ->
            csrf.ignoringRequestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/ingest/lightspeed"),
                    AntPathRequestMatcher.antMatcher("/api/ingest/lightspeed-products"),
                    AntPathRequestMatcher.antMatcher("/api/auth/signup"),
                    AntPathRequestMatcher.antMatcher("/api/auth/login"))
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));

    http.formLogin(
        form ->
            form.loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .successHandler(this::successHandler)
                .failureHandler(this::failureHandler));

    http.logout(
        logout ->
            logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(
                    (request, response, authentication) -> response.setStatus(204)));

    return http.build();
  }

  private void successHandler(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    AccountUserDetails user = (AccountUserDetails) authentication.getPrincipal();
    response.setStatus(200);
    response.setContentType("application/json");
    objectMapper.writeValue(
        response.getWriter(),
        new StaffProfileSummary(user.displayName(), user.department(), user.seniority()));
  }

  private void failureHandler(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    response.setStatus(401);
    response.setContentType("application/json");
    objectMapper.writeValue(
        response.getWriter(),
        new ApiErrorResponse("INVALID_CREDENTIALS", "Invalid email or password", null, Map.of()));
  }
}
