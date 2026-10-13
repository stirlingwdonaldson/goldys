package com.goldys.platform.connectors.opentable.session;

import com.goldys.platform.connectors.opentable.cdp.CdpException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The assisted sign-in flow: show an owner the sidecar browser at the GuestCenter login, wait until
 * the session is authenticated, then start the OpenTable sync run on its own so the owner can close
 * the tab.
 *
 * <pre>
 * DISCONNECTED ─start→ LOGIN_REQUIRED ─signed in→ SYNCING ─run ends→ DONE | FAILED
 *        └──── already signed in ──────────────────┘
 * </pre>
 *
 * There is one viewer at a time (a new one replaces the old). The view ends after {@code
 * idleTimeout} without input. Sign-in polls and input relaying call the browser outside the
 * monitor, so a slow DevTools call in one never blocks the other.
 */
public class OpenTableSessionService {
  public enum State {
    DISCONNECTED,
    LOGIN_REQUIRED,
    SYNCING,
    DONE,
    FAILED
  }

  public enum Mode {
    INCREMENTAL,
    FULL
  }

  /**
   * What a viewer sees. {@code detail} is a short operator message (never page content); {@code
   * runStatus} is the ingestion run's terminal status once known.
   */
  public record Snapshot(State state, Mode mode, String runStatus, String detail, Instant at) {}

  /** A live-view subscriber, e.g. an SSE stream. Implementations must not block. */
  public interface Viewer {
    void onState(Snapshot snapshot);

    void onFrame(ScreencastFrame frame);

    /** Called when another viewer replaced this one or the session ended. */
    void onClosed();
  }

  /** Starts the sync run from a watermark and reports its terminal status name. */
  @FunctionalInterface
  public interface SyncLauncher {
    void launch(String watermark, Consumer<String> onCompleteStatusName);
  }

  private static final Logger log = LoggerFactory.getLogger(OpenTableSessionService.class);

  private final OpenTableBrowser browser;
  private final SyncLauncher launcher;
  private final ScheduledExecutorService scheduler;
  private final Clock clock;
  private final Duration pollInterval;
  private final Duration idleTimeout;

  private State state = State.DISCONNECTED;
  private Mode mode = Mode.INCREMENTAL;
  private String runStatus;
  private String detail;
  private Instant lastActivity;
  private volatile Viewer viewer;
  private boolean screencasting;
  private ScheduledFuture<?> poll;

  public OpenTableSessionService(
      OpenTableBrowser browser,
      SyncLauncher launcher,
      ScheduledExecutorService scheduler,
      Clock clock,
      Duration pollInterval,
      Duration idleTimeout) {
    this.browser = browser;
    this.launcher = launcher;
    this.scheduler = scheduler;
    this.clock = clock;
    this.pollInterval = pollInterval;
    this.idleTimeout = idleTimeout;
  }

  public synchronized Snapshot snapshot() {
    return new Snapshot(state, mode, runStatus, detail, clock.instant());
  }

  /**
   * Begins a sync. Already signed in: the run starts immediately. Otherwise the browser opens the
   * GuestCenter login and the live view starts. A sync already in flight is left alone.
   */
  public Snapshot start(Mode requested) {
    synchronized (this) {
      if (state == State.SYNCING) {
        return snapshot();
      }
      mode = requested == null ? Mode.INCREMENTAL : requested;
      runStatus = null;
      detail = null;
    }
    try {
      if (browser.isAuthenticated()) {
        beginSync();
        return snapshot();
      }
      browser.navigateToGuestCenter();
    } catch (CdpException e) {
      fail("The OpenTable browser is unavailable");
      return snapshot();
    }
    synchronized (this) {
      lastActivity = clock.instant();
      transition(State.LOGIN_REQUIRED);
      startScreencastLocked();
      schedulePollLocked();
    }
    return snapshot();
  }

  /** Attaches a viewer, replacing (and closing) any previous one. */
  public void attach(Viewer next) {
    Viewer previous;
    synchronized (this) {
      previous = viewer;
      viewer = next;
      lastActivity = clock.instant();
      if (state == State.LOGIN_REQUIRED) {
        startScreencastLocked();
      }
    }
    if (previous != null && previous != next) {
      previous.onClosed();
    }
    next.onState(snapshot());
  }

  public synchronized void detach(Viewer leaving) {
    if (viewer == leaving) {
      viewer = null;
      stopScreencastLocked();
    }
  }

  /**
   * Relays one input event while the owner is signing in. Returns false (and drops the event) in
   * any other state: keystrokes still in flight when sign-in completes are expected, not errors.
   */
  public boolean input(InputEvent event) {
    synchronized (this) {
      if (state != State.LOGIN_REQUIRED) {
        return false;
      }
      lastActivity = clock.instant();
    }
    try {
      browser.dispatch(event);
      return true;
    } catch (CdpException e) {
      fail("The OpenTable browser is unavailable");
      return false;
    }
  }

