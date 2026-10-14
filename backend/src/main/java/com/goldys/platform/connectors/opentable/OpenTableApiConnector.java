package com.goldys.platform.connectors.opentable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.goldys.platform.canonical.CanonicalGuestIngest;
import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.CanonicalReviewIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.connectors.opentable.session.BrowserResponse;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulls reservations, reviews and menu items from GuestCenter's JSON endpoints through the browser
 * an owner signed in to (see {@code OpenTableSessionService}). Every response is handed to the sink
 * byte-for-byte before parsing; reservations and reviews are then canonicalized, while menu items
 * stay raw-only until their domain exists.
 *
 * <p>The watermark carries the sync mode: {@code FULL} pulls all history from {@code backfillFrom};
 * anything else is incremental (recent and upcoming reservations, last 120 days of reviews).
 *
 * <p><b>Unchanged responses are not re-stored.</b> A response whose content matches the latest
 * stored payload for the same dataset window ({@code fetcherIdentity} + {@code sourceRecordRef}) is
 * skipped, apart from its top-level request metadata ({@code meta-data} / {@code meta}: request ids
 * and timestamps that differ on every call). The earlier payload remains the evidence for that
 * content, so the ledger stays append-only and complete while repeat syncs stop duplicating it.
 * Reservation windows are still canonicalized against the stored record (a no-op when nothing
 * changed), so a run that died between storing and canonicalizing is completed by the next one.
 */
public class OpenTableApiConnector implements SourceConnector {
  public static final String FULL = "FULL";

  static final int RESERVATION_LIMIT = 1000;
  static final int REVIEW_PAGE_SIZE = 25; // Fixed by the endpoint.
  static final int MENU_PAGE_SIZE = 100;
  private static final int MAX_REVIEW_PAGES = 400;
  private static final int MAX_MENU_PAGES = 100;
  private static final int INCREMENTAL_PAST_DAYS = 60;
  private static final int INCREMENTAL_REVIEW_DAYS = 120;
  private static final int FUTURE_MONTHS = 18;

  /**
   * Top-level keys that change on every request without the data changing: {@code meta-data}
   * (reservations: {@code ot-requestid}, {@code last_modified}) and {@code meta} (reviews, items).
   */
  private static final List<String> VOLATILE_KEYS = List.of("meta-data", "meta");

  private static final Logger log = LoggerFactory.getLogger(OpenTableApiConnector.class);

  private final OpenTableApiClient client;
  private final OpenTableReservationJsonParser parser;
  private final OpenTableReviewJsonParser reviewParser;
  private final CanonicalReservationIngest canonical;
  private final CanonicalReviewIngest reviewIngest;
  private final CanonicalGuestIngest guestIngest;
  private final ObjectMapper json;
  private final VenueClock venueClock;
  private final String restaurantId;
  private final LocalDate backfillFrom;
  private final StoredPayloads stored;

  public OpenTableApiConnector(
      OpenTableApiClient client,
      OpenTableReservationJsonParser parser,
      OpenTableReviewJsonParser reviewParser,
      CanonicalReservationIngest canonical,
      CanonicalReviewIngest reviewIngest,
      CanonicalGuestIngest guestIngest,
      ObjectMapper json,
      VenueClock venueClock,
      String restaurantId,
      LocalDate backfillFrom,
      StoredPayloads stored) {
    this.client = client;
    this.parser = parser;
    this.reviewParser = reviewParser;
    this.canonical = canonical;
    this.reviewIngest = reviewIngest;
    this.guestIngest = guestIngest;
    this.json = json;
    this.venueClock = venueClock;
    this.restaurantId = restaurantId;
    this.backfillFrom = backfillFrom;
    this.stored = stored;
  }

  @Override
  public String sourceSystem() {
    return OpenTableReservationJsonParser.SOURCE_SYSTEM;
  }

  @Override
  public String connectorName() {
    return "opentable-api";
  }

