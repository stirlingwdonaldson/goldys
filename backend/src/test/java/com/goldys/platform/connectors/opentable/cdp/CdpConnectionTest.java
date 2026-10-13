package com.goldys.platform.connectors.opentable.cdp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CdpConnectionTest {
  private final ObjectMapper json = new ObjectMapper();

  /** Records sent messages and lets the test deliver replies and events. */
  private static final class StubTransport implements CdpTransport {
    final List<String> sent = new ArrayList<>();
    Consumer<String> onMessage;
    Runnable onClose;
    Consumer<String> autoReply = m -> {};

    @Override
    public void send(String message) {
      sent.add(message);
      autoReply.accept(message);
    }

    @Override
    public void listen(Consumer<String> onMessage, Runnable onClose) {
      this.onMessage = onMessage;
      this.onClose = onClose;
    }

    @Override
    public void close() {
      onClose.run();
    }
  }

  @Test
  void correlatesResponsesById() throws Exception {
    StubTransport transport = new StubTransport();
    CdpConnection cdp = new CdpConnection(transport, json);
    transport.autoReply =
        m -> {
          long id = readId(m);
          transport.onMessage.accept("{\"id\":" + id + ",\"result\":{\"value\":" + id + "}}");
        };

    JsonNode first = cdp.send("Runtime.evaluate", null, Duration.ofSeconds(1));
    JsonNode second = cdp.send("Runtime.evaluate", null, Duration.ofSeconds(1));

    assertThat(first.path("value").asLong()).isEqualTo(1);
    assertThat(second.path("value").asLong()).isEqualTo(2);
    assertThat(json.readTree(transport.sent.get(0)).path("method").asText())
        .isEqualTo("Runtime.evaluate");
  }

  @Test
  void protocolErrorBecomesCdpException() {
    StubTransport transport = new StubTransport();
    CdpConnection cdp = new CdpConnection(transport, json);
    transport.autoReply =
        m ->
            transport.onMessage.accept(
                "{\"id\":" + readId(m) + ",\"error\":{\"message\":\"Cannot find context\"}}");

    assertThatThrownBy(() -> cdp.send("Runtime.evaluate", null, Duration.ofSeconds(1)))
        .isInstanceOf(CdpException.class)
        .hasMessageContaining("Cannot find context");
  }

  @Test
  void timesOutWhenNoResponse() {
    CdpConnection cdp = new CdpConnection(new StubTransport(), json);

    assertThatThrownBy(() -> cdp.send("Page.navigate", null, Duration.ofMillis(50)))
        .isInstanceOf(CdpException.class)
        .hasMessageContaining("timed out");
  }

  @Test
  void dispatchesEventsToListenersUntilUnsubscribed() {
    StubTransport transport = new StubTransport();
    CdpConnection cdp = new CdpConnection(transport, json);
    List<Integer> seen = new ArrayList<>();
    Runnable unsubscribe =
        cdp.on("Page.screencastFrame", p -> seen.add(p.path("sessionId").asInt()));

    transport.onMessage.accept(
        "{\"method\":\"Page.screencastFrame\",\"params\":{\"sessionId\":7}}");
    unsubscribe.run();
    transport.onMessage.accept(
        "{\"method\":\"Page.screencastFrame\",\"params\":{\"sessionId\":8}}");

    assertThat(seen).containsExactly(7);
  }

  @Test
  void closingFailsPendingCommandsAndRejectsNewOnes() throws Exception {
    StubTransport transport = new StubTransport();
    CdpConnection cdp = new CdpConnection(transport, json);
    CompletableFuture<Throwable> failure =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                cdp.send("Page.navigate", null, Duration.ofSeconds(5));
                return null;
              } catch (CdpException e) {
                return e;
              }
            });
    while (transport.sent.isEmpty()) {
      Thread.onSpinWait();
    }

    transport.close();

    assertThat(failure.get()).isInstanceOf(CdpException.class);
    assertThat(cdp.isOpen()).isFalse();
    assertThatThrownBy(() -> cdp.send("Page.navigate", null, Duration.ofSeconds(1)))
        .isInstanceOf(CdpException.class);
  }

  private long readId(String message) {
    try {
      return json.readTree(message).path("id").asLong();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
