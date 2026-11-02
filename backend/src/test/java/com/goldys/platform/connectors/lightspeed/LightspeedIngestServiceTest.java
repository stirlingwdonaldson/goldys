package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.ingestion.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LightspeedIngestServiceTest {
  @Test
  void canonicalFailureRecordsFailedStage() throws Exception {
    var ingestion = mock(IngestionService.class);
    var canonical = mock(CanonicalDailySalesIngest.class);
    UUID raw = UUID.randomUUID();
    when(ingestion.ingestPush(
            anyString(), anyString(), any(), anyString(), any(), anyString(), anyString()))
        .thenReturn(raw);
    doThrow(new IllegalStateException("database unavailable")).when(canonical).record(any());
    byte[] body =
        new ObjectMapper()
            .writeValueAsBytes(
                Map.of(
                    "attachment",
                    Map.of(
                        "data",
                        Files.readString(
                            Path.of(
                                "src/test/resources/fixtures/lightspeed/daily-sales-closed-date.csv")))));
    var service =
        new LightspeedIngestService(ingestion, canonical, new LightspeedInsightsCsvParser());
    assertThatThrownBy(() -> service.ingest(body)).isInstanceOf(IllegalStateException.class);
    verify(ingestion).recordStage(raw, IngestionStageKind.PARSED, IngestionStageOutcome.SUCCESS);
    verify(ingestion)
        .recordStage(raw, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
    verify(ingestion, never())
        .recordStage(raw, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.SUCCESS);
  }
}
