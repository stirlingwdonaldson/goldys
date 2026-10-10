package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalPaymentIngest;
import com.goldys.platform.canonical.PaymentInput;
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
class LightspeedPaymentIngestServiceTest {

  @Mock IngestionService ingestion;
  @Mock CanonicalPaymentIngest canonical;
  @Mock LightspeedPaymentCsvParser parser;

  private LightspeedPaymentIngestService service() {
    return new LightspeedPaymentIngestService(ingestion, canonical, parser);
  }

  private static byte[] body() {
    return "{\"attachment\":{\"data\":\"csv\"}}".getBytes(StandardCharsets.UTF_8);
  }

  private static LightspeedPayment payment() {
    return new LightspeedPayment(
        LocalDate.parse("2026-09-19"),
        "SP-100 0919000001",
        "Tyro",
        "Tyro",
        "4",
        null,
        null,
        new BigDecimal("222"),
        BigDecimal.ZERO,
        new BigDecimal("222"),
        BigDecimal.ZERO,
        4,
        0,
        "Reconciled",
        "BAR_2",
        "BAR.2",
        "Example Staff",
        null,
        "96181",
        null);
  }

  @Test
  void persistsRawThenParsesAndCanonicalizes() {
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);
    when(parser.parse(any())).thenReturn(List.of(payment()));

    service().ingest(body());

    verify(ingestion)
        .ingestPush(
            eq("LIGHTSPEED"),
            eq("lightspeed-payments"),
            eq(FetchMethod.FILE_EXPORT),
            eq("application/json"),
            any(),
            eq(StandardCharsets.UTF_8.name()),
            eq("lightspeed-payments"));
    verify(canonical).record(any(PaymentInput.class));
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
