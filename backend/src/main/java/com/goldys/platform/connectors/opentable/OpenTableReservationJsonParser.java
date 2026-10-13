package com.goldys.platform.connectors.opentable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
      out.add(toInput(r, rawRecordId));
    }
    return out;
  }

  /**
   * Maps one reservation object to the full canonical shape. The JSON carries the same rich field
   * set as the CSV export, but with different names (guest-lifetime rollups under a {@code diner…}
   * prefix, {@code server} for wait staff, {@code gratuity}/{@code tax} for tip/VAT) and money in
   * cents (divided by the row's {@code currencyMultiplier}). Mapping every field keeps both ingest
   * paths writing complete records, so a JSON sync can never bitemporally blank a CSV backfill.
   */
  private ReservationInput toInput(JsonNode r, UUID rawRecordId) {
    int multiplier = currencyMultiplier(r);
    return ReservationInput.builder()
        .sourceSystem(SOURCE_SYSTEM)
        .reservationId(requireText(r, "reservationId"))
        .reservationAt(reservationAt(requireText(r, "visitDate")))
        .partySize(requireInt(r, "partySize"))
        .status(status(requireText(r, "stateCategory"), text(r, "primarySource")))
        .tableName(text(r, "tableId"))
        .sourceChannel(text(r, "primarySource"))
        .partyName(text(r, "guestName"))
        .rawRecordId(rawRecordId)
        // Visit lifecycle
        .createdAt(optionalInstant(r, "createdDate"))
        .cancelledAt(optionalInstant(r, "cancelledDate"))
        .noShowAt(optionalInstant(r, "noShowDate"))
        .seatedAt(optionalInstant(r, "seatedDate"))
        .finishedAt(optionalInstant(r, "finishedDate"))
        .lastUpdatedAt(optionalInstant(r, "lastUpdatedDate"))
        .totalActualDurationMinutes(intOrNull(r, "totalActualDuration"))
        // Operational / floor
        .tableType(text(r, "tableType"))
        .diningAreaAssigned(text(r, "diningAreaAssigned"))
        .diningAreaBooked(text(r, "diningAreaBooked"))
        .diningAreaIdAssigned(idOrNull(r, "diningAreaIdAssigned"))
        .diningAreaIdBooked(idOrNull(r, "diningAreaIdBooked"))
        .waitStaff(text(r, "server"))
        .createdBy(text(r, "createdBy"))
        .turnTimeSetting(text(r, "turnTimeSetting"))
        .reservationType(text(r, "reservationType"))
        .occasion(text(r, "occasion"))
        .reservationStatus(text(r, "reservationStatus"))
        .onlineWaitlist(boolOrNull(r, "onlineWaitList"))
        .isFlexReso(boolOrNull(r, "isFlexReso"))
        .quotedWaitTime(intOrNull(r, "quotedWaitTime"))
        .totalWaitTime(intOrNull(r, "totalWaitTime"))
        // Booking source / marketing
        .onlineChannel(text(r, "onlineChannel"))
        .partner(text(r, "partner"))
        .campaignType(text(r, "otBoostType"))
        .campaignDetails(text(r, "campaignDetails"))
        .referrerName(text(r, "referrerName"))
        .referrerBusiness(text(r, "referrerBusiness"))
        .restrefSource(text(r, "restrefSource"))
        .restrefCampaignName(text(r, "restrefCampaignName"))
        .restrefId(text(r, "restrefId"))
        // Guest (identity + reported lifetime rollups)
        .guestFirstName(text(r, "firstName"))
        .guestLastName(text(r, "lastName"))
        .guestNickname(text(r, "nickname"))
        // guestEmail / guestPhone left null: the JSON returns them unmasked, but the CSV path masks
        // them and storing real contact data is a privacy decision not yet made (see the design
        // note).
        .company(text(r, "company"))
        .marketingOptIn(boolOrNull(r, "marketingOptIn"))
        .birthday(date(r, "birthday"))
        .anniversary(date(r, "anniversary"))
        .firstVisitDate(date(r, "dinerFirstVisitDate"))
        .recentVisitDate(date(r, "dinerRecentVisitDate"))
        .upcomingVisits(intOrNull(r, "dinerUpcomingVisits"))
        .completedVisits(intOrNull(r, "dinerCompletedVisits"))
        .cancelledCount(intOrNull(r, "dinerCancelledCount"))
        .noShowCount(intOrNull(r, "dinerNoShowCount"))
        .lifetimeSpend(money(r, "dinerLifetimeSpend", multiplier))
        .lifetimePerCoverSpend(money(r, "dinerLifetimePerCoverSpend", multiplier))
        // guestId stays null — the JSON has no Guest ID column.
        // Experience / prepayment
        .experienceTitle(text(r, "experienceTitle"))
        .experienceType(text(r, "experienceType"))
        .experienceTypeEnum(text(r, "experienceTypeEnum"))
        .experiencePriceType(text(r, "experiencePriceType"))
        .prepaymentMethod(text(r, "prepaymentMethod"))
        .prepaymentStatus(text(r, "prepaymentStatus"))
        .vendorPayoutStatus(text(r, "vendorPayoutStatus"))
        .currency(currency(r, "currency"))
        .experiencePrice(money(r, "experiencePricePerCover", multiplier))
        .experienceSubtotal(money(r, "experienceSubtotal", multiplier))
        .addonsSubtotal(money(r, "experienceAddonsSubtotal", multiplier))
        .serviceCharges(money(r, "experienceServiceCharges", multiplier))
        .prepaidExperienceTip(money(r, "experienceGratuity", multiplier))
        .prepaidExperienceVat(money(r, "experienceTax", multiplier))
        .experienceTotalSales(money(r, "experienceTotalRevenue", multiplier))
        .experienceTotalSalesWithTip(money(r, "experienceTotalRevenueWithTip", multiplier))
        .prepaymentRequiredTotal(money(r, "prepaymentRequiredTotal", multiplier))
        .prepaymentPaidTotal(money(r, "prepaymentPaidTotal", multiplier))
        // Payment / revenue (POS breakdown)
        .posCheckIds(text(r, "posCheckIds"))
        .posSubtotal(money(r, "posSubtotal", multiplier))
        .posTax(money(r, "posTax", multiplier))
        .posServiceCharges(money(r, "posServiceCharges", multiplier))
        .posTip(money(r, "posGratuity", multiplier))
        .posPaid(money(r, "posPaid", multiplier))
        .posDue(money(r, "posDue", multiplier))
        .totalTips(money(r, "totalGratuity", multiplier))
        .totalVat(money(r, "totalTax", multiplier))
        .totalIncome(money(r, "totalIncomeWithoutTip", multiplier))
        .totalIncomeWithTip(money(r, "totalIncomeWithTip", multiplier))
        .totalRevenue(money(r, "totalRevenue", multiplier))
        .totalRevenueWithTip(money(r, "totalRevenueWithTip", multiplier))
        .totalRevenueWithoutTip(money(r, "totalRevenueWithoutTip", multiplier))
        .posSpend(money(r, "posSpend", multiplier))
        .posRefund(money(r, "posRefund", multiplier))
        .posRevenue(money(r, "posRevenue", multiplier))
        .refundAmount(money(r, "refundAmount", multiplier))
        .vendorPayoutAmount(money(r, "vendorPayoutAmount", multiplier))
        // Notes / tags
        .guestRequests(text(r, "guestRequests"))
        .visitNotes(text(r, "visitNotes"))
        .reservationTags(text(r, "reservationTags"))
        .guestTags(text(r, "guestTags"))
        .build();
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

  /** Optional wall-time timestamp, venue-local with no offset; null when absent or unparseable. */
  private java.time.Instant optionalInstant(JsonNode r, String field) {
    String value = text(r, field);
    if (value == null) {
      return null;
    }
    try {
      return LocalDateTime.parse(value).atZone(zone).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** The row's money divisor (100 for AUD); defaults to 100 when absent. */
  private static int currencyMultiplier(JsonNode r) {
    JsonNode value = r.get("currencyMultiplier");
    return value != null && value.canConvertToInt() && value.asInt() > 0 ? value.asInt() : 100;
  }

  /** Integer cents divided by the multiplier → dollars; null when absent or non-numeric. */
  private static BigDecimal money(JsonNode r, String field, int multiplier) {
    JsonNode value = r.get(field);
    if (value == null || value.isNull() || !value.isNumber()) {
      return null;
    }
    return value.decimalValue().divide(BigDecimal.valueOf(multiplier), 2, RoundingMode.HALF_UP);
  }

  private static Integer intOrNull(JsonNode r, String field) {
    JsonNode value = r.get(field);
    if (value == null || value.isNull() || !value.canConvertToInt()) {
      return null;
    }
    return value.asInt();
  }

  private static Boolean boolOrNull(JsonNode r, String field) {
    JsonNode value = r.get(field);
    if (value == null || value.isNull() || !value.isBoolean()) {
      return null;
    }
    return value.asBoolean();
  }

  /** Dining-area ids are 0 when unassigned; normalise that to null like the CSV's blank cells. */
  private static String idOrNull(JsonNode r, String field) {
    String value = text(r, field);
    return "0".equals(value) ? null : value;
  }

  /** Date-only or timestamp → {@link LocalDate}; null when absent or unparseable. */
  private static LocalDate date(JsonNode r, String field) {
    String value = text(r, field);
    if (value == null) {
      return null;
    }
    try {
      return LocalDateTime.parse(value).toLocalDate();
    } catch (DateTimeParseException e) {
      try {
        return LocalDate.parse(value);
      } catch (DateTimeParseException e2) {
        return null;
      }
    }
  }

  /** Currency code uppercased ("aud" → "AUD"); null when absent. */
  private static String currency(JsonNode r, String field) {
    String value = text(r, field);
    return value == null ? null : value.toUpperCase(Locale.ROOT);
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
