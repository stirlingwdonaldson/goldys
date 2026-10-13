package com.goldys.platform.connectors.opentable.session;

import java.util.function.Consumer;

/**
 * The browser on the VM that a human signs in to GuestCenter with, and that the connector then uses
 * to call GuestCenter's own JSON endpoints with the resulting session.
 *
 * <p>Nothing here types credentials or interacts with a challenge on its own: {@link #dispatch}
 * only relays events a signed-in owner produced in the live view.
 */
public interface OpenTableBrowser {
  /** Opens GuestCenter, which redirects to its login flow when signed out. */
  void navigateToGuestCenter();

  /** True when GuestCenter's user-context endpoint answers 200 for the current session. */
  boolean isAuthenticated();

  /** Starts streaming page frames to {@code onFrame} until {@link #stopScreencast()}. */
  void startScreencast(Consumer<ScreencastFrame> onFrame);

  void stopScreencast();

  /** Relays one validated input event from the live view. */
  void dispatch(InputEvent event);

  /**
   * Performs a same-origin {@code fetch} inside the GuestCenter page so the request carries the
   * signed-in session, returning the exact response bytes. Only {@code /gateway/} paths and GET /
   * POST are allowed.
   */
  BrowserResponse fetch(String method, String path, String jsonBody);

  /** Clears GuestCenter and OpenTable login state from the browser profile. */
  void signOut();

  /**
   * Leaves GuestCenter for {@code about:blank}. While a GuestCenter page is open its own scripts
   * keep refreshing the login (every 30 minutes), which would hold the session open indefinitely;
   * parked, nothing refreshes it, so it lapses on OpenTable's own timeouts.
   */
  void park();
}
