package com.goldys.platform.connectors.opentable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps one GuestCenter reporting response ({@code reservations/gold}) to canonical reservation
 * inputs. The response carries ~150 fields per reservation; only the ones {@code
 * canonical_reservation} models are read here, and the rest stay in the raw ledger.
 */
public class OpenTableReservationJsonParser {
  static final String SOURCE_SYSTEM = "OPENTABLE";

  private final ObjectMapper json;
  private final ZoneId zone;

  public OpenTableReservationJsonParser(ObjectMapper json, ZoneId zone) {
    this.json = json;
    this.zone = zone;
  }

  /** Parses the {@code data} array; throws {@code CONNECTOR_SCHEMA_MISMATCH} on a shape change. */
  public List<ReservationInput> parse(byte[] body, UUID rawRecordId) {
    JsonNode data = readTree(body).get("data");
    if (data == null || !data.isArray()) {
      throw schema("OpenTable reservations response has no data array");
    }
    List<ReservationInput> out = new ArrayList<>(data.size());
    for (JsonNode r : data) {
      out.add(
          new ReservationInput(
              SOURCE_SYSTEM,
              requireText(r, "reservationId"),
              reservationAt(requireText(r, "visitDate")),
              requireInt(r, "partySize"),
              status(requireText(r, "stateCategory"), text(r, "primarySource")),
              text(r, "tableId"),
              text(r, "primarySource"),
              text(r, "guestName"),
              rawRecordId));
    }
    return out;
  }

  /** Number of rows in a response, used to detect a window that hit the page limit. */
  public int rowCount(byte[] body) {
    JsonNode data = readTree(body).get("data");
    if (data == null || !data.isArray()) {
      throw schema("OpenTable reservations response has no data array");
    }
    return data.size();
  }

  /**
   * GuestCenter's state categories, in the vocabulary {@code ReservationProjector} counts. Walk-ins
   * arrive as an ordinary seated/finished state with {@code primarySource = walkin}; mapping them
   * to {@code WALK_IN} keeps the projector's walk-in count working (it still counts them as
   * attended).
   */
  static String status(String stateCategory, String primarySource) {
    String state = stateCategory.trim();
    boolean walkIn = "walkin".equalsIgnoreCase(primarySource);
    return switch (state) {
      case "seated" -> walkIn ? "WALK_IN" : "SEATED";
      case "finished" -> walkIn ? "WALK_IN" : "COMPLETED";
      case "booked" -> "BOOKED";
      case "noShow" -> "NO_SHOW";
      case "cancelled" -> "CANCELLED";
      // Unknown categories pass through so the projector treats them as bookings only.
      default -> state.toUpperCase(Locale.ROOT);
    };
  }

  /**
   * {@code visitDate} is venue-local wall time with no offset, e.g. {@code 2026-10-11T18:30:00}.
   */
  private java.time.Instant reservationAt(String visitDate) {
    try {
      return LocalDateTime.parse(visitDate).atZone(zone).toInstant();
    } catch (DateTimeParseException e) {
      throw schema("OpenTable reservation has an unparseable visitDate");
    }
  }

  private JsonNode readTree(byte[] body) {
    try {
      JsonNode root = json.readTree(body);
      if (root == null || !root.isObject()) {
        throw schema("OpenTable reservations response is not a JSON object");
      }
      return root;
    } catch (IOException e) {
      throw schema("OpenTable reservations response is not valid JSON");
    }
  }

  private static String requireText(JsonNode r, String field) {
    String value = text(r, field);
    if (value == null) {
      throw schema("OpenTable reservation is missing '" + field + "'");
    }
    return value;
  }

  private static int requireInt(JsonNode r, String field) {
    JsonNode value = r.get(field);
    if (value == null || !value.canConvertToInt()) {
      throw schema("OpenTable reservation is missing '" + field + "'");
    }
    return value.asInt();
  }

  private static String text(JsonNode r, String field) {
    JsonNode value = r.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    String s = value.asText().trim();
    return s.isEmpty() ? null : s;
  }

  private static ConnectorFetchException schema(String message) {
    return new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message);
  }
}
