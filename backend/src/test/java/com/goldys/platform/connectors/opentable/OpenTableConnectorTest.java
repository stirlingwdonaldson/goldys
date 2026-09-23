package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenTableConnectorTest {

  private final byte[] csv = fixtureCsv();
  private final OpenTableClient client = mock(OpenTableClient.class);
  private final OpenTableCsvParser parser = new OpenTableCsvParser("Australia/Sydney");
  private final CanonicalReservationIngest canonical = mock(CanonicalReservationIngest.class);

  private OpenTableConnector connector() {
    return new OpenTableConnector(client, "e@example.com", "pw", parser, canonical, 30, 14);
  }

  @Test
  void fetchesScrapePayloadAndCanonicalizesEachRow() {
    when(client.exportReservationsCsv(any(), any())).thenReturn(csv);
    AtomicReference<FetchedPayload> captured = new AtomicReference<>();
    IngestionSink sink =
        payload -> {
          captured.set(payload);
          return UUID.randomUUID();
        };

    connector().fetch(null, sink);

    assertThat(captured.get().fetchMethod()).isEqualTo(FetchMethod.SCRAPE);
    assertThat(captured.get().contentType()).isEqualTo("text/csv");
    assertThat(captured.get().fetcherIdentity()).isEqualTo("opentable-guestcenter");

    ArgumentCaptor<ReservationInput> input = ArgumentCaptor.forClass(ReservationInput.class);
    verify(canonical, org.mockito.Mockito.times(2)).record(input.capture());
    assertThat(input.getAllValues().get(0).reservationId()).isEqualTo("1000000001");
    assertThat(input.getAllValues().get(0).status()).isEqualTo("BOOKED");
    assertThat(input.getAllValues().get(0).sourceSystem()).isEqualTo("OPENTABLE");
  }

  @Test
  void authFailurePropagates() {
    doThrow(new ConnectorFetchException("CONNECTOR_AUTH_FAILED", "bad login"))
        .when(client)
        .login(any(), any());

    assertThatThrownBy(() -> connector().fetch(null, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("bad login");
  }

  @Test
  void browserFailurePropagates() {
    when(client.exportReservationsCsv(any(), any()))
        .thenThrow(new ConnectorFetchException("CONNECTOR_BROWSER_FAILED", "no chromium"));

    assertThatThrownBy(
            () -> {
              OpenTableConnector c = connector();
              c.fetch(null, p -> UUID.randomUUID());
            })
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("no chromium");
  }

  private static byte[] fixtureCsv() {
    return ("Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n"
            + "1000000001,2026-09-23,19:30,4,Booked,12,OpenTable,Smith\n"
            + "1000000002,2026-09-23,18:00,2,Seated,7,OpenTable,Jones\n")
        .getBytes(StandardCharsets.UTF_8);
  }
}
