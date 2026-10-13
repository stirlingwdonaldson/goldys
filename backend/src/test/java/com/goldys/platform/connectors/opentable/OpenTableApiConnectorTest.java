package com.goldys.platform.connectors.opentable;

import static com.goldys.platform.connectors.opentable.session.FakeOpenTableBrowser.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.connectors.opentable.session.BrowserResponse;
import com.goldys.platform.connectors.opentable.session.FakeOpenTableBrowser;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenTableApiConnectorTest {
  private static final ZoneId ZONE = ZoneId.of("Australia/Melbourne");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
  private static final Pattern WINDOW = Pattern.compile("startDate=([\\d-]+)&endDate=([\\d-]+)");

  private final ObjectMapper json = new ObjectMapper();
  private final FakeOpenTableBrowser browser = new FakeOpenTableBrowser();
  private final CanonicalReservationIngest canonical = mock(CanonicalReservationIngest.class);
  private final List<FetchedPayload> stored = new ArrayList<>();
  private OpenTableApiConnector connector;

  @BeforeEach
  void setUp() {
    browser.authenticated = true;
    browser.router = this::defaultRoute;
    connector =
        new OpenTableApiConnector(
            new OpenTableApiClient(browser, Duration.ZERO, d -> {}),
            new OpenTableReservationJsonParser(json, ZONE),
            canonical,
            json,
            VenueClock.fixed(ZONE, TODAY.atTime(9, 0).atZone(ZONE).toInstant()),
            "140126",
            LocalDate.of(2020, 1, 1));
  }

  private UUID sink(FetchedPayload payload) {
    stored.add(payload);
    return UUID.randomUUID();
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
    assertThat(windows.getFirst().sourceRecordRef()).startsWith(TODAY.minusDays(60) + "..");
    assertThat(windows.getLast().sourceRecordRef()).endsWith(".." + TODAY.plusMonths(18));
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
                    .mapToObj(i -> "{\"id\":\"p" + page + "-" + i + "\"}")
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
