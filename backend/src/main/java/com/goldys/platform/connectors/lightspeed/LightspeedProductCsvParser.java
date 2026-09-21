package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** Parses the per-product CSV the scheduled Lightspeed report delivers (the "Sales By" export). */
@Component
public class LightspeedProductCsvParser {
  private static final String[] HEADERS = {
    "Position",
    "Product Number",
    "Product",
    "Quantity",
    "Percent of Quantity",
    "Sale Amount",
    "Percent of Sale Amount",
    "Cost",
    "Percent of Gross Profit"
  };

  public List<LightspeedProductSale> parse(byte[] csv, LocalDate tradingDate) {
    List<LightspeedProductSale> out = new ArrayList<>();
    try (var in = new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8)) {
      for (CSVRecord r :
          CSVFormat.DEFAULT
              .builder()
              .setHeader(HEADERS)
              .setSkipHeaderRecord(true)
              .build()
              .parse(in)) {
        String name = r.get("Product");
        if (name == null || name.isBlank()) continue;
        out.add(
            new LightspeedProductSale(
                tradingDate, name, money(r.get("Quantity")), money(r.get("Sale Amount"))));
      }
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static BigDecimal money(String value) {
    if (value == null || value.isBlank()) return BigDecimal.ZERO;
    try {
      return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    } catch (NumberFormatException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_SCHEMA_MISMATCH", "Bad amount '" + value + "'", e);
    }
  }
}