  /** Stops viewing; {@code signOut} also clears the OpenTable login from the browser profile. */
  public void end(boolean signOut) {
    Viewer closing;
    boolean abandonedLogin;
    synchronized (this) {
      cancelPollLocked();
      stopScreencastLocked();
      abandonedLogin = state == State.LOGIN_REQUIRED;
      if (abandonedLogin) {
        transition(State.DISCONNECTED);
      }
      closing = viewer;
      viewer = null;
    }
    if (signOut) {
      try {
        browser.signOut();
      } catch (CdpException e) {
        log.warn("Could not clear the OpenTable browser session: {}", e.getMessage());
      }
    } else if (abandonedLogin) {
      parkQuietly(); // Don't leave a half-filled login form open on the VM.
    }
    if (closing != null) {
      closing.onClosed();
    }
  }

  // ---- internals ------------------------------------------------------------------------------

  /** Runs on the scheduler while waiting for sign-in. */
  void pollOnce() {
    boolean idledOut = false;
    synchronized (this) {
      if (state != State.LOGIN_REQUIRED) {
        cancelPollLocked();
        return;
      }
      if (Duration.between(lastActivity, clock.instant()).compareTo(idleTimeout) > 0) {
        cancelPollLocked();
        stopScreencastLocked();
        detail = "Sign-in view closed after inactivity";
        transition(State.DISCONNECTED);
        idledOut = true;
      } else {
        // The first screencast can race the login navigation the sidecar is still committing, which
        // Chromium rejects with "Not attached to an active page". Re-arm it while we wait so the
        // view recovers without the owner reloading. Only while still waiting for sign-in: once the
        // view has idled out and disconnected above, the screencast must stay stopped.
        startScreencastLocked();
      }
    }
    if (idledOut) {
      parkQuietly();
      return;
    }
    boolean authenticated;
    try {
      authenticated = browser.isAuthenticated();
    } catch (CdpException e) {
      fail("The OpenTable browser is unavailable");
      return;
    }
    if (authenticated) {
      beginSync();
    }
  }

  private void beginSync() {
    String watermark;
    synchronized (this) {
      if (state == State.SYNCING) {
        return;
      }
      cancelPollLocked();
      stopScreencastLocked();
      detail = "Signed in. Syncing; you can close this page";
      transition(State.SYNCING);
      watermark = mode == Mode.FULL ? "FULL" : null;
    }
    try {
      launcher.launch(watermark, this::onRunComplete);
    } catch (RuntimeException e) {
      log.warn("Could not start the OpenTable sync run", e);
      fail("The sync run could not be started");
    }
  }

  private void onRunComplete(String status) {
    // Leave GuestCenter so its scripts stop refreshing the login: the session then lapses on
    // OpenTable's own timeouts instead of staying signed in on the VM indefinitely.
    parkQuietly();
    synchronized (this) {
      runStatus = status;
      if (status == null) {
        detail = "A sync was already running";
        transition(State.DONE);
      } else if ("SUCCESS".equals(status) || "NO_NEW_DATA".equals(status)) {
        detail = "Reservations, reviews and menu items are up to date.";
        transition(State.DONE);
      } else {
        detail = "Sync ended " + status + "; see Data health for details";
        transition(State.FAILED);
      }
    }
  }

  private void parkQuietly() {
    try {
      browser.park();
    } catch (CdpException e) {
      log.debug("Could not park the OpenTable browser: {}", e.getMessage());
    }
  }

  private void fail(String message) {
    synchronized (this) {
      cancelPollLocked();
      stopScreencastLocked();
      detail = message;
      transition(State.FAILED);
    }
  }

  /** Callers hold the monitor; viewers are notified with a snapshot of the new state. */
  private void transition(State next) {
    state = next;
    Viewer v = viewer;
    if (v != null) {
      Snapshot s = new Snapshot(state, mode, runStatus, detail, clock.instant());
      scheduler.execute(() -> v.onState(s));
    }
  }

  private void startScreencastLocked() {
    if (screencasting || viewer == null) {
      return;
    }
    try {
      browser.startScreencast(
          frame -> {
            Viewer v = viewer;
            if (v != null) {
              v.onFrame(frame);
            }
          });
      screencasting = true;
      detail = null; // A retry succeeded: drop the earlier "Live view unavailable".
    } catch (CdpException e) {
      detail = "Live view unavailable";
      log.warn("Could not start the OpenTable live view: {}", e.getMessage());
    }
  }

  private void stopScreencastLocked() {
    if (screencasting) {
      screencasting = false;
      try {
        browser.stopScreencast();
      } catch (CdpException e) {
        log.debug("Stopping the OpenTable live view failed: {}", e.getMessage());
      }
    }
  }

  private void schedulePollLocked() {
    cancelPollLocked();
    long millis = pollInterval.toMillis();
    poll = scheduler.scheduleWithFixedDelay(this::pollOnce, millis, millis, TimeUnit.MILLISECONDS);
  }

  private void cancelPollLocked() {
    if (poll != null) {
      poll.cancel(false);
      poll = null;
    }
  }
}
