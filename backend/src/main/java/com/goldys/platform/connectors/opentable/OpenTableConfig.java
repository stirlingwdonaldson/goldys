package com.goldys.platform.connectors.opentable;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the OpenTable connector, reading credentials from environment variables. */
@Configuration
public class OpenTableConfig {
  @Bean
  OpenTableClient opentableClient() {
    return new PlaywrightOpenTableClient();
  }
}
