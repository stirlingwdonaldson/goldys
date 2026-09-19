package com.goldys.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Backend-owned session policy.
 *
 * <p>The venue's OIDC provider handles authentication; this application maps the resulting identity
 * to a staff profile. Only the public health endpoint is reachable without a session, and CSRF
 * stays on for browser sessions.
 *
 * <p>The OAuth2 login configurer and the venue's client registration are wired when the provider
 * details land (environment placeholders), not here — a placeholder registration would embed a fake
 * provider in the security chain.
 */
@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth -> auth.requestMatchers("/api/health").permitAll().anyRequest().authenticated());
    return http.build();
  }
}
