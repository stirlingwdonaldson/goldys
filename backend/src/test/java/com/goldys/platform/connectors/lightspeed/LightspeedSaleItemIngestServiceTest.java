package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalSaleItemIngest;
import com.goldys.platform.canonical.SaleItemInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LightspeedSaleItemIngestServiceTest {

  @Mock IngestionService ingestion;
  @Mock CanonicalSaleItemIngest canonical;
  @Mock LightspeedSaleItemCsvParser parser;

  private LightspeedSaleItemIngestService service() {
    return new LightspeedSaleItemIngestService(ingestion, canonical, parser);
  }

  private static byte[] body() {
    return "{\"attachment\":{\"data\":\"csv\"}}".getBytes(StandardCharsets.UTF_8);
  }

  private static LightspeedSaleItem item() {
    return new LightspeedSaleItem(
        "LI-1",
        LocalDate.parse("2026-09-19"),
        "SP-100 0919000001",
        "Burger",
        "P-100",
        "SKU100",
        "Food",
        BigDecimal.ONE,
        new BigDecimal("22.00"),
        new BigDecimal("22.00"),
        new BigDecimal("2.00"),
        new BigDecimal("5.00"),
        "Dine in",
        "Sale",
        "Example Staff",
        "Goldy's Main Bar",
        "12");
  }

  @Test
  void persistsRawThenParsesAndCanonicalizes() {
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);
    when(parser.parse(any())).thenReturn(List.of(item()));

    service().ingest(body());

    verify(ingestion)
        .ingestPush(
            eq("LIGHTSPEED"),
            eq("lightspeed-sale-items"),
            eq(FetchMethod.FILE_EXPORT),
            eq("application/json"),
            any(),
            eq(StandardCharsets.UTF_8.name()),
            eq("lightspeed-sale-items"));
    verify(canonical).record(any(SaleItemInput.class));
    verify(ingestion)
        .recordStage(eq(rawId), eq(IngestionStageKind.PARSED), eq(IngestionStageOutcome.SUCCESS));
    verify(ingestion)
        .recordStage(
            eq(rawId), eq(IngestionStageKind.CANONICALIZED), eq(IngestionStageOutcome.SUCCESS));
  }

  @Test
  void recordsEmptyParseWithoutFabricatingCanonicalRows() {
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);
    when(parser.parse(any())).thenReturn(List.of());

    service().ingest(body());

    verify(ingestion)
        .recordStage(eq(rawId), eq(IngestionStageKind.PARSED), eq(IngestionStageOutcome.EMPTY));
    verify(ingestion)
        .recordStage(
            eq(rawId), eq(IngestionStageKind.CANONICALIZED), eq(IngestionStageOutcome.EMPTY));
    verify(canonical, never()).record(any());
  }

  @Test
  void recordsParseFailure() {
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);
    when(parser.parse(any())).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service().ingest(body())).isInstanceOf(RuntimeException.class);

    verify(ingestion)
        .recordStage(eq(rawId), eq(IngestionStageKind.PARSED), eq(IngestionStageOutcome.FAILED));
    verify(ingestion, never()).recordStage(any(), eq(IngestionStageKind.CANONICALIZED), any());
  }

  @Test
  void extractsAttachmentDataBeyondDefaultJacksonStringLimit() {
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);
    when(parser.parse(any())).thenReturn(List.of());

    // The real "all data, ever" reports carry attachment.data well over Jackson's default 20MB
    // string cap; without the raised StreamReadConstraints this is reported as "Non-JSON".
    String large = "x".repeat(21 * 1024 * 1024);
    byte[] big = ("{\"attachment\":{\"data\":\"" + large + "\"}}").getBytes(StandardCharsets.UTF_8);

    service().ingest(big);

    verify(parser).parse(any());
  }
}
