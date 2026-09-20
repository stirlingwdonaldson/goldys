package com.goldys.platform.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Backend-owned session policy.
 *
 * <p>The venue's OIDC provider handles authentication; this application maps the resulting identity
 * to a staff profile. Only the public health endpoint is reachable without a session, and CSRF
 * stays on for browser sessions.
 *
 * <p>The OAuth2 login flow activates only when a {@link ClientRegistrationRepository} is present —
 * i.e. once the venue's provider details are supplied as environment variables. With no provider
 * configured (as in tests that use a mocked OIDC identity), the chain simply requires an
 * authenticated session.
 */
@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectProvider<ClientRegistrationRepository> registrations)
      throws Exception {
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers("/api/health", "/api/ingest/lightspeed")
                .permitAll()
                .anyRequest()
                .authenticated());
    // The server-to-server webhook has no session, so it is exempt from CSRF; browser sessions keep
    // CSRF via a cookie the frontend reads back into the X-XSRF-TOKEN header.
    http.csrf(
        csrf ->
            csrf.ignoringRequestMatchers("/api/ingest/lightspeed")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
    if (registrations.getIfAvailable() != null) {
      http.oauth2Login(Customizer.withDefaults());
    }
    return http.build();
  }
}
