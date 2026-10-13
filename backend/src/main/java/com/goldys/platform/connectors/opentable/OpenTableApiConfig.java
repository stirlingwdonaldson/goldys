package com.goldys.platform.connectors.opentable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.connectors.opentable.cdp.CdpTargets;
import com.goldys.platform.connectors.opentable.session.CdpOpenTableBrowser;
import com.goldys.platform.connectors.opentable.session.OpenTableBrowser;
import com.goldys.platform.connectors.opentable.session.OpenTableSessionService;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Wires the assisted-session OpenTable connector. Off unless {@code opentable.api-enabled=true} (it
 * needs the {@code opentable-browser} sidecar), in which case OpenTable stays CSV-drop only.
 */
@Configuration
@ConditionalOnProperty(name = "opentable.api-enabled", havingValue = "true")
public class OpenTableApiConfig {

  @Bean
  OpenTableBrowser openTableBrowser(
      ObjectMapper json,
      @Value("${opentable.cdp-host:opentable-browser}") String host,
      @Value("${opentable.cdp-port:9223}") int port,
      @Value("${opentable.restaurant-id:140126}") String restaurantId) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    return new CdpOpenTableBrowser(
        new CdpTargets(http, json, Duration.ofSeconds(5)), http, json, host, port, restaurantId);
  }

  @Bean
  OpenTableApiConnector openTableApiConnector(
      OpenTableBrowser browser,
      ObjectMapper json,
      CanonicalReservationIngest canonical,
      VenueClock venueClock,
      @Value("${opentable.timezone:Australia/Melbourne}") String zone,
      @Value("${opentable.restaurant-id:140126}") String restaurantId,
      @Value("${opentable.backfill-from:2020-01-01}") String backfillFrom,
      @Value("${opentable.request-spacing:PT1.5S}") Duration spacing,
      RawLedgerQuery rawLedger) {
    StoredPayloads stored =
        (fetcherIdentity, ref) ->
            rawLedger
                .latestDigest(OpenTableReservationJsonParser.SOURCE_SYSTEM, fetcherIdentity, ref)
                .map(
                    d ->
                        new StoredPayloads.Stored(
                            d.id(), d.sha256(), () -> rawLedger.payloadBytes(d.id())));
    OpenTableApiClient client = new OpenTableApiClient(browser, spacing, OpenTableApiConfig::sleep);
    return new OpenTableApiConnector(
        client,
        new OpenTableReservationJsonParser(json, ZoneId.of(zone)),
        canonical,
        json,
        venueClock,
        restaurantId,
        LocalDate.parse(backfillFrom),
        stored);
  }

  @Bean(destroyMethod = "shutdownNow")
  ScheduledExecutorService openTableSessionScheduler() {
    return Executors.newSingleThreadScheduledExecutor(
        r -> {
          Thread t = new Thread(r, "opentable-session");
          t.setDaemon(true);
          return t;
        });
  }

  /**
   * {@code IngestionService} is injected lazily: it collects every {@code SourceConnector},
   * including the one above, so resolving it eagerly here would order the beans needlessly.
   */
  @Bean
  OpenTableSessionService openTableSessionService(
      OpenTableBrowser browser,
      @Lazy IngestionService ingestion,
      ScheduledExecutorService openTableSessionScheduler,
      @Value("${opentable.session.poll-interval:PT2S}") Duration pollInterval,
      @Value("${opentable.session.idle-timeout:PT10M}") Duration idleTimeout) {
    return new OpenTableSessionService(
        browser,
        (watermark, onComplete) ->
            ingestion.runConnectorFrom(
                OpenTableReservationJsonParser.SOURCE_SYSTEM, watermark, onComplete),
        openTableSessionScheduler,
        Clock.systemUTC(),
        pollInterval,
        idleTimeout);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", e);
    }
  }
}