  @Override
  public void fetch(String watermark, IngestionSink sink) {
    if (!client.isAuthenticated()) {
      throw new ConnectorFetchException(
          "CONNECTOR_AUTH_FAILED",
          "Not signed in to OpenTable; sign in from Data health → Sync OpenTable");
    }
    boolean full = FULL.equalsIgnoreCase(watermark);
    LocalDate today = venueClock.today();

    // Datasets are independent: one failing must not drop the others, and the run must still end
    // PARTIAL/FAILED rather than look complete (same contract as CtbConnector).
    Map<String, ConnectorFetchException> failures = new LinkedHashMap<>();
    pull("reservations", failures, () -> pullReservations(full, today, sink));
    pull("reviews", failures, () -> pullReviews(full, today, sink));
    pull("menu-items", failures, () -> pullMenuItems(sink));
    pull("menus", failures, () -> pullMenus(sink));
    if (!failures.isEmpty()) {
      throw aggregate(failures);
    }
  }

  // ---- reservations ---------------------------------------------------------------------------

  private void pullReservations(boolean full, LocalDate today, IngestionSink sink) {
    // Half-month windows: the busiest month seen held ~1,250 reservations, so a half month fits in
    // one 1000-row page. Paging with offsets is deliberately avoided: GuestCenter sorts by visit
    // time only, and offset pages over tied times skipped rows during discovery. Both ends snap to
    // half-month boundaries so a window keeps the same sourceRecordRef from one sync to the next,
    // which is what lets an unchanged window be recognised and skipped.
    LocalDate from = halfMonthStart(full ? backfillFrom : today.minusDays(INCREMENTAL_PAST_DAYS));
    LocalDate to = halfMonthEnd(today.plusMonths(FUTURE_MONTHS));
    LocalDate start = from;
    while (!start.isAfter(to)) {
      LocalDate end = halfMonthEnd(start);
      pullReservationWindow(start, end, sink);
      start = end.plusDays(1);
    }
  }

  static LocalDate halfMonthStart(LocalDate date) {
    return date.getDayOfMonth() <= 15 ? date.withDayOfMonth(1) : date.withDayOfMonth(16);
  }

  static LocalDate halfMonthEnd(LocalDate date) {
    return date.getDayOfMonth() <= 15
        ? date.withDayOfMonth(15)
        : date.withDayOfMonth(date.lengthOfMonth());
  }

