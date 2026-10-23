package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class CtbRecipeRawBytesTest {
  @Test
  void unsuccessfulHttpResponseRetainsExactBytesAndContentTypeBeforeValidation() throws Exception {
    byte[] actual = {(byte) 0xff, 0, 65};
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/RecipeBook/GetRecipeDetailByRecipeId",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
          exchange.sendResponseHeaders(503, actual.length);
          exchange.getResponseBody().write(actual);
          exchange.close();
        });
    server.start();
    try {
      var response =
          new CtbClient("http://127.0.0.1:" + server.getAddress().getPort())
              .getRecipeDirectStocks(651);
      assertThat(response.status()).isEqualTo(503);
      assertThat(response.contentType()).isEqualTo("application/octet-stream");
      assertThat(response.bytes()).containsExactly(actual);
      response.bytes()[0] = 0;
      assertThat(response.bytes()).containsExactly(actual);
    } finally {
      server.stop(0);
    }
  }
}
