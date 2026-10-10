package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LightspeedAllSalesIngestServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void persistsRawAndAggregatesPerSaleDate() throws Exception {
    IngestionService ingestion = Mockito.mock(IngestionService.class);
    CanonicalDailySalesIngest canonical = Mockito.mock(CanonicalDailySalesIngest.class);
    LightspeedAllSalesIngestService service =
        new LightspeedAllSalesIngestService(
            ingestion, canonical, new LightspeedAllSalesCsvParser());

    String csv =
        "Sales Data Sale Closed Date,Sales Data Total Inc Tax,Sales Data Total Tax\n"
            + "2026-09-20,64.00,5.26\n"
            + "2026-09-20,10.00,0.91\n"
            + "2026-09-21,23.50,2.14\n";
    ObjectNode root = MAPPER.createObjectNode();
    root.putObject("attachment").put("data", csv);
    byte[] body = MAPPER.writeValueAsBytes(root);

    service.ingest(body);

    verify(ingestion)
        .ingestPush(
            "LIGHTSPEED",
            "lightspeed-all-sales",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-all-sales");

    ArgumentCaptor<DailySalesInput> captor = ArgumentCaptor.forClass(DailySalesInput.class);
    Mockito.verify(canonical, Mockito.times(2)).record(captor.capture());
    var inputs = captor.getAllValues();

    DailySalesInput day20 =
        inputs.stream()
            .filter(i -> i.tradingDate().equals(LocalDate.parse("2026-09-20")))
            .findFirst()
            .orElseThrow();
    assertThat(day20.totalSales()).isEqualByComparingTo("74.00");
    assertThat(day20.gstTotal()).isEqualByComparingTo("6.17");
    assertThat(day20.netTotal()).isEqualByComparingTo("67.83");

    DailySalesInput day21 =
        inputs.stream()
            .filter(i -> i.tradingDate().equals(LocalDate.parse("2026-09-21")))
            .findFirst()
            .orElseThrow();
    assertThat(day21.totalSales()).isEqualByComparingTo("23.50");
    assertThat(day21.gstTotal()).isEqualByComparingTo("2.14");
  }
}
