package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalDeletedSaleIngest;
import com.goldys.platform.canonical.DeletedSaleInput;
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
class LightspeedDeletedSaleIngestServiceTest {

  @Mock IngestionService ingestion;
  @Mock CanonicalDeletedSaleIngest canonical;
  @Mock LightspeedDeletedSaleCsvParser parser;

  private LightspeedDeletedSaleIngestService service() {
    return new LightspeedDeletedSaleIngestService(ingestion, canonical, parser);
  }

  private static byte[] body() {
    return "{\"attachment\":{\"data\":\"csv\"}}".getBytes(StandardCharsets.UTF_8);
  }

  private static LightspeedDeletedSale deletedSale() {
    return new LightspeedDeletedSale(
        LocalDate.parse("2026-10-09"),
        "SP-6 1009105801",
        "Unspecified",
        "Test",
        new BigDecimal("12"),
        new BigDecimal("10.91"),
        new BigDecimal("1.09"),
        null,
        "Functions_POS",
        "Functions POS",
        "Functions_POS",
        "Functions POS",
        "Example Staff",
        null,
        "Example Staff",
        null,
        null,
        "96181",
        null);
  }

  @Test
  void persistsRawThenParsesAndCanonicalizes() {
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);
    when(parser.parse(any())).thenReturn(List.of(deletedSale()));

    service().ingest(body());

    verify(ingestion)
        .ingestPush(
            eq("LIGHTSPEED"),
            eq("lightspeed-deleted-sales"),
            eq(FetchMethod.FILE_EXPORT),
            eq("application/json"),
            any(),
            eq(StandardCharsets.UTF_8.name()),
            eq("lightspeed-deleted-sales"));
    verify(canonical).record(any(DeletedSaleInput.class));
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
}
