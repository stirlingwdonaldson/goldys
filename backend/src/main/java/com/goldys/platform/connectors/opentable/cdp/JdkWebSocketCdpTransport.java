package com.goldys.platform.connectors.opentable.cdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** {@link CdpTransport} over the JDK's built-in WebSocket client. */
public final class JdkWebSocketCdpTransport implements CdpTransport {
  private final URI uri;
  private final HttpClient http;
  private final Duration connectTimeout;
  private final AtomicBoolean closed = new AtomicBoolean();
  private volatile WebSocket socket;
  private volatile Runnable onClose = () -> {};

  public JdkWebSocketCdpTransport(URI uri, HttpClient http, Duration connectTimeout) {
    this.uri = uri;
    this.http = http;
    this.connectTimeout = connectTimeout;
  }

  @Override
  public void listen(Consumer<String> onMessage, Runnable onClose) {
    this.onClose = onClose;
    socket =
        http.newWebSocketBuilder()
            .connectTimeout(connectTimeout)
            .buildAsync(uri, new Listener(onMessage))
            .join();
  }

  @Override
  public void send(String message) {
    WebSocket ws = socket;
    if (ws == null || closed.get()) {
      throw new CdpException("CDP channel is not open");
    }
    // The JDK WebSocket allows one outstanding send at a time; serialize callers.
    synchronized (this) {
      ws.sendText(message, true).join();
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      WebSocket ws = socket;
      if (ws != null) {
        ws.abort();
      }
      onClose.run();
    }
  }

  /**
   * Reassembles fragmented text frames: Chrome splits large messages (a 1000-row reservations
   * response is several MB) across many frames, and only the last carries {@code last == true}.
   */
  private final class Listener implements WebSocket.Listener {
    private final Consumer<String> onMessage;
    private final StringBuilder partial = new StringBuilder();

    private Listener(Consumer<String> onMessage) {
      this.onMessage = onMessage;
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      partial.append(data);
      if (last) {
        String message = partial.toString();
        partial.setLength(0);
        onMessage.accept(message);
      }
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
      close();
      return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
      close();
    }
  }
}
