package com.goldys.platform.connectors.opentable;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the OpenTable connector, reading credentials and the Fortress CDP endpoint from environment
 * variables.
 */
@Configuration
public class OpenTableConfig {
  @Bean(destroyMethod = "close")
  OpenTableClient opentableClient(
      @Value("${opentable.fortress-cdp-url:http://localhost:9222}") String cdpUrl,
      @Value("${opentable.email:}") String email,
      @Value("${opentable.password:}") String password,
      @Value("${opentable.session-path:/tmp/opentable-session.json}") String sessionPath) {
    return new FortressOpenTableClient(cdpUrl, email, password, Path.of(sessionPath));
  }

  @Bean
  OpenTableConnector opentableConnector(
      OpenTableClient client,
      OpenTableCsvParser parser,
      CanonicalReservationIngest canonical,
      @Value("${opentable.window-before-days:30}") int windowBeforeDays,
      @Value("${opentable.window-after-days:14}") int windowAfterDays) {
    return new OpenTableConnector(client, parser, canonical, windowBeforeDays, windowAfterDays);
  }
}
