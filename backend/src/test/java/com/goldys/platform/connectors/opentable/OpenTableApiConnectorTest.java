package com.goldys.platform.connectors.opentable;

import static com.goldys.platform.connectors.opentable.session.FakeOpenTableBrowser.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalGuestIngest;
import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.CanonicalReviewIngest;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.connectors.opentable.session.BrowserResponse;
import com.goldys.platform.connectors.opentable.session.FakeOpenTableBrowser;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenTableApiConnectorTest {
  private static final ZoneId ZONE = ZoneId.of("Australia/Melbourne");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
  private static final Pattern WINDOW = Pattern.compile("startDate=([\\d-]+)&endDate=([\\d-]+)");

  private final ObjectMapper json = new ObjectMapper();
  private final FakeOpenTableBrowser browser = new FakeOpenTableBrowser();
  private final CanonicalReservationIngest canonical = mock(CanonicalReservationIngest.class);
  private final CanonicalReviewIngest reviewIngest = mock(CanonicalReviewIngest.class);
  private final CanonicalGuestIngest guestIngest = mock(CanonicalGuestIngest.class);
  private final List<FetchedPayload> stored = new ArrayList<>();

  /** A minimal raw ledger: latest payload per (fetcherIdentity, sourceRecordRef). */
  private final Map<String, StoredPayloads.Stored> ledger = new HashMap<>();

  private OpenTableApiConnector connector;

  @BeforeEach
  void setUp() {
    browser.authenticated = true;
    browser.router = this::defaultRoute;
    connector =
        new OpenTableApiConnector(
            new OpenTableApiClient(browser, Duration.ZERO, d -> {}),
            new OpenTableReservationJsonParser(json, ZONE),
            new OpenTableReviewJsonParser(json),
            canonical,
            reviewIngest,
            guestIngest,
            json,
            VenueClock.fixed(ZONE, TODAY.atTime(9, 0).atZone(ZONE).toInstant()),
            "140126",
            LocalDate.of(2020, 1, 1),
            (fetcher, ref) -> Optional.ofNullable(ledger.get(fetcher + "|" + ref)));
  }

  private UUID sink(FetchedPayload payload) {
    stored.add(payload);
    UUID id = UUID.randomUUID();
    byte[] bytes = payload.bytes();
    ledger.put(
        payload.fetcherIdentity() + "|" + payload.sourceRecordRef(),
        new StoredPayloads.Stored(id, sha256(bytes), () -> bytes));
    return id;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private BrowserResponse defaultRoute(String path) {
    if (path.contains("reservations/gold")) {
      return reservations(1);
    }
    if (path.contains("/items?")) {
      return json(200, "{\"data\":{\"items\":[{\"id\":\"i1\"}]}}");
    }
    if (path.endsWith("/menus")) {
      return json(200, "{\"data\":{\"menus\":[]}}");
    }
    if (path.endsWith("/reviews/details")) {
      return json(200, "{\"meta\":{\"totalCount\":0},\"data\":[]}");
    }
    return json(404, "{}");
  }

  private static BrowserResponse reservations(int rows) {
    String data =
        IntStream.range(0, rows)
            .mapToObj(
                i ->
                    "{\"reservationId\":\"r"
                        + i
                        + "\",\"visitDate\":\"2026-10-11T18:30:00\",\"partySize\":2,"
                        + "\"stateCategory\":\"finished\",\"primarySource\":\"website\"}")
            .collect(Collectors.joining(","));
    return json(200, "{\"data\":[" + data + "]}");
  }

  private List<FetchedPayload> storedAs(String fetcher) {
    return stored.stream().filter(p -> p.fetcherIdentity().equals(fetcher)).toList();
  }

  @Test
  void refusesToRunWhenNotSignedIn() {
    browser.authenticated = false;

    assertThatThrownBy(() -> connector.fetch(null, this::sink))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_AUTH_FAILED"));
    assertThat(browser.requests).isEmpty();
  }

  @Test
  void incrementalCoversRecentAndUpcomingInContiguousHalfMonthWindows() {
    connector.fetch(null, this::sink);

    List<FetchedPayload> windows = storedAs("opentable-reservations");
    // Today - 60 days = 2026-08-14 and today + 18 months = 2028-04-13, each snapped outward to its
    // half-month so window refs stay stable from one day's sync to the next.
    assertThat(windows.getFirst().sourceRecordRef()).isEqualTo("2026-08-01..2026-08-15");
    assertThat(windows.getLast().sourceRecordRef()).isEqualTo("2028-04-01..2028-04-15");
    assertContiguous(windows);
    for (FetchedPayload w : windows) {
      String[] range = w.sourceRecordRef().split("\\.\\.");
      assertThat(ChronoUnit.DAYS.between(LocalDate.parse(range[0]), LocalDate.parse(range[1])))
          .isLessThanOrEqualTo(15);
    }
    // One canonical record per row in each stored window.
    verify(canonical, times(windows.size())).record(any());
    assertThat(storedAs("opentable-menu-items")).hasSize(1);
    assertThat(storedAs("opentable-menus")).hasSize(1);
  }

  @Test
  void fullModeStartsFromBackfillDate() {
    connector.fetch(OpenTableApiConnector.FULL, this::sink);

    assertThat(storedAs("opentable-reservations").getFirst().sourceRecordRef())
        .startsWith("2020-01-01..");
  }

  @Test
  void fullWindowIsSplitAndNotStored() {
    // Any window longer than 3 days comes back at the page limit; shorter ones fit.
    browser.router =
        path -> {
          Matcher m = WINDOW.matcher(path);
          if (m.find()) {
            long days =
                ChronoUnit.DAYS.between(LocalDate.parse(m.group(1)), LocalDate.parse(m.group(2)));
            return reservations(days > 3 ? OpenTableApiConnector.RESERVATION_LIMIT : 1);
          }
          return defaultRoute(path);
        };

    connector.fetch(null, this::sink);

    List<FetchedPayload> windows = storedAs("opentable-reservations");
    assertContiguous(windows);
    assertThat(windows)
        .allSatisfy(
            w -> {
              String[] r = w.sourceRecordRef().split("\\.\\.");
              assertThat(ChronoUnit.DAYS.between(LocalDate.parse(r[0]), LocalDate.parse(r[1])))
                  .isLessThanOrEqualTo(3);
            });
  }

  @Test
  void oneDayAtThePageLimitFailsLoudly() {
    browser.router =
        path ->
            path.contains("reservations/gold")
                ? reservations(OpenTableApiConnector.RESERVATION_LIMIT)
                : defaultRoute(path);

    assertThatThrownBy(() -> connector.fetch(null, this::sink))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
    verify(canonical, never()).record(any());
    // The other datasets still ran.
    assertThat(storedAs("opentable-menu-items")).hasSize(1);
  }

  @Test
  void reviewsArePagedWithAllFiltersIncludingHidden() throws Exception {
    browser.router =
        path -> {
          if (path.endsWith("/reviews/details")) {
            String last = browser.requests.getLast();
            int page = Integer.parseInt(last.replaceAll(".*\"page\":(\\d+).*", "$1"));
            int rows = page == 0 ? 25 : 5;
            String data =
                IntStream.range(0, rows)
                    .mapToObj(
                        i ->
                            "{\"id\":\"OT-p"
                                + page
                                + "-"
                                + i
                                + "\",\"source\":\"OpenTable\","
                                + "\"reviewDate\":\"2026-10-13T02:06:40Z\"}")
                    .collect(Collectors.joining(","));
            return json(200, "{\"meta\":{\"totalCount\":30},\"data\":[" + data + "]}");
          }
          return defaultRoute(path);
        };

    connector.fetch(null, this::sink);

    assertThat(storedAs("opentable-reviews")).hasSize(2);
    String firstBody =
        browser.requests.stream()
            .filter(r -> r.contains("/reviews/details"))
            .findFirst()
            .orElseThrow();
    JsonNode body = json.readTree(firstBody.substring(firstBody.indexOf('{')));
    assertThat(body.path("restaurantVisibility").toString()).contains("Hidden");
    assertThat(body.path("showUpdatedByDiner").asBoolean()).isTrue();
    assertThat(body.path("sources").isEmpty()).isTrue();
    assertThat(body.path("workflowStatuses")).hasSize(4);
    assertThat(Instant.parse(body.path("startDate").asText()))
        .isEqualTo(TODAY.minusDays(120).atStartOfDay(ZONE).toInstant());
  }

  @Test
  void reviewsAreCanonicalizedWithGuestsEnsured() throws Exception {
    browser.router =
        path -> {
          if (path.endsWith("/reviews/details")) {
            String data =
                "{\"id\":\"OT-1\",\"source\":\"OpenTable\",\"reviewDate\":\"2026-10-13T02:06:40Z\","
                    + "\"overallRating\":5.0,\"guestId\":\"68f9d1af3720e3c69efd313c\","
                    + "\"guestName\":\"Mackenzie\"},"
                    + "{\"id\":\"RT-1\",\"source\":\"Google\",\"reviewDate\":\"2026-10-13T00:54:05Z\","
                    + "\"overallRating\":4.0}";
            return json(200, "{\"meta\":{\"totalCount\":2},\"data\":[" + data + "]}");
          }
          return defaultRoute(path);
        };

    connector.fetch(null, this::sink);

    ArgumentCaptor<ReviewInput> input = ArgumentCaptor.forClass(ReviewInput.class);
    verify(reviewIngest, times(2)).record(input.capture());
    assertThat(input.getAllValues())
        .extracting(ReviewInput::reviewId)
        .containsExactly("OT-1", "RT-1");
    assertThat(input.getAllValues().get(0).guestId()).isEqualTo("68f9d1af3720e3c69efd313c");
    assertThat(input.getAllValues().get(1).guestId()).isNull();

    // Only the OpenTable review carries a Guest ID.
    verify(guestIngest).ensure(eq("68f9d1af3720e3c69efd313c"), eq("Mackenzie"), any(UUID.class));
  }

  @Test
  void sessionLossMidRunKeepsEarlierPayloadsAndReportsAuthFailure() {
    browser.router =
        path -> path.endsWith("/reviews/details") ? json(401, "{}") : defaultRoute(path);

    assertThatThrownBy(() -> connector.fetch(null, this::sink))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_AUTH_FAILED"));
    assertThat(storedAs("opentable-reservations")).isNotEmpty();
    assertThat(storedAs("opentable-menu-items")).hasSize(1);
  }

  @Test
  void menuItemsArePagedUntilAShortPage() {
    browser.router =
        path -> {
          if (path.contains("/items?")) {
            int count = path.contains("pageNumber=1&") ? OpenTableApiConnector.MENU_PAGE_SIZE : 2;
            String items =
                IntStream.range(0, count)
                    .mapToObj(i -> "{\"id\":\"i" + i + "\"}")
                    .collect(Collectors.joining(","));
            return json(200, "{\"data\":{\"items\":[" + items + "]}}");
          }
          return defaultRoute(path);
        };

    connector.fetch(null, this::sink);

    assertThat(storedAs("opentable-menu-items"))
        .extracting(FetchedPayload::sourceRecordRef)
        .containsExactly("p1", "p2");
    assertThat(browser.requests).noneMatch(r -> r.contains("sources="));
  }

  @Test
  void aRepeatSyncOfUnchangedDataStoresNothingNewButStillCanonicalizes() {
    connector.fetch(null, this::sink);
    int firstRun = stored.size();
    int windows = storedAs("opentable-reservations").size();

    connector.fetch(null, this::sink);

    assertThat(stored).hasSize(firstRun);
    assertThat(storedAs("opentable-reservations")).hasSize(windows);
    assertThat(storedAs("opentable-menu-items")).hasSize(1);
    assertThat(storedAs("opentable-menus")).hasSize(1);
    // Canonicalization still runs for every window (a no-op downstream when nothing changed).
    verify(canonical, times(windows * 2)).record(any());
  }

  @Test
  void requestMetadataAloneDoesNotCountAsAChange() {
    int[] call = {0};
    browser.router =
        path ->
            path.contains("reservations/gold")
                ? json(
                    200,
                    "{\"meta-data\":{\"ot-requestid\":\"req-"
                        + (call[0]++)
                        + "\",\"last_modified\":\"t"
                        + call[0]
                        + "\"},\"data\":[{\"reservationId\":\"r1\",\"visitDate\":\"2026-10-11T18:30:00\","
                        + "\"partySize\":2,\"stateCategory\":\"booked\",\"primarySource\":\"phone\"}]}")
                : defaultRoute(path);

    connector.fetch(null, this::sink);
    int windows = storedAs("opentable-reservations").size();
    connector.fetch(null, this::sink);

    assertThat(storedAs("opentable-reservations")).hasSize(windows);
  }

  @Test
  void changedDataIsStoredAgain() {
    connector.fetch(null, this::sink);
    int windows = storedAs("opentable-reservations").size();

    browser.router =
        path -> path.contains("reservations/gold") ? reservations(2) : defaultRoute(path);
    connector.fetch(null, this::sink);

    assertThat(storedAs("opentable-reservations")).hasSize(windows * 2);
  }

  @Test
  void halfMonthBoundaries() {
    assertThat(OpenTableApiConnector.halfMonthStart(LocalDate.of(2026, 8, 14)))
        .isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(OpenTableApiConnector.halfMonthStart(LocalDate.of(2026, 8, 16)))
        .isEqualTo(LocalDate.of(2026, 8, 16));
    assertThat(OpenTableApiConnector.halfMonthEnd(LocalDate.of(2028, 2, 20)))
        .isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(OpenTableApiConnector.halfMonthEnd(LocalDate.of(2026, 10, 15)))
        .isEqualTo(LocalDate.of(2026, 10, 15));
  }

  private static void assertContiguous(List<FetchedPayload> windows) {
    LocalDate expectedStart = null;
    for (FetchedPayload w : windows) {
      String[] range = w.sourceRecordRef().split("\\.\\.");
      LocalDate start = LocalDate.parse(range[0]);
      if (expectedStart != null) {
        assertThat(start).isEqualTo(expectedStart);
      }
      expectedStart = LocalDate.parse(range[1]).plusDays(1);
    }
  }
}
