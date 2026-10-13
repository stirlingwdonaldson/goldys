package com.goldys.platform.connectors.opentable.cdp;

import java.util.function.Consumer;

/**
 * A text-message channel to one Chrome DevTools Protocol target. Separated from {@link
 * CdpConnection} so the request/response correlation can be tested without a browser.
 */
public interface CdpTransport {
  /** Sends one complete JSON message. */
  void send(String message);

  /**
   * Registers the handlers. Called once, before the first {@link #send}. {@code onMessage} receives
   * each complete (reassembled) message; {@code onClose} fires once when the channel ends.
   */
  void listen(Consumer<String> onMessage, Runnable onClose);

  /** Closes the channel; idempotent. */
  void close();
}
