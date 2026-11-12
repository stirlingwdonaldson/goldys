package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.canonical.CanonicalDeletedSaleIngest;
import com.goldys.platform.canonical.DeletedSaleInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionProcessing;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.util.Nulls;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lightspeed "all-deleted-orders" webhook pipeline: persist the Looker envelope byte-faithfully,
 * then parse the embedded CSV and record one canonical deleted sale per row.
 */
@Service
public class LightspeedDeletedSaleIngestService {

  private final IngestionService ingestion;
  private final IngestionProcessing processing;
  private final CanonicalDeletedSaleIngest canonical;
  private final LightspeedDeletedSaleCsvParser parser;

  public LightspeedDeletedSaleIngestService(
      IngestionService ingestion,
      CanonicalDeletedSaleIngest canonical,
      LightspeedDeletedSaleCsvParser parser) {
    this.ingestion = ingestion;
    this.processing = new IngestionProcessing(ingestion);
    this.canonical = canonical;
    this.parser = parser;
  }

  public void ingest(byte[] body) {
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-deleted-sales",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-deleted-sales");

    List<LightspeedDeletedSale> deletedSales =
        processing.parse(rawId, () -> parser.parse(LightspeedEnvelope.csv(body)));

    processing.canonicalize(
        rawId,
        deletedSales,
        d -> {
          canonical.record(
              new DeletedSaleInput(
                  "LIGHTSPEED",
                  d.saleOpenedDate(),
                  d.saleNumber(),
                  d.orderType(),
                  d.note(),
                  Nulls.zeroIfNull(d.totalIncTax()),
                  Nulls.zeroIfNull(d.totalExTax()),
                  Nulls.zeroIfNull(d.totalTax()),
                  d.totalCost(),
                  d.openedRegisterCode(),
                  d.openedRegisterName(),
                  d.deletedRegisterCode(),
                  d.deletedRegisterName(),
                  d.staffName(),
                  d.staffCode(),
                  d.deletedByStaffName(),
                  d.deletedByStaffCode(),
                  d.tableNumber(),
                  d.siteId(),
                  d.customerName(),
                  rawId));
        });
  }
}
