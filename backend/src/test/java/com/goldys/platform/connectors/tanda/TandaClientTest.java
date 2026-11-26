package com.goldys.platform.connectors.tanda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class TandaClientTest {
  private final HttpClient http = mock(HttpClient.class);

  @Test
  void sendsOnlyGetToFixedHttpsOriginAndPreservesBytes() throws Exception {
    @SuppressWarnings("unchecked")
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    byte[] bytes = "[ { \"id\": 1 } ]\n".getBytes();
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(bytes);
    when(http.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(response);
    assertThat(new TandaClient(http).get("locations", "test-token")).isEqualTo(bytes);
    var request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(request.capture(), any());
    assertThat(request.getValue().uri().toString())
        .isEqualTo("https://my.tanda.co/api/v2/locations");
    assertThat(request.getValue().method()).isEqualTo("GET");
    assertThat(request.getValue().headers().firstValue("Authorization"))
        .contains("Bearer test-token");
  }

  @ParameterizedTest
  @CsvSource({
    "401,TANDA_TOKEN_REJECTED",
    "403,TANDA_ACCESS_DENIED",
    "429,TANDA_RATE_LIMITED",
    "302,TANDA_HTTP_ERROR"
  })
  void errorBodiesNeverBecomeOperatorMessages(int status, String type) throws Exception {
    @SuppressWarnings("unchecked")
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn("sensitive-source-error".getBytes());
    when(http.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(response);
    assertThatThrownBy(() -> new TandaClient(http).get("locations", "test-token"))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> {
              assertThat(e.failureType()).isEqualTo(type);
              assertThat(e.getMessage()).doesNotContain("sensitive-source-error", "test-token");
              assertThat(e.getCause()).isNull();
            });
  }

  @Test
  void networkExceptionCauseIsNotRetained() throws Exception {
    when(http.send(any(HttpRequest.class), any())).thenThrow(new IOException("sensitive-header"));
    assertThatThrownBy(() -> new TandaClient(http).get("locations", "test-token"))
        .isInstanceOfSatisfying(
            ConnectorFetchException.class,
            e -> {
              assertThat(e.getMessage()).doesNotContain("sensitive-header", "test-token");
              assertThat(e.getCause()).isNull();
            });
  }
}
