package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

class CtbClientRetryTest {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void retriesTransportFailureAtMostThreeTimes(boolean recover) throws Exception {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/Sale/SearchSaleItemsByDateRange",
        exchange -> {
          int call = calls.incrementAndGet();
          assertThat(
                  new String(
                      exchange.getRequestBody().readAllBytes(),
                      java.nio.charset.StandardCharsets.UTF_8))
              .contains("start=200", "searchType=-1");
          if (recover && call == 2) {
            byte[] body =
                "{\"data\":[],\"totalCount\":0}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();
    try {
      var client = new CtbClient("http://127.0.0.1:" + server.getAddress().getPort());
      if (recover) {
        assertThat(client.searchSaleItems("2026-09-05", "2026-09-05", 200, 200).totalCount())
            .isZero();
        assertThat(calls.get()).isEqualTo(2);
      } else {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.searchSaleItems("2026-09-05", "2026-09-05", 200, 200))
            .hasFieldOrPropertyWithValue("failureType", "CONNECTOR_FETCH_FAILED");
        assertThat(calls.get()).isEqualTo(3);
      }
    } finally {
      server.stop(0);
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {403, 503})
  void httpErrorsAreNotRetriedOrAcceptedAsEmptyReports(int status) throws Exception {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/Sale/SearchSaleItemsByDateRange",
        exchange -> {
          calls.incrementAndGet();
          exchange.getRequestBody().readAllBytes();
          byte[] body =
              (status == 403 ? "You don't have authority" : "{\"data\":[],\"totalCount\":0}")
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var client = new CtbClient("http://127.0.0.1:" + server.getAddress().getPort());
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> client.searchSaleItems("2026-09-05", "2026-09-05", 200, 200))
          .hasFieldOrPropertyWithValue(
              "failureType",
              status == 403 ? "CONNECTOR_PERMISSION_DENIED" : "CONNECTOR_FETCH_FAILED");
      assertThat(calls.get()).isEqualTo(1);
    } finally {
      server.stop(0);
    }
  }
}
