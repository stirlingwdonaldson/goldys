package com.goldys.platform.connectors.opentable.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.goldys.platform.connectors.opentable.cdp.CdpConnection;
import com.goldys.platform.connectors.opentable.cdp.CdpException;
import com.goldys.platform.connectors.opentable.cdp.CdpTargets;
import com.goldys.platform.connectors.opentable.cdp.JdkWebSocketCdpTransport;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** {@link OpenTableBrowser} driving the {@code opentable-browser} sidecar over DevTools. */
public final class CdpOpenTableBrowser implements OpenTableBrowser {
  static final String GUESTCENTER = "https://guestcenter.opentable.com";
  private static final String USER_CONTEXT =
      GUESTCENTER + "/gateway/proxies/gcrestaurant/api/userContext";
  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(15);
  // A 1000-row reservations window is several MB and GuestCenter's reporting backend is slow.
  private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(150);
  private static final Duration NAVIGATION_WAIT = Duration.ofSeconds(20);

  /** Origins whose storage holds the GuestCenter/Okta login. */
  private static final List<String> LOGIN_ORIGINS =
      List.of(GUESTCENTER, "https://restauth.opentable.com", "https://www.opentable.com");

  /** Windows virtual key codes for the allowed non-printable keys (CDP needs them for defaults). */
  private static final Map<String, Integer> KEY_CODES =
      Map.ofEntries(
          Map.entry("Enter", 13),
          Map.entry("Backspace", 8),
          Map.entry("Tab", 9),
          Map.entry("Escape", 27),
          Map.entry("Delete", 46),
          Map.entry("ArrowLeft", 37),
          Map.entry("ArrowUp", 38),
          Map.entry("ArrowRight", 39),
          Map.entry("ArrowDown", 40),
          Map.entry("Home", 36),
          Map.entry("End", 35),
          Map.entry("Shift", 16),
          Map.entry("Control", 17),
          Map.entry("Alt", 18),
          Map.entry("Meta", 91));

  /**
   * Runs one same-origin fetch in the page and returns status, content type and the body as base64
   * of the exact bytes (via Blob + FileReader, so nothing is decoded and re-encoded). Arguments are
   * appended as JSON literals, never concatenated into the script body.
   */
  private static final String FETCH_SCRIPT =
      """
      (async (method, path, body) => {
        try {
          const r = await fetch(path, {
            method,
            credentials: 'include',
            headers: body === null ? {} : { 'content-type': 'application/json' },
            body: body === null ? undefined : body,
          });
          const blob = await r.blob();
          const dataUrl = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.onerror = () => reject(reader.error);
            reader.readAsDataURL(blob);
          });
          return {
            status: r.status,
            contentType: r.headers.get('content-type') || '',
            data: dataUrl.substring(dataUrl.indexOf(',') + 1),
          };
        } catch (e) {
          return { status: 0, contentType: '', data: '' };
        }
      })""";

  private final CdpTargets targets;
  private final HttpClient http;
  private final ObjectMapper json;
  private final String host;
  private final int port;
  private final String restaurantId;
  private CdpConnection connection;
  private Runnable screencastUnsubscribe;

  public CdpOpenTableBrowser(
      CdpTargets targets,
      HttpClient http,
      ObjectMapper json,
      String host,
      int port,
      String restaurantId) {
    this.targets = targets;
    this.http = http;
    this.json = json;
    this.host = host;
    this.port = port;
    this.restaurantId = restaurantId;
  }

  @Override
  public synchronized void navigateToGuestCenter() {
    ObjectNode params = connection().params();
    params.put("url", GUESTCENTER + "/restaurant/" + restaurantId + "/");
    connection().send("Page.navigate", params, COMMAND_TIMEOUT);
  }

  @Override
  public boolean isAuthenticated() {
    // From the Okta login origin this cross-origin request fails CORS and yields 0, which is
    // correctly "not signed in"; on GuestCenter a signed-out session answers 401.
    String script =
        "(async () => { try { const r = await fetch("
            + literal(USER_CONTEXT)
            + ", { credentials: 'include' }); return r.status; } catch (e) { return 0; } })()";
    return evaluate(script, COMMAND_TIMEOUT).asInt(0) == 200;
  }

  @Override
  public synchronized void startScreencast(Consumer<ScreencastFrame> onFrame) {
    stopScreencast();
    CdpConnection cdp = connection();
    screencastUnsubscribe =
        cdp.on(
            "Page.screencastFrame",
            frame -> {
              ObjectNode ack = cdp.params();
              ack.put("sessionId", frame.path("sessionId").asInt());
              cdp.post("Page.screencastFrameAck", ack);
              JsonNode meta = frame.path("metadata");
              onFrame.accept(
                  new ScreencastFrame(
                      frame.path("data").asText(),
                      meta.path("deviceWidth").asInt(),
                      meta.path("deviceHeight").asInt()));
            });
    ObjectNode params = cdp.params();
    params.put("format", "jpeg");
    params.put("quality", 60);
    params.put("maxWidth", 1280);
    params.put("maxHeight", 800);
    params.put("everyNthFrame", 2);
    cdp.send("Page.startScreencast", params, COMMAND_TIMEOUT);
  }

  @Override
  public synchronized void stopScreencast() {
    if (screencastUnsubscribe != null) {
      screencastUnsubscribe.run();
      screencastUnsubscribe = null;
      if (connection != null && connection.isOpen()) {
        connection.post("Page.stopScreencast", null);
      }
    }
  }

