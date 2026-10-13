package com.goldys.platform.connectors.opentable.session;

import com.goldys.platform.connectors.opentable.cdp.CdpException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Scriptable {@link OpenTableBrowser} for connector and session tests. No real browser. */
public class FakeOpenTableBrowser implements OpenTableBrowser {
  public boolean authenticated;
  public boolean unavailable;
  public int navigations;
  public int signOuts;
  public int parks;
  public Consumer<ScreencastFrame> screencast;
  public final List<InputEvent> dispatched = new ArrayList<>();
  public final List<String> requests = new ArrayList<>();

  /** Queued responses consumed in order before {@link #router} is consulted. */
  public final Deque<BrowserResponse> queued = new ArrayDeque<>();

  /** Answers requests by path once {@link #queued} is empty. */
  public Function<String, BrowserResponse> router = path -> json(200, "{}");

  public static BrowserResponse json(int status, String body) {
    return new BrowserResponse(
        status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void navigateToGuestCenter() {
    guard();
    navigations++;
  }

  @Override
  public boolean isAuthenticated() {
    guard();
    return authenticated;
  }

  @Override
  public void startScreencast(Consumer<ScreencastFrame> onFrame) {
    guard();
    screencast = onFrame;
  }

  @Override
  public void stopScreencast() {
    screencast = null;
  }

  @Override
  public void dispatch(InputEvent event) {
    guard();
    dispatched.add(event);
  }

  @Override
  public BrowserResponse fetch(String method, String path, String jsonBody) {
    guard();
    requests.add(method + " " + path + (jsonBody == null ? "" : " " + jsonBody));
    BrowserResponse next = queued.poll();
    return next != null ? next : router.apply(path);
  }

  @Override
  public void signOut() {
    guard();
    signOuts++;
    authenticated = false;
  }

  @Override
  public void park() {
    guard();
    parks++;
  }

  private void guard() {
    if (unavailable) {
      throw new CdpException("sidecar down");
    }
  }
}
