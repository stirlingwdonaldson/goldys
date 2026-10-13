package com.goldys.platform.connectors.opentable;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parses the rich 156-column GuestCenter reservation export into {@link OpenTableReservationDetail}
 * rows. The header is read from the CSV and the required columns are validated, so a report-shape
 * change fails loudly rather than silently producing wrong rows.
 */
@Component
public class OpenTableReservationsCsvParser {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final ZoneId zone;

  public OpenTableReservationsCsvParser(
      @Value("${opentable.timezone:Australia/Melbourne}") String zone) {
    this.zone = ZoneId.of(zone);
  }

  public List<OpenTableReservationDetail> parse(byte[] csv) {
    List<CSVRecord> records = readAll(stripBom(csv));
    if (records.size() < 2) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "OpenTable reservations CSV is empty");
    }

    Map<String, Integer> columns = columnIndex(records.get(0));
    require(columns, "Reservation ID", "Visit Date", "Size", "Status");

    List<OpenTableReservationDetail> out = new ArrayList<>();
    int width = columns.size();
    for (int i = 1; i < records.size(); i++) {
      CSVRecord r = records.get(i);
      if (r.size() != width) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "OpenTable reservations CSV row has wrong column count");
      }
      out.add(row(r, columns));
    }
    return out;
  }

  private OpenTableReservationDetail row(CSVRecord r, Map<String, Integer> c) {
    return OpenTableReservationDetail.builder()
        .reservationId(requireValue(c, r, "Reservation ID"))
        .reservationAt(instant(requireValue(c, r, "Visit Date"), "Visit Date"))
        .partySize(requiredInt(c, r, "Size"))
        .status(normalizeStatus(requireValue(c, r, "Status")))
        .tableName(blankToNull(get(c, r, "Table")))
        .sourceChannel(blankToNull(get(c, r, "Source")))
        .partyName(blankToNull(get(c, r, "Guest Name")))
        .createdAt(instant(get(c, r, "Created Date"), "Created Date"))
        .cancelledAt(instant(get(c, r, "Cancelled Date"), "Cancelled Date"))
        .noShowAt(instant(get(c, r, "No-show Date"), "No-show Date"))
        .seatedAt(instant(get(c, r, "Seated Date"), "Seated Date"))
        .finishedAt(instant(get(c, r, "Finished Date"), "Finished Date"))
        .lastUpdatedAt(instant(get(c, r, "Last Updated"), "Last Updated"))
        .totalActualDurationMinutes(
            integer(get(c, r, "Total Actual Duration"), "Total Actual Duration"))
        .tableType(blankToNull(get(c, r, "Table Type")))
        .diningAreaAssigned(blankToNull(get(c, r, "Dining Area Assigned")))
        .diningAreaBooked(blankToNull(get(c, r, "Dining Area Booked")))
        .diningAreaIdAssigned(blankToNull(get(c, r, "Dining Area Id Assigned")))
        .diningAreaIdBooked(blankToNull(get(c, r, "Dining Area Id Booked")))
        .waitStaff(blankToNull(get(c, r, "Wait staff")))
        .createdBy(blankToNull(get(c, r, "Created By")))
        .turnTimeSetting(blankToNull(get(c, r, "Turn Time Setting")))
        .reservationType(blankToNull(get(c, r, "Reservation Type")))
        .occasion(blankToNull(get(c, r, "Occasion")))
        .reservationStatus(blankToNull(get(c, r, "Reservation Status")))
        .onlineWaitlist(bool(get(c, r, "Online Waitlist"), "Online Waitlist"))
        .isFlexReso(bool(get(c, r, "Is Flex Reso"), "Is Flex Reso"))
        .quotedWaitTime(integer(get(c, r, "Quoted Wait Time"), "Quoted Wait Time"))
        .totalWaitTime(integer(get(c, r, "Total Wait Time"), "Total Wait Time"))
        .onlineChannel(blankToNull(get(c, r, "Online Channel")))
        .partner(blankToNull(get(c, r, "Partner")))
        .campaignType(blankToNull(get(c, r, "OpenTable Campaign Type")))
        .campaignDetails(blankToNull(get(c, r, "Campaign Details")))
        .referrerName(blankToNull(get(c, r, "Referrer Name")))
        .referrerBusiness(blankToNull(get(c, r, "Referrer Business")))
        .restrefSource(blankToNull(get(c, r, "RestRef Source")))
        .restrefCampaignName(blankToNull(get(c, r, "RestRef Campaign Name")))
        .restrefId(blankToNull(get(c, r, "RestRef ID")))
        .guestFirstName(blankToNull(get(c, r, "First Name")))
        .guestLastName(blankToNull(get(c, r, "Last Name")))
        .guestNickname(blankToNull(get(c, r, "Guest Nickname")))
        .guestEmail(pii(get(c, r, "Email")))
        .guestPhone(pii(get(c, r, "Phone Number")))
        .company(blankToNull(get(c, r, "Company")))
        .marketingOptIn(bool(get(c, r, "Marketing Opt-In"), "Marketing Opt-In"))
        .birthday(date(get(c, r, "Birthday"), "Birthday"))
        .anniversary(date(get(c, r, "Anniversary"), "Anniversary"))
        .firstVisitDate(date(get(c, r, "First Visit Date"), "First Visit Date"))
        .recentVisitDate(date(get(c, r, "Recent Visit Date"), "Recent Visit Date"))
        .upcomingVisits(integer(get(c, r, "Upcoming Visits"), "Upcoming Visits"))
        .completedVisits(integer(get(c, r, "Completed Visits"), "Completed Visits"))
        .cancelledCount(integer(get(c, r, "Cancelled Count"), "Cancelled Count"))
        .noShowCount(integer(get(c, r, "No-Show Count"), "No-Show Count"))
        .lifetimeSpend(decimal(get(c, r, "Lifetime Spend"), "Lifetime Spend"))
        .lifetimePerCoverSpend(
            decimal(get(c, r, "Lifetime Per Cover Spend"), "Lifetime Per Cover Spend"))
        .guestId(null)
        .experienceTitle(blankToNull(get(c, r, "Experience Title")))
        .experienceType(blankToNull(get(c, r, "Experience Type")))
        .experienceTypeEnum(blankToNull(get(c, r, "Experience Type Enum")))
        .experiencePriceType(blankToNull(get(c, r, "Experience Price Type")))
        .prepaymentMethod(blankToNull(get(c, r, "Prepayment Method")))
        .prepaymentStatus(blankToNull(get(c, r, "Prepayment Status")))
        .vendorPayoutStatus(blankToNull(get(c, r, "Vendor Payout Status")))
        .currency(blankToNull(get(c, r, "Currency")))
        .experiencePrice(decimal(get(c, r, "Experience Price"), "Experience Price"))
        .experienceSubtotal(decimal(get(c, r, "Experience Subtotal"), "Experience Subtotal"))
        .addonsSubtotal(decimal(get(c, r, "Addons Subtotal"), "Addons Subtotal"))
        .serviceCharges(decimal(get(c, r, "Service charges"), "Service charges"))
        .prepaidExperienceTip(
            decimal(get(c, r, "Prepaid Experience Tip"), "Prepaid Experience Tip"))
        .prepaidExperienceVat(
            decimal(get(c, r, "Prepaid Experience VAT"), "Prepaid Experience VAT"))
        .prepaidExperienceTotalPaid(
            decimal(get(c, r, "Prepaid Experience Total Paid"), "Prepaid Experience Total Paid"))
        .experienceTotalSales(
            decimal(get(c, r, "Experience Total Sales"), "Experience Total Sales"))
        .experienceTotalSalesWithTip(
            decimal(
                get(c, r, "Experience Total Sales with Tip"), "Experience Total Sales with Tip"))
        .prepaymentRequiredTotal(
            decimal(get(c, r, "Prepayment Required Total"), "Prepayment Required Total"))
        .prepaymentPaidTotal(decimal(get(c, r, "Prepayment Paid Total"), "Prepayment Paid Total"))
        .posCheckIds(blankToNull(get(c, r, "POS Check IDs")))
        .posSubtotal(decimal(get(c, r, "POS Subtotal"), "POS Subtotal"))
        .posTax(decimal(get(c, r, "POS Tax"), "POS Tax"))
        .posServiceCharges(decimal(get(c, r, "POS Service Charges"), "POS Service Charges"))
        .posTip(decimal(get(c, r, "POS Tip"), "POS Tip"))
        .posPaid(decimal(get(c, r, "POS Paid"), "POS Paid"))
        .posDue(decimal(get(c, r, "POS Due"), "POS Due"))
        .totalTips(decimal(get(c, r, "Total Tips"), "Total Tips"))
        .totalVat(decimal(get(c, r, "Total VAT"), "Total VAT"))
        .totalIncome(decimal(get(c, r, "Total Income"), "Total Income"))
        .totalIncomeWithTip(decimal(get(c, r, "Total Income with Tip"), "Total Income with Tip"))
        .totalRevenue(decimal(get(c, r, "Total Revenue"), "Total Revenue"))
        .totalRevenueWithTip(decimal(get(c, r, "Total Revenue With Tip"), "Total Revenue With Tip"))
        .totalRevenueWithoutTip(
            decimal(get(c, r, "Total Revenue Without Tip"), "Total Revenue Without Tip"))
        .posSpend(decimal(get(c, r, "POS Spend"), "POS Spend"))
        .posRefund(decimal(get(c, r, "POS Refund"), "POS Refund"))
        .posRevenue(decimal(get(c, r, "POS Revenue"), "POS Revenue"))
        .refundAmount(decimal(get(c, r, "Refund Amount"), "Refund Amount"))
        .vendorPayoutAmount(decimal(get(c, r, "Vendor Payout Amount"), "Vendor Payout Amount"))
        .guestRequests(blankToNull(get(c, r, "Guest Requests")))
        .visitNotes(blankToNull(get(c, r, "Visit Notes")))
        .reservationTags(blankToNull(get(c, r, "Reservation Tags")))
        .guestTags(blankToNull(get(c, r, "Guest Tags")))
        .build();
  }

  private Instant instant(String value, String name) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    try {
      return LocalDateTime.parse(v, TIMESTAMP).atZone(zone).toInstant();
    } catch (RuntimeException e) {
      throw schemaMismatch("Bad timestamp in column '" + name + "'", e);
    }
  }

  private static int requiredInt(Map<String, Integer> c, CSVRecord r, String name) {
    String value = requireValue(c, r, name);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw schemaMismatch("Bad integer in column '" + name + "'", e);
    }
  }

  private Integer integer(String value, String name) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    try {
      return Integer.valueOf(v);
    } catch (NumberFormatException e) {
      throw schemaMismatch("Bad integer in column '" + name + "'", e);
    }
  }

  private BigDecimal decimal(String value, String name) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    // Amounts are plain numbers, but strip a currency prefix defensively in case a future export
    // decorates them (the lightspeed pattern).
    String stripped = v;
    if (stripped.startsWith("A$") || stripped.startsWith("a$")) {
      stripped = stripped.substring(2).trim();
    } else if (stripped.startsWith("$")) {
      stripped = stripped.substring(1).trim();
    }
    try {
      return new BigDecimal(stripped);
    } catch (NumberFormatException e) {
      throw schemaMismatch("Bad amount in column '" + name + "'", e);
    }
  }

  private LocalDate date(String value, String name) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    try {
      // First/Recent Visit Date are full timestamps; Birthday/Anniversary are pure dates.
      if (v.length() > 10) {
        return LocalDateTime.parse(v, TIMESTAMP).toLocalDate();
      }
      return LocalDate.parse(v, DATE);
    } catch (RuntimeException e) {
      throw schemaMismatch("Bad date in column '" + name + "'", e);
    }
  }

  private Boolean bool(String value, String name) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    return switch (v.toLowerCase(Locale.ROOT)) {
      case "true" -> Boolean.TRUE;
      case "false" -> Boolean.FALSE;
      default -> throw schemaMismatch("Bad boolean in column '" + name + "'", null);
    };
  }

  private static String normalizeStatus(String raw) {
    if (raw == null) {
      return null;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "finished", "completed" -> "COMPLETED";
      case "seated" -> "SEATED";
      case "cancelled", "canceled" -> "CANCELLED";
      case "booked" -> "BOOKED";
      case "no show", "no-show" -> "NO_SHOW";
      case "walk-in", "walk in" -> "WALK_IN";
      default -> raw.trim().toUpperCase(Locale.ROOT);
    };
  }

  private static ConnectorFetchException schemaMismatch(String message, Throwable cause) {
    return cause == null
        ? new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message)
        : new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message, cause);
  }

  private static byte[] stripBom(byte[] csv) {
    if (csv.length >= 3
        && (csv[0] & 0xFF) == 0xEF
        && (csv[1] & 0xFF) == 0xBB
        && (csv[2] & 0xFF) == 0xBF) {
      return Arrays.copyOfRange(csv, 3, csv.length);
    }
    return csv;
  }

  private static List<CSVRecord> readAll(byte[] csv) {
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      return CSVFormat.DEFAULT.parse(in).getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Malformed OpenTable reservations CSV", e);
    }
  }

  private static Map<String, Integer> columnIndex(CSVRecord header) {
    Map<String, Integer> out = new HashMap<>();
    for (int i = 0; i < header.size(); i++) {
      out.put(header.get(i).trim(), i);
    }
    return out;
  }

  private static void require(Map<String, Integer> columns, String... names) {
    for (String name : names) {
      if (!columns.containsKey(name)) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH",
            "Missing column '" + name + "' in OpenTable reservations CSV");
      }
    }
  }

  private static String get(Map<String, Integer> columns, CSVRecord r, String name) {
    Integer i = columns.get(name);
    return i == null ? null : r.get(i);
  }

  private static String requireValue(Map<String, Integer> columns, CSVRecord r, String name) {
    String value = get(columns, r, name);
    if (value == null || value.isBlank()) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH",
          "Missing value for '" + name + "' in OpenTable reservations CSV");
    }
    return value.trim();
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Masked PII (e.g. {@code **********}) maps to null, like a blank cell. */
  private static String pii(String value) {
    String v = blankToNull(value);
    if (v == null) {
      return null;
    }
    return v.chars().allMatch(ch -> ch == '*') ? null : v;
  }
}