  @Override
  public synchronized void dispatch(InputEvent event) {
    CdpConnection cdp = connection();
    ObjectNode params = cdp.params();
    switch (event) {
      case InputEvent.Mouse m -> {
        params.put(
            "type",
            switch (m.action()) {
              case DOWN -> "mousePressed";
              case UP -> "mouseReleased";
              case MOVE -> "mouseMoved";
            });
        params.put("x", m.x());
        params.put("y", m.y());
        params.put("button", m.button().name().toLowerCase());
        params.put("clickCount", m.clickCount());
        cdp.send("Input.dispatchMouseEvent", params, COMMAND_TIMEOUT);
      }
      case InputEvent.Wheel w -> {
        params.put("type", "mouseWheel");
        params.put("x", w.x());
        params.put("y", w.y());
        params.put("deltaX", w.deltaX());
        params.put("deltaY", w.deltaY());
        cdp.send("Input.dispatchMouseEvent", params, COMMAND_TIMEOUT);
      }
      case InputEvent.Key k -> {
        boolean down = k.action() == InputEvent.KeyAction.DOWN;
        // Enter needs "keyDown" with text so it submits forms; other editing keys are raw keys.
        boolean enter = "Enter".equals(k.key());
        params.put("type", down ? (enter ? "keyDown" : "rawKeyDown") : "keyUp");
        params.put("key", k.key());
        params.put("code", k.key());
        params.put("windowsVirtualKeyCode", KEY_CODES.get(k.key()));
        params.put("modifiers", k.modifiers());
        if (down && enter) {
          params.put("text", "\r");
        }
        cdp.send("Input.dispatchKeyEvent", params, COMMAND_TIMEOUT);
      }
      case InputEvent.Text t -> {
        params.put("text", t.value());
        cdp.send("Input.insertText", params, COMMAND_TIMEOUT);
      }
    }
  }

  @Override
  public BrowserResponse fetch(String method, String path, String jsonBody) {
    if (!("GET".equals(method) || "POST".equals(method))) {
      throw new IllegalArgumentException("Only GET and POST are allowed");
    }
    if (path == null || !path.startsWith("/gateway/") || path.contains("..")) {
      throw new IllegalArgumentException("Only GuestCenter /gateway/ paths are allowed");
    }
    ensureOnGuestCenter();
    String script =
        FETCH_SCRIPT
            + "("
            + literal(method)
            + ", "
            + literal(path)
            + ", "
            + literal(jsonBody)
            + ")";
    JsonNode result = evaluate(script, FETCH_TIMEOUT);
    return new BrowserResponse(
        result.path("status").asInt(0),
        result.path("contentType").asText(""),
        Base64.getDecoder().decode(result.path("data").asText("")));
  }

  @Override
  public synchronized void signOut() {
    CdpConnection cdp = connection();
    cdp.send("Network.clearBrowserCookies", null, COMMAND_TIMEOUT);
    for (String origin : LOGIN_ORIGINS) {
      ObjectNode params = cdp.params();
      params.put("origin", origin);
      params.put("storageTypes", "all");
      cdp.send("Storage.clearDataForOrigin", params, COMMAND_TIMEOUT);
    }
    ObjectNode blank = cdp.params();
    blank.put("url", "about:blank");
    cdp.send("Page.navigate", blank, COMMAND_TIMEOUT);
  }

  /**
   * In-page fetches use relative paths, so the page must be on GuestCenter (after login it is; a
   * human may have clicked away). Navigates home and waits for the origin if not.
   */
  private void ensureOnGuestCenter() {
    if (GUESTCENTER.equals(evaluate("location.origin", COMMAND_TIMEOUT).asText())) {
      return;
    }
    navigateToGuestCenter();
    long deadline = System.nanoTime() + NAVIGATION_WAIT.toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode state =
          evaluate("({ origin: location.origin, ready: document.readyState })", COMMAND_TIMEOUT);
      if (GUESTCENTER.equals(state.path("origin").asText())
          && "complete".equals(state.path("ready").asText())) {
        return;
      }
      sleep(Duration.ofMillis(500));
    }
    throw new CdpException("Browser did not reach GuestCenter");
  }

  private JsonNode evaluate(String expression, Duration timeout) {
    CdpConnection cdp;
    synchronized (this) {
      cdp = connection();
    }
    ObjectNode params = cdp.params();
    params.put("expression", expression);
    params.put("awaitPromise", true);
    params.put("returnByValue", true);
    JsonNode result = cdp.send("Runtime.evaluate", params, timeout);
    if (result.has("exceptionDetails")) {
      // The exception text could echo page data; report only that the script threw.
      throw new CdpException("In-page script failed");
    }
    return result.path("result").path("value");
  }

  private synchronized CdpConnection connection() {
    if (connection == null || !connection.isOpen()) {
      screencastUnsubscribe = null; // A screencast never survives a reconnect.
      URI ws = targets.pageWebSocket(host, port);
      connection = new CdpConnection(new JdkWebSocketCdpTransport(ws, http, COMMAND_TIMEOUT), json);
      connection.send("Page.enable", null, COMMAND_TIMEOUT);
    }
    return connection;
  }

  private String literal(String value) {
    try {
      return json.writeValueAsString(value); // JSON string/null literals are valid JavaScript.
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unencodable argument", e);
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CdpException("Interrupted", e);
    }
  }
}
