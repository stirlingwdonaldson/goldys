package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the OpenTable connector, reading credentials from environment variables. */
@Configuration
public class OpenTableConfig {
  @Bean
  OpenTableClient opentableClient() {
    return new PlaywrightOpenTableClient();
  }

  @Bean
  OpenTableConnector opentableConnector(
      @Value("${opentable.email:}") String email,
      @Value("${opentable.password:}") String password,
      OpenTableClient client,
      OpenTableCsvParser parser,
      CanonicalReservationIngest canonical,
      @Value("${opentable.window-before-days:30}") int windowBeforeDays,
      @Value("${opentable.window-after-days:14}") int windowAfterDays) {
    return new OpenTableConnector(
        client, email, password, parser, canonical, windowBeforeDays, windowAfterDays);
  }
}
