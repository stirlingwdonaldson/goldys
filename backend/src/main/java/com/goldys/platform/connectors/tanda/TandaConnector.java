package com.goldys.platform.connectors.tanda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tanda evidence capture and validated roster/worked observations. Re-fetches the bounded window to
 * retain corrections; absence never deletes prior evidence.
 */
@Component
public class TandaConnector implements SourceConnector {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final TandaClient client;
  private final String tokenFile;
  private final VenueClock clock;
  private final int historyDays;
  private final com.goldys.platform.canonical.CanonicalLabourIngest labour;

  @org.springframework.beans.factory.annotation.Autowired
  public TandaConnector(
      @Value("${tanda.token-file:}") String tokenFile,
      @Value("${tanda.history-days:30}") int historyDays,
      VenueClock clock,
      com.goldys.platform.canonical.CanonicalLabourIngest labour) {
    this(new TandaClient(), tokenFile, historyDays, clock, labour);
  }

  TandaConnector(
      TandaClient client,
      String tokenFile,
      int historyDays,
      VenueClock clock,
      com.goldys.platform.canonical.CanonicalLabourIngest labour) {
    this.labour = java.util.Objects.requireNonNull(labour);
    if (historyDays < 1 || historyDays > 366)
      throw new IllegalArgumentException("Tanda history days must be 1–366");
    this.client = client;
    this.tokenFile = tokenFile;
    this.historyDays = historyDays;
    this.clock = clock;
  }

  @Override
  public String sourceSystem() {
    return "TANDA";
  }

  @Override
  public String connectorName() {
    return "tanda-api";
  }

  @Override
  public void fetch(String watermark, IngestionSink sink) {
    String token = readToken();
    var pages = new java.util.ArrayList<TandaLabourMapper.Page>();
    IngestionSink recordingSink =
        payload -> {
          var rawId = sink.accept(payload);
          try {
            pages.add(
                new TandaLabourMapper.Page(
                    payload.fetcherIdentity().substring(6), JSON.readTree(payload.bytes()), rawId));
          } catch (IOException e) {
            throw new ConnectorFetchException(
                "TANDA_SCHEMA_MISMATCH", "Invalid Tanda JSON; raw evidence retained");
          }
          return rawId;
        };
    LocalDate today = clock.today();
    LocalDate from = today.minusDays(historyDays - 1L);
    String window = "from=" + from + "&to=" + today.plusDays(14);
    capture("locations", "locations", false, token, recordingSink);
    capture("departments", "departments", true, token, recordingSink);
    capture("users?show_inactive=true&show_wages=true", "users", true, token, recordingSink);
    // Tanda rejects schedule windows more than seven days apart.
    for (LocalDate start = from; !start.isAfter(today.plusDays(14)); start = start.plusDays(7)) {
      LocalDate end = start.plusDays(6);
      if (end.isAfter(today.plusDays(14))) end = today.plusDays(14);
      capture(
          "schedules?from="
              + start
              + "&to="
              + end
              + "&show_costs=true&show_award_interpretation=true",
          "schedules",
          true,
          token,
          recordingSink);
    }
    capture("leave?" + window + "&include_inactive=true", "leave", true, token, recordingSink);
    // Timesheets are pay-period envelopes. Overlapping dates intentionally remain raw evidence,
    // never summed. The mapper deduplicates source shift IDs across these envelopes.
    for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
      capture(
          "timesheets/on/"
              + day
              + "?show_costs=true&include_oncosts=true"
              + "&show_award_interpretation=true&show_variance=true&skip_status_update=true",
          "timesheets",
          true,
          token,
          recordingSink);
    }
    var inputs = new TandaLabourMapper().map(pages, from, today.plusDays(14), clock.zone());
    labour.synchronize("TANDA", from, today.plusDays(14), inputs);
  }

  private void capture(
      String endpoint, String dataset, boolean paged, String token, IngestionSink sink) {
    for (int page = 1; page <= 1000; page++) {
      String path =
          endpoint
              + (paged
                  ? (endpoint.contains("?") ? "&" : "?") + "page=" + page + "&page_size=100"
                  : "");
      byte[] bytes = client.get(path, token);
      sink.accept(
          new FetchedPayload(
              FetchMethod.API, "application/json", bytes, "UTF-8", "tanda-" + dataset, path));
      int count;
      try {
        var data = JSON.readTree(bytes);
        if (data == null || !data.isArray()) throw new IOException();
        count = data.size();
      } catch (IOException e) {
        throw new ConnectorFetchException(
            "TANDA_SCHEMA_MISMATCH",
            "Tanda " + dataset + " was not an array. Raw response retained for review.");
      }
      if (!paged || count < 100) return;
    }
    throw new ConnectorFetchException(
        "TANDA_PAGE_LIMIT", "Tanda pagination exceeded the capture limit; data is incomplete.");
  }

  private String readToken() {
    if (tokenFile.isBlank())
      throw new ConnectorFetchException(
          "TANDA_NOT_CONFIGURED",
          "Set up the Tanda token using ops/tanda-token.py, then configure TANDA_TOKEN_FILE.");
    try {
      Path path = Path.of(tokenFile);
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
              .equals(PosixFilePermissions.fromString("rw-------"))
          || Files.size(path) > 4097) throw new IOException();
      String token = Files.readString(path).strip();
      if (token.isBlank() || !token.matches("[A-Za-z0-9._~-]{16,4096}")) throw new IOException();
      return token;
    } catch (IOException | RuntimeException e) {
      throw new ConnectorFetchException(
          "TANDA_TOKEN_FILE",
          "Tanda token file must be a regular owner-readable 0600 file containing a valid token. Check configuration without sharing its contents.");
    }
  }
}
