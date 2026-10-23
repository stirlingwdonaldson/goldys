package com.goldys.platform.connectors.ctb;

import com.goldys.platform.connectors.lightspeed.LightspeedSaleItem;
import com.goldys.platform.connectors.lightspeed.LightspeedSaleItemCsvParser;
import com.goldys.platform.connectors.lightspeed.LightspeedSaleItemIngestService;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Database-free parser check: payload bytes enter stdin; only counts leave stdout. */
public final class RetainedPayloadProbe {
  public static void main(String[] args) {
    try {
      byte[] bytes = System.in.readAllBytes();
      UUID raw = UUID.randomUUID();
      if (args.length == 1 && args[0].equals("ctb")) {
        var items = new CtbSaleItemParser().parse(bytes);
        var batch = new CtbProductSalesBatch(LocalDate.of(2026, 1, 1));
        items.forEach(item -> batch.add(item, raw));
        System.out.printf("{\"rows\":%d,\"products\":%d}%n", items.size(), batch.totals().size());
      } else if (args.length == 1 && args[0].equals("lightspeed")) {
        var service = new LightspeedSaleItemIngestService(null, null, new LightspeedSaleItemCsvParser());
        var rows = service.preview(new RawLedgerQuery.RawPayload(raw, bytes));
        Map<String, LightspeedSaleItem> unique = new LinkedHashMap<>();
        int conflicts = 0;
        for (var row : rows) {
          var prior = unique.putIfAbsent(row.receiptLineId(), row);
          if (prior != null && !prior.equals(row)) conflicts++;
        }
        System.out.printf("{\"rows\":%d,\"keys\":%d,\"conflictingRepeats\":%d}%n",
            rows.size(), unique.size(), conflicts);
        if (conflicts != 0) System.exit(1);
      } else throw new IllegalArgumentException();
    } catch (Exception e) {
      // Parser messages can contain source values. Do not print messages or stack traces.
      System.err.println("Payload validation failed: " + e.getClass().getSimpleName());
      System.exit(1);
    }
  }
}
