package com.goldys.platform.connectors.opentable.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.connectors.opentable.session.OpenTableSessionService.Mode;
import com.goldys.platform.connectors.opentable.session.OpenTableSessionService.Snapshot;
import com.goldys.platform.connectors.opentable.session.OpenTableSessionService.State;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenTableSessionServiceTest {
  private final FakeOpenTableBrowser browser = new FakeOpenTableBrowser();
  private final MutableClock clock = new MutableClock();
  private final List<String> launchedWatermarks = new ArrayList<>();
  private Consumer<String> runCompletion;
  private ScheduledExecutorService scheduler;
  private OpenTableSessionService session;

  /** A clock the test advances by hand. */
  private static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-13T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  /** Records what a live-view subscriber receives. */
  private static final class RecordingViewer implements OpenTableSessionService.Viewer {
    final List<Snapshot> states = new java.util.concurrent.CopyOnWriteArrayList<>();
    final List<ScreencastFrame> frames = new ArrayList<>();
    boolean closed;

    @Override
    public void onState(Snapshot snapshot) {
      states.add(snapshot);
    }

    @Override
    public void onFrame(ScreencastFrame frame) {
      frames.add(frame);
    }

    @Override
    public void onClosed() {
      closed = true;
    }
  }

  @BeforeEach
  void setUp() {
    scheduler = Executors.newSingleThreadScheduledExecutor();
    session =
        new OpenTableSessionService(
            browser,
            (watermark, onComplete) -> {
              launchedWatermarks.add(watermark);
              runCompletion = onComplete;
            },
            scheduler,
            clock,
            Duration.ofHours(1), // The test drives pollOnce() itself.
            Duration.ofMinutes(10));
  }

  @AfterEach
  void tearDown() {
    scheduler.shutdownNow();
  }

  @Test
  void signedOutStartOpensLoginAndStreamsToTheViewer() {
    RecordingViewer viewer = new RecordingViewer();
    session.attach(viewer);

    Snapshot started = session.start(Mode.INCREMENTAL);

    assertThat(started.state()).isEqualTo(State.LOGIN_REQUIRED);
    assertThat(browser.navigations).isEqualTo(1);
    assertThat(browser.screencast).isNotNull();
    browser.screencast.accept(new ScreencastFrame("AAAA", 1280, 800));
    assertThat(viewer.frames).hasSize(1);
  }

  @Test
  void signingInStopsTheViewAndStartsTheSyncAutomatically() {
    session.attach(new RecordingViewer());
    session.start(Mode.FULL);

    browser.authenticated = true;
    session.pollOnce();

    assertThat(session.snapshot().state()).isEqualTo(State.SYNCING);
    assertThat(browser.screencast).isNull();
    assertThat(launchedWatermarks).containsExactly("FULL");

    runCompletion.accept("SUCCESS");
    assertThat(session.snapshot().state()).isEqualTo(State.DONE);
    // Left on about:blank so GuestCenter's scripts stop keeping the login alive.
    assertThat(browser.parks).isEqualTo(1);
  }

  @Test
  void parksAfterAFailedRunAndAfterAnAbandonedSignIn() {
    browser.authenticated = true;
    session.start(Mode.INCREMENTAL);
    runCompletion.accept("FAILED");
    assertThat(browser.parks).isEqualTo(1);

    browser.authenticated = false;
    session.start(Mode.INCREMENTAL);
    session.end(false);
    assertThat(browser.parks).isEqualTo(2);
  }

  @Test
  void alreadySignedInSyncsWithoutShowingTheLogin() {
    browser.authenticated = true;

    Snapshot started = session.start(Mode.INCREMENTAL);

    assertThat(started.state()).isEqualTo(State.SYNCING);
    assertThat(browser.navigations).isZero();
    assertThat(launchedWatermarks).containsExactly((String) null);
  }

  @Test
  void partialRunEndsFailedWithAPointerToDataHealth() {
    browser.authenticated = true;
    session.start(Mode.INCREMENTAL);

    runCompletion.accept("PARTIAL");

    Snapshot s = session.snapshot();
    assertThat(s.state()).isEqualTo(State.FAILED);
    assertThat(s.runStatus()).isEqualTo("PARTIAL");
    assertThat(s.detail()).contains("Data health");
  }

  @Test
  void inputIsRelayedOnlyWhileSigningIn() {
    InputEvent.Text text = new InputEvent.Text("owner@example.com");
    assertThat(session.input(text)).isFalse();

    session.start(Mode.INCREMENTAL);
    assertThat(session.input(text)).isTrue();
    assertThat(browser.dispatched).containsExactly(text);
  }

  @Test
  void idleViewEndsAfterTimeout() {
    session.attach(new RecordingViewer());
    session.start(Mode.INCREMENTAL);

    clock.now = clock.now.plus(Duration.ofMinutes(11));
    session.pollOnce();

    assertThat(session.snapshot().state()).isEqualTo(State.DISCONNECTED);
    assertThat(browser.screencast).isNull();
    assertThat(browser.parks).isEqualTo(1);
  }

  @Test
  void aNewViewerReplacesAndClosesTheOldOne() {
    RecordingViewer first = new RecordingViewer();
    RecordingViewer second = new RecordingViewer();
    session.attach(first);

    session.attach(second);

    assertThat(first.closed).isTrue();
    assertThat(second.closed).isFalse();
  }

  @Test
  void sidecarDownFailsWithoutThrowing() {
    browser.unavailable = true;

    Snapshot s = session.start(Mode.INCREMENTAL);

    assertThat(s.state()).isEqualTo(State.FAILED);
    assertThat(s.detail()).contains("unavailable");
  }

  @Test
  void endWithSignOutClearsTheProfile() {
    session.start(Mode.INCREMENTAL);

    session.end(true);

    assertThat(browser.signOuts).isEqualTo(1);
    assertThat(session.snapshot().state()).isEqualTo(State.DISCONNECTED);
  }

  @Test
  void typedTextIsRedactedInToString() {
    assertThat(new InputEvent.Text("hunter2").toString()).doesNotContain("hunter2");
  }
}