  /** Stores one window, or splits it in half if it came back at the page limit. */
  private void pullReservationWindow(LocalDate start, LocalDate end, IngestionSink sink) {
    BrowserResponse response = client.get(reservationsPath(start, end));
    byte[] body = response.body();
    if (parser.rowCount(body) >= RESERVATION_LIMIT) {
      if (start.equals(end)) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH",
            "OpenTable returned "
                + RESERVATION_LIMIT
                + "+ reservations for one day ("
                + start
                + ")");
      }
      // A full page may be truncated, so it is not stored: the two halves replace it.
      LocalDate mid = start.plusDays(ChronoUnit.DAYS.between(start, end) / 2);
      pullReservationWindow(start, mid, sink);
      pullReservationWindow(mid.plusDays(1), end, sink);
      return;
    }
    UUID rawId = store(body, "opentable-reservations", start + ".." + end, sink);
    for (ReservationInput input : parser.parse(body, rawId)) {
      canonical.record(input);
    }
  }

  private String reservationsPath(LocalDate start, LocalDate end) {
    return "/gateway/long-proxies/restaurant-reporting/reportingBiDatasourcesGold/api/v5/reservations/gold/"
        + "?rid="
        + restaurantId
        + "&startDate="
        + start
        + "&endDate="
        + end
        + "&offset=0&limit="
        + RESERVATION_LIMIT
        + "&sort=visitDate&startTime=&endTime="
        + "&stateCategories=booked%2Cseated%2Cfinished%2CnoShow%2Ccancelled"
        + "&isVisitDate=true";
  }

  // ---- reviews --------------------------------------------------------------------------------

  private void pullReviews(boolean full, LocalDate today, IngestionSink sink) {
    LocalDate from = full ? LocalDate.of(2000, 1, 1) : today.minusDays(INCREMENTAL_REVIEW_DAYS);
    String path =
        "/gateway/proxies/hospitality/ot4rFeedbackService/api/v1/feedback/restaurant/"
            + restaurantId
            + "/reviews/details";
    String range = from + ".." + today;
    int fetched = 0;
    for (int page = 0; page < MAX_REVIEW_PAGES; page++) {
      BrowserResponse response = client.post(path, reviewsBody(from, today, page));
      byte[] body = response.body();
      JsonNode root = readTree(body, "reviews");
      JsonNode data = root.get("data");
      long total = root.path("meta").path("totalCount").asLong(-1);
      if (data == null || !data.isArray() || total < 0) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH",
            "OpenTable reviews response is missing data or totalCount");
      }
      if (data.isEmpty()) {
        return;
      }
      UUID rawId = store(body, "opentable-reviews", range + "#p" + page, sink);
      for (ReviewInput review : reviewParser.parse(body, rawId)) {
        if (review.guestId() != null) {
          guestIngest.ensure(review.guestId(), review.guestName(), rawId);
        }
        reviewIngest.record(review);
      }
      fetched += data.size();
      if (fetched >= total) {
        return;
      }
    }
    throw new ConnectorFetchException(
        "CONNECTOR_SCHEMA_MISMATCH", "OpenTable reviews exceeded " + MAX_REVIEW_PAGES + " pages");
  }

  /**
   * Every filter set to "all", with hidden reviews included ({@code restaurantVisibility}) and
   * "Updated" status selected ({@code showUpdatedByDiner}). An empty {@code sources} means all,
   * including Facebook, which the filter UI doesn't list.
   */
  private String reviewsBody(LocalDate from, LocalDate to, int page) {
    ObjectNode body = json.createObjectNode();
    body.put("startDate", from.atStartOfDay(venueClock.zone()).toInstant().toString());
    body.put(
        "endDate",
        to.plusDays(1).atStartOfDay(venueClock.zone()).toInstant().minusMillis(1).toString());
    body.put("showUpdatedByDiner", true);
    body.putArray("workflowStatuses").add("New").add("NewReply").add("Open").add("Complete");
    body.putArray("sources");
    body.putArray("serverNames");
    body.put("serverNameFilter", "Any");
    body.putArray("ratings").add(1).add(2).add(3).add(4).add(5);
    body.put("search", "");
    body.putArray("restaurantVisibility").add("Visible").add("Hidden");
    body.put("includeTextReviews", true);
    body.put("includeRatingOnlyReviews", true);
    body.put("page", page);
    return body.toString();
  }

  // ---- menu -----------------------------------------------------------------------------------

  /** No {@code sources} filter: the page's default ({@code Opentable}) hides experience add-ons. */
  private void pullMenuItems(IngestionSink sink) {
    for (int page = 1; page <= MAX_MENU_PAGES; page++) {
      BrowserResponse response =
          client.get(
              "/gateway/proxies/menu-manager/restaurantMenuService/v2/restaurants/"
                  + restaurantId
                  + "/items?pageSize="
                  + MENU_PAGE_SIZE
                  + "&pageNumber="
                  + page
                  + "&statuses=Active%2CInactive%2CUnassigned%2CArchived");
      byte[] body = response.body();
      JsonNode items = readTree(body, "menu items").path("data").get("items");
      if (items == null || !items.isArray()) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "OpenTable menu items response has no data.items");
      }
      store(body, "opentable-menu-items", "p" + page, sink);
      if (items.size() < MENU_PAGE_SIZE) {
        return;
      }
    }
    throw new ConnectorFetchException(
        "CONNECTOR_SCHEMA_MISMATCH", "OpenTable menu items exceeded " + MAX_MENU_PAGES + " pages");
  }

  private void pullMenus(IngestionSink sink) {
    BrowserResponse response =
        client.get(
            "/gateway/proxies/menu-manager/restaurantMenuService/v1/restaurants/"
                + restaurantId
                + "/menus");
    byte[] body = response.body();
    readTree(body, "menus"); // Validate before storing: the run should fail loudly on HTML.
    store(body, "opentable-menus", "menus", sink);
  }

  // ---- shared ---------------------------------------------------------------------------------

  /**
   * Hands the response to the sink unless its content matches the latest stored payload for the
   * same dataset window, in which case that payload's id is returned instead (see class docs).
   */
  private UUID store(byte[] body, String fetcherIdentity, String ref, IngestionSink sink) {
    Optional<StoredPayloads.Stored> previous = stored.latest(fetcherIdentity, ref);
    if (previous.isPresent() && sameContent(body, previous.get())) {
      log.debug("OpenTable {} {} unchanged; not re-stored", fetcherIdentity, ref);
      return previous.get().rawRecordId();
    }
    return sink.accept(payload(body, fetcherIdentity, ref));
  }

  /** Exact-hash check first (cheap); otherwise compare with request metadata stripped. */
  private boolean sameContent(byte[] body, StoredPayloads.Stored previous) {
    if (sha256Hex(body).equals(previous.sha256())) {
      return true;
    }
    byte[] previousBytes = previous.bytes().get();
    if (previousBytes == null) {
      return false;
    }
    String current = contentDigest(body);
    return current != null && current.equals(contentDigest(previousBytes));
  }

  /**
   * SHA-256 of the response with its top-level request metadata removed. Field order is kept as the
   * source sent it, which GuestCenter keeps stable. Null when the body isn't a JSON object, so an
   * unparseable payload is never treated as a duplicate.
   */
  private String contentDigest(byte[] body) {
    try {
      JsonNode root = json.readTree(body);
      if (!(root instanceof ObjectNode object)) {
        return null;
      }
      ObjectNode content = object.deepCopy();
      content.remove(VOLATILE_KEYS);
      return sha256Hex(json.writeValueAsBytes(content));
    } catch (IOException e) {
      return null;
    }
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }

  private static FetchedPayload payload(byte[] body, String fetcherIdentity, String ref) {
    return new FetchedPayload(
        FetchMethod.API, "application/json", body, "UTF-8", fetcherIdentity, ref);
  }

  private JsonNode readTree(byte[] body, String what) {
    try {
      JsonNode root = json.readTree(body);
      if (root == null || !root.isObject()) {
        throw new IOException("not an object");
      }
      return root;
    } catch (IOException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "OpenTable " + what + " response is not a JSON object");
    }
  }

  private static void pull(
      String dataset, Map<String, ConnectorFetchException> failures, Runnable attempt) {
    try {
      attempt.run();
    } catch (ConnectorFetchException e) {
      log.warn("OpenTable dataset {} failed: {}", dataset, e.failureType());
      failures.put(dataset, e);
    }
  }

  /** As {@code CtbConnector.aggregate}: one failure verbatim, several summarised. */
  private static ConnectorFetchException aggregate(Map<String, ConnectorFetchException> failures) {
    if (failures.size() == 1) {
      return failures.values().iterator().next();
    }
    String firstType = failures.values().iterator().next().failureType();
    StringBuilder detail = new StringBuilder("OpenTable datasets failed:");
    failures.forEach(
        (dataset, e) ->
            detail
                .append("\n- ")
                .append(dataset)
                .append(": ")
                .append(e.failureType())
                .append(": ")
                .append(e.getMessage()));
    return new ConnectorFetchException(firstType, detail.toString());
  }
}
