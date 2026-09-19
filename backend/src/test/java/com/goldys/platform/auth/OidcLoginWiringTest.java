package com.goldys.platform.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, OidcLoginWiringTest.OidcTestConfig.class})
class OidcLoginWiringTest {
  @Autowired MockMvc mvc;

  @Test
  void unauthenticatedRequestRedirectsToOidcLoginWhenProviderConfigured() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/oauth2/authorization/goldys"));
  }

  @TestConfiguration
  static class OidcTestConfig {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("goldys")
              .clientId("test-client")
              .clientSecret("test-secret")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .authorizationUri("https://id.example/oauth2/authorize")
              .tokenUri("https://id.example/oauth2/token")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
