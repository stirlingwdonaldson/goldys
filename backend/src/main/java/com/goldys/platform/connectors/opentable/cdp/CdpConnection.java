package com.goldys.platform.connectors.opentable.cdp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * One DevTools session with a page target: numbered commands matched to their responses, plus
 * method-keyed event listeners. Connected directly to the page's own WebSocket URL, so no flattened
 * {@code sessionId} routing is needed.
 */
public final class CdpConnection implements AutoCloseable {
  private final CdpTransport transport;
  private final ObjectMapper json;
  private final AtomicLong nextId = new AtomicLong(1);
  private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
  private final Map<String, List<Consumer<JsonNode>>> listeners = new ConcurrentHashMap<>();
  private final AtomicBoolean open = new AtomicBoolean(true);

  public CdpConnection(CdpTransport transport, ObjectMapper json) {
    this.transport = transport;
    this.json = json;
    transport.listen(this::onMessage, this::onClosed);
  }

  public boolean isOpen() {
    return open.get();
  }

  /** Sends {@code method} and waits for its result, failing on a protocol error or timeout. */
  public JsonNode send(String method, ObjectNode params, Duration timeout) {
    if (!open.get()) {
      throw new CdpException("CDP connection is closed (" + method + ")");
    }
    long id = nextId.getAndIncrement();
    CompletableFuture<JsonNode> response = new CompletableFuture<>();
    pending.put(id, response);
    ObjectNode message = json.createObjectNode();
    message.put("id", id);
    message.put("method", method);
    message.set("params", params == null ? json.createObjectNode() : params);
    try {
      transport.send(json.writeValueAsString(message));
      return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new CdpException("CDP " + method + " timed out", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CdpException("Interrupted waiting for CDP " + method, e);
    } catch (ExecutionException e) {
      throw e.getCause() instanceof CdpException cdp
          ? cdp
          : new CdpException("CDP " + method + " failed", e.getCause());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new CdpException("Could not encode CDP " + method, e);
    } finally {
      pending.remove(id);
    }
  }

  /** Fire-and-forget command (e.g. screencast frame acks) whose response nobody waits for. */
  public void post(String method, ObjectNode params) {
    if (!open.get()) {
      return;
    }
    ObjectNode message = json.createObjectNode();
    message.put("id", nextId.getAndIncrement());
    message.put("method", method);
    message.set("params", params == null ? json.createObjectNode() : params);
    try {
      transport.send(json.writeValueAsString(message));
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
      // A lost ack only pauses the screencast; the next start re-arms it.
    }
  }

  /** Registers a listener for protocol events named {@code method}; returns an unsubscriber. */
  public Runnable on(String method, Consumer<JsonNode> listener) {
    List<Consumer<JsonNode>> forMethod =
        listeners.computeIfAbsent(method, m -> new CopyOnWriteArrayList<>());
    forMethod.add(listener);
    return () -> forMethod.remove(listener);
  }

  public ObjectNode params() {
    return json.createObjectNode();
  }

  @Override
  public void close() {
    transport.close();
  }

  private void onMessage(String text) {
    JsonNode message;
    try {
      message = json.readTree(text);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return; // Not a protocol message; nothing to correlate it with.
    }
    JsonNode id = message.get("id");
    if (id != null && id.canConvertToLong()) {
      CompletableFuture<JsonNode> response = pending.get(id.asLong());
      if (response == null) {
        return;
      }
      JsonNode error = message.get("error");
      if (error != null) {
        // CDP error messages describe the protocol fault (e.g. "Cannot find context"), not page
        // data.
        response.completeExceptionally(
            new CdpException("CDP error: " + error.path("message").asText("unknown")));
      } else {
        response.complete(message.path("result"));
      }
      return;
    }
    JsonNode method = message.get("method");
    if (method != null) {
      for (Consumer<JsonNode> listener : listeners.getOrDefault(method.asText(), List.of())) {
        listener.accept(message.path("params"));
      }
    }
  }

  private void onClosed() {
    if (open.compareAndSet(true, false)) {
      CdpException closed = new CdpException("CDP connection closed");
      pending.values().forEach(f -> f.completeExceptionally(closed));
      pending.clear();
    }
  }
}
