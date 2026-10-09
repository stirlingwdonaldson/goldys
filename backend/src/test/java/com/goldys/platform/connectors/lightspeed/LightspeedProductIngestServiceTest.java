package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalProductSalesIngest;
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
class LightspeedProductIngestServiceTest {

  @Mock IngestionService ingestion;
  @Mock CanonicalProductSalesIngest canonical;
  @Mock LightspeedProductCsvParser parser;

  private LightspeedProductIngestService service() {
    return new LightspeedProductIngestService(ingestion, canonical, parser);
  }

  private static byte[] body() {
    return "{\"attachment\":{\"data\":\"csv\"}}".getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void recordsParsedAndCanonicalizedStagesOnSuccess() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    when(parser.parse(any(), any()))
        .thenReturn(
            List.of(
                new LightspeedProductSale(
                    LocalDate.now(), "Beer", BigDecimal.ONE, new BigDecimal("100"))));

    service().ingest(body());

    verify(ingestion)
        .recordStage(any(), eq(IngestionStageKind.PARSED), eq(IngestionStageOutcome.SUCCESS));
    verify(ingestion)
        .recordStage(
            any(), eq(IngestionStageKind.CANONICALIZED), eq(IngestionStageOutcome.SUCCESS));
  }

  @Test
  void recordsEmptyParseWithoutFabricatingCanonicalRows() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    when(parser.parse(any(), any())).thenReturn(List.of());

    service().ingest(body());

    verify(ingestion)
        .recordStage(any(), eq(IngestionStageKind.PARSED), eq(IngestionStageOutcome.EMPTY));
    verify(ingestion)
        .recordStage(any(), eq(IngestionStageKind.CANONICALIZED), eq(IngestionStageOutcome.EMPTY));
    verify(canonical, never()).record(any());
  }

  @Test
  void recordsParseFailure() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    when(parser.parse(any(), any())).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service().ingest(body())).isInstanceOf(RuntimeException.class);

    verify(ingestion)
        .recordStage(any(), eq(IngestionStageKind.PARSED), eq(IngestionStageOutcome.FAILED));
    verify(ingestion, never()).recordStage(any(), eq(IngestionStageKind.CANONICALIZED), any());
  }
}
