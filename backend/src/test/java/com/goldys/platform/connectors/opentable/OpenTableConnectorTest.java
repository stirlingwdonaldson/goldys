package com.goldys.platform.connectors.opentable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import com.goldys.platform.ingestion.port.IngestionSink;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class OpenTableConnectorTest {

  private final byte[] csv = fixtureCsv();
  private final OpenTableClient client = mock(OpenTableClient.class);
  private final OpenTableCsvParser parser = new OpenTableCsvParser("Australia/Sydney");
  private final CanonicalReservationIngest canonical = mock(CanonicalReservationIngest.class);

  private OpenTableConnector connector() {
    return new OpenTableConnector(client, parser, canonical, 30, 14);
  }

  @Test
  void authenticatesBeforeExportingAndCanonicalizesEachRow() {
    UUID rawId = UUID.randomUUID();
    when(client.exportReservationsCsv(any(), any())).thenReturn(csv);
    AtomicReference<FetchedPayload> captured = new AtomicReference<>();
    IngestionSink sink =
        payload -> {
          captured.set(payload);
          return rawId;
        };

    connector().fetch(null, sink);

    InOrder order = inOrder(client);
    order.verify(client).authenticate();
    order.verify(client).exportReservationsCsv(any(), any());

    assertThat(captured.get().fetchMethod()).isEqualTo(FetchMethod.SCRAPE);
    assertThat(captured.get().contentType()).isEqualTo("text/csv");
    assertThat(captured.get().fetcherIdentity()).isEqualTo("opentable-guestcenter");

    ArgumentCaptor<ReservationInput> input = ArgumentCaptor.forClass(ReservationInput.class);
    verify(canonical, times(2)).record(input.capture());
    ReservationInput first = input.getAllValues().get(0);
    assertThat(first.sourceSystem()).isEqualTo("OPENTABLE");
    assertThat(first.reservationId()).isEqualTo("1000000001");
    assertThat(first.reservationAt()).isEqualTo(Instant.parse("2026-09-23T09:30:00Z"));
    assertThat(first.partySize()).isEqualTo(4);
    assertThat(first.status()).isEqualTo("BOOKED");
    assertThat(first.tableName()).isEqualTo("12");
    assertThat(first.sourceChannel()).isEqualTo("OpenTable");
    assertThat(first.partyName()).isEqualTo("Smith");
    assertThat(first.rawRecordId()).isEqualTo(rawId);
  }

  @Test
  void authFailurePropagates() {
    doThrow(new ConnectorFetchException("CONNECTOR_AUTH_FAILED", "bad login"))
        .when(client)
        .authenticate();

    assertThatThrownBy(() -> connector().fetch(null, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("bad login");
  }

  @Test
  void browserFailurePropagates() {
    doThrow(new ConnectorFetchException("CONNECTOR_BROWSER_FAILED", "no fortress"))
        .when(client)
        .authenticate();

    assertThatThrownBy(() -> connector().fetch(null, p -> UUID.randomUUID()))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("no fortress");
  }

  private static byte[] fixtureCsv() {
    return ("Reservation ID,Date,Time,Party Size,Status,Table,Source,Guest Name\n"
            + "1000000001,2026-09-23,19:30,4,Booked,12,OpenTable,Smith\n"
            + "1000000002,2026-09-23,18:00,2,Seated,7,OpenTable,Jones\n")
        .getBytes(StandardCharsets.UTF_8);
  }
}
