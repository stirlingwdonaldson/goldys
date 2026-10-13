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

class OpenTableMenuItemsCsvIngestServiceTest {

  private final IngestionService ingestion = mock(IngestionService.class);
  private final OpenTableMenuItemsCsvParser parser = new OpenTableMenuItemsCsvParser();
  private final OpenTableMenuItemsCsvIngestService service =
      new OpenTableMenuItemsCsvIngestService(ingestion, parser);

  @Test
  void storesRawOnlyWithFetcherIdentity() throws Exception {
    byte[] csv = fixture();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());

    service.ingest(csv);

    ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(ingestion)
        .ingestPush(
            eq("OPENTABLE"),
            eq("opentable-menu-items"),
            eq(FetchMethod.FILE_EXPORT),
            eq("text/csv"),
            bytes.capture(),
            eq(StandardCharsets.UTF_8.name()),
            eq("opentable-menu-items"));
    assertThat(bytes.getValue()).isEqualTo(csv);

    // Validation only: a malformed header is rejected loudly rather than silently ingested.
    assertThatThrownBy(() -> service.ingest(badHeader()))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("CONNECTOR_SCHEMA_MISMATCH"));
  }

  private static byte[] fixture() throws Exception {
    return Files.readAllBytes(
        Path.of("src/test/resources/fixtures/opentable/menu_items_sample.csv"));
  }

  private static byte[] badHeader() {
    return "Items,Price,Item ID\nTarot Grenache,A$72.00,ffedfaa2-a648-4430-9922-6bb934bc22e0\n"
        .getBytes(StandardCharsets.UTF_8);
  }
}
