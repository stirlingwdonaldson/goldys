package com.goldys.platform.config;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the single {@link VenueClock} bean, whose zone is {@code app.venue.timezone}. */
@Configuration
public class VenueClockConfig {

  @Bean
  VenueClock venueClock(@Value("${app.venue.timezone:Australia/Melbourne}") String zone) {
    return VenueClock.system(ZoneId.of(zone));
  }
}
