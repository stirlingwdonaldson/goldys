package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.ProductNameKey;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Parses invoice-PDF text into line items. PROVISIONAL: the real invoice layout is unconfirmed, so
 * this assumes each line item is "product name … quantity unit-cost line-total" (the last three
 * whitespace-separated tokens are numeric). It emits observations only — product, qty, unit cost,
 * line total — never COGS or food-cost figures.
 */
@Component
public class InvoiceLineTextParser {

  public List<InvoiceLine> parse(String text) {
    List<InvoiceLine> out = new ArrayList<>();
    for (String line : text.split("\\R")) {
      parseLine(line).ifPresent(out::add);
    }
    return out;
  }

  private static Optional<InvoiceLine> parseLine(String line) {
    String[] tokens = line.trim().split("\\s+");
    if (tokens.length < 4) {
      return Optional.empty();
    }
    try {
      BigDecimal quantity = new BigDecimal(tokens[tokens.length - 3]);
      BigDecimal unitCost = new BigDecimal(tokens[tokens.length - 2]);
      BigDecimal lineTotal = new BigDecimal(tokens[tokens.length - 1]);
      String name = String.join(" ", Arrays.copyOfRange(tokens, 0, tokens.length - 3));
      return Optional.of(
          new InvoiceLine(ProductNameKey.normalize(name), quantity, unitCost, lineTotal));
    } catch (NumberFormatException e) {
      return Optional.empty(); // header/footer noise
    }
  }
}
