package com.goldys.platform.connectors.tanda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.config.VenueClock;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TandaConnectorTest {
  @TempDir Path directory;
  private final TandaClient client = mock(TandaClient.class);
  private final VenueClock clock =
      VenueClock.fixed(ZoneId.of("Australia/Melbourne"), Instant.parse("2026-11-25T14:00:00Z"));

  private TandaConnector connector() throws Exception {
    Path token = directory.resolve("token");
    Files.writeString(token, "test-token-not-a-real-credential");
    Files.setPosixFilePermissions(token, PosixFilePermissions.fromString("rw-------"));
    return new TandaConnector(client, token.toString(), 1, clock);
  }

  @Test
  void retainsBytesBeforeRejectingChangedSchema() throws Exception {
    byte[] bytes = "{\"unexpected\":true}".getBytes();
    when(client.get(anyString(), anyString())).thenReturn(bytes);
    var captured = new ArrayList<FetchedPayload>();
    assertThatThrownBy(
            () ->
                connector()
                    .fetch(
                        null,
                        p -> {
                          captured.add(p);
                          return UUID.randomUUID();
                        }))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("TANDA_SCHEMA_MISMATCH"));
    assertThat(captured).singleElement().satisfies(p -> assertThat(p.bytes()).isEqualTo(bytes));
  }

  @Test
  void capturesAllDatasetsWithoutClaimingReportReadiness() throws Exception {
    when(client.get(anyString(), anyString())).thenReturn("[]".getBytes());
    var captured = new ArrayList<FetchedPayload>();
    assertThatThrownBy(
            () ->
                connector()
                    .fetch(
                        null,
                        p -> {
                          captured.add(p);
                          return UUID.randomUUID();
                        }))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("TANDA_MAPPING_PENDING"));
    assertThat(captured)
        .extracting(FetchedPayload::fetcherIdentity)
        .containsExactly(
            "tanda-locations",
            "tanda-departments",
            "tanda-users",
            "tanda-schedules",
            "tanda-leave",
            "tanda-timesheets");
    verify(client).get(contains("timesheets/on/2026-11-26?"), anyString());
    assertThat(captured.getLast().sourceRecordRef()).contains("skip_status_update=true");
  }

  @Test
  void fullPagesContinueAndLaterFailurePreservesEarlierPages() throws Exception {
    String full = "[" + String.join(",", java.util.Collections.nCopies(100, "{}")) + "]";
    when(client.get(anyString(), anyString())).thenReturn("[]".getBytes());
    when(client.get(startsWith("departments?page=1"), anyString())).thenReturn(full.getBytes());
    when(client.get(startsWith("departments?page=2"), anyString()))
        .thenThrow(new ConnectorFetchException("TANDA_RATE_LIMITED", "Retry later"));
    var captured = new ArrayList<FetchedPayload>();
    assertThatThrownBy(
            () ->
                connector()
                    .fetch(
                        null,
                        p -> {
                          captured.add(p);
                          return UUID.randomUUID();
                        }))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("TANDA_RATE_LIMITED"));
    assertThat(captured).hasSize(2);
  }

  @Test
  void missingTokenFailsBeforeNetworkAccess() {
    assertThatThrownBy(
            () -> new TandaConnector(client, "", 1, clock).fetch(null, p -> UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> assertThat(e.failureType()).isEqualTo("TANDA_NOT_CONFIGURED"));
    verifyNoInteractions(client);
  }

  @Test
  void rejectsReadableByOthersAndSymlinkTokens() throws Exception {
    var connector = connector();
    Files.setPosixFilePermissions(
        directory.resolve("token"), PosixFilePermissions.fromString("rw-r--r--"));
    assertThatThrownBy(() -> connector.fetch(null, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class);
    Files.createSymbolicLink(directory.resolve("link"), directory.resolve("token"));
    assertThatThrownBy(
            () ->
                new TandaConnector(client, directory.resolve("link").toString(), 1, clock)
                    .fetch(null, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class);
    verifyNoInteractions(client);
  }
}
