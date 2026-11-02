package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.*;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;

/** Parses real Sales Feed and mapping exports; source time is kept local and unqualified. */
@Component
public class LightspeedReceiptCsvParser {
  private static final List<String> MAPPING_HEADERS =
      List.of(
          "Sales Data Receipt Line ID",
          "Saleline Payments Sale Number",
          "Site Numeric ID",
          "Sales Data Sale Closed Date");

  public ReceiptImportScope observedMappingScope(byte[] csv, long siteNumber, String siteName) {
    try (var parser = read(csv)) {
      LocalDate from = null, to = null;
      for (var row : parser) {
        LocalDate date = LocalDate.parse(required(row, "Sales Data Sale Closed Date"));
        if (from == null || date.isBefore(from)) from = date;
        if (to == null || date.isAfter(to)) to = date;
      }
      if (from == null) throw invalid();
      return new ReceiptImportScope(siteNumber, siteName, from, to);
    } catch (IOException | RuntimeException e) {
      throw invalid();
    }
  }

  public List<ReceiptMappingInput> mappings(byte[] csv, ReceiptImportScope scope) {
    try (var parser = read(csv)) {
      if (!new HashSet<>(parser.getHeaderNames()).equals(new HashSet<>(MAPPING_HEADERS)))
        throw invalid();
      Set<ReceiptMappingInput> out = new LinkedHashSet<>();
      for (var r : parser) {
        long site = Long.parseLong(required(r, "Site Numeric ID"));
        LocalDate date = LocalDate.parse(required(r, "Sales Data Sale Closed Date"));
        scope.requireDate(date);
        if (site != scope.siteNumber()) throw invalid();
        out.add(
            new ReceiptMappingInput(
                required(r, "Sales Data Receipt Line ID"),
                optional(r, "Saleline Payments Sale Number"),
                site,
                date));
      }
      return List.copyOf(out);
    } catch (IOException | RuntimeException e) {
      throw invalid();
    }
  }

  public List<SaleTransactionInput> transactions(byte[] csv, ReceiptImportScope scope) {
    try (var parser = read(csv)) {
      if (!parser
          .getHeaderNames()
          .containsAll(
              List.of(
                  "SaleID",
                  "SaleNo",
                  "SaleDate",
                  "SiteName",
                  "LinkedSaleID",
                  "Net Amount",
                  "Tax Amount",
                  "Tip",
                  "Total"))) throw invalid();
      Map<String, SaleTransactionInput> out = new LinkedHashMap<>();
      for (var r : parser) {
        if (!required(r, "SiteName").equals(scope.siteName())) throw invalid();
        LocalDateTime time =
            LocalDateTime.parse(
                required(r, "SaleDate"),
                DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        scope.requireDate(time.toLocalDate());
        var row =
            new SaleTransactionInput(
                required(r, "SaleID"),
                optional(r, "SaleNo"),
                optional(r, "LinkedSaleID"),
                time,
                amount(r, "Net Amount"),
                amount(r, "Tax Amount"),
                amount(r, "Tip"),
                amount(r, "Total"));
        if (row.net().add(row.tax()).add(row.tip()).compareTo(row.total()) != 0) throw invalid();
        var previous = out.putIfAbsent(row.transactionId(), row);
        if (previous != null && !previous.equals(row)) throw invalid();
      }
      return List.copyOf(out.values());
    } catch (IOException | RuntimeException e) {
      throw invalid();
    }
  }

  private static CSVParser read(byte[] csv) throws IOException {
    var decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    var parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setAllowMissingColumnNames(false)
            .build()
            .parse(new InputStreamReader(new ByteArrayInputStream(csv), decoder));
    if (new HashSet<>(parser.getHeaderNames()).size() != parser.getHeaderNames().size()) {
      parser.close();
      throw invalid();
    }
    return parser;
  }

  private static String optional(CSVRecord row, String name) {
    String value = row.get(name).trim();
    if (value.length() > 255 || !row.isConsistent()) throw invalid();
    return value.isEmpty() ? null : value;
  }

  private static String required(CSVRecord row, String name) {
    String v = optional(row, name);
    if (v == null) throw invalid();
    return v;
  }

  private static BigDecimal amount(CSVRecord row, String name) {
    BigDecimal value = new BigDecimal(required(row, name));
    if (value.scale() > 4 || value.precision() - value.scale() > 14) throw invalid();
    return value;
  }

  private static ConnectorFetchException invalid() {
    return new ConnectorFetchException(
        "CONNECTOR_SCHEMA_MISMATCH",
        "Receipt export has invalid headers, rows, scope or amounts; review retained evidence");
  }
}
