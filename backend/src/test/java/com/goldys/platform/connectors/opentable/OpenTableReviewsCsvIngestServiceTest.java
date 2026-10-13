package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenTableReviewsCsvIngestServiceTest {

  private final IngestionService ingestion = mock(IngestionService.class);
  private final OpenTableReviewsCsvParser parser =
      new OpenTableReviewsCsvParser("Australia/Melbourne");
  private final OpenTableReviewsCsvIngestService service =
      new OpenTableReviewsCsvIngestService(ingestion, parser);

  @Test
  void storesRawOnlyAndValidates() throws Exception {
    byte[] csv = fixture();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    service.ingest(csv);

    ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(ingestion)
        .ingestPush(
            eq("OPENTABLE"),
            eq("opentable-reviews"),
            eq(FetchMethod.FILE_EXPORT),
            eq("text/csv"),
            bytes.capture(),
            eq(StandardCharsets.UTF_8.name()),
            eq("opentable-reviews"));
    assertThat(bytes.getValue()).isEqualTo(csv);

    // Validation only: a malformed header is rejected loudly rather than silently ingested.
    assertThatThrownBy(() -> service.ingest(badHeader()))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  private static byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/fixtures/opentable/reviews_sample.csv"));
  }

  private static byte[] badHeader() {
    return "Review ID,Source,Review Date\nOT-1,OpenTable,2026-10-12 21:56:54\n"
        .getBytes(StandardCharsets.UTF_8);
  }
}
