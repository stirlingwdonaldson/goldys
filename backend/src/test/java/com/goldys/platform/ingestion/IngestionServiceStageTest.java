package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IngestionServiceStageTest {

  @Test
  void recordStageResolvesRunSourceAndDatasetFromTheRawRecord() {
    UUID runId = UUID.randomUUID();
    UUID rawId = UUID.randomUUID();
    RawRecord record =
        RawRecord.create(
            runId,
            "CTB",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            "x".getBytes(StandardCharsets.UTF_8),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            StandardCharsets.UTF_8.name(),
            "ctb-invoices",
            Instant.EPOCH);

    RawRecordRepository rawRecords = mock(RawRecordRepository.class);
    when(rawRecords.findById(rawId)).thenReturn(Optional.of(record));
    IngestionStageRepository stages = mock(IngestionStageRepository.class);

    IngestionService service =
        new IngestionService(
            mock(IngestionRunService.class),
            mock(RawPayloadService.class),
            mock(IngestionRunRepository.class),
            mock(IngestionFailureRepository.class),
            rawRecords,
            stages,
            mock(ConnectorRunner.class),
            List.of());

    service.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.SUCCESS);

    ArgumentCaptor<IngestionStage> captor = ArgumentCaptor.forClass(IngestionStage.class);
    verify(stages).save(captor.capture());
    assertThat(captor.getValue().ingestionRunId()).isEqualTo(runId);
    assertThat(captor.getValue().sourceSystem()).isEqualTo("CTB");
    assertThat(captor.getValue().dataset()).isEqualTo("ctb-invoices");
    assertThat(captor.getValue().stage()).isEqualTo(IngestionStageKind.PARSED);
    assertThat(captor.getValue().outcome()).isEqualTo(IngestionStageOutcome.SUCCESS);
  }
}
