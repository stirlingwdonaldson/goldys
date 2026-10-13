package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.connectors.opentable.session.InputEvent;
import com.goldys.platform.connectors.opentable.session.OpenTableSessionService;
import com.goldys.platform.connectors.opentable.session.OpenTableSessionService.Mode;
import com.goldys.platform.connectors.opentable.session.OpenTableSessionService.Snapshot;
import com.goldys.platform.connectors.opentable.session.OpenTableSessionService.Viewer;
import com.goldys.platform.connectors.opentable.session.ScreencastFrame;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Authorizes and translates the OpenTable assisted sign-in for the delivery layer. The live view is
 * a window into the venue's OpenTable account, so it has its own resource rather than riding on
 * {@code connectors}: READ to watch, WRITE to start, type into, or end it.
 */
@Service
public class OpenTableSessionApplicationService {
  private static final ResourceKey RESOURCE = new ResourceKey("connectors.opentable.session");

  /** Session state as the frontend sees it. */
  public record SessionState(
      String state, String mode, String runStatus, String detail, Instant at) {}

  /** A live-view frame as the frontend sees it. */
  public record Frame(String jpegBase64, int width, int height) {}

  /** Receives session updates; implemented by the SSE stream. */
  public interface Listener {
    void onState(SessionState state);

    void onFrame(Frame frame);

    void onClosed();
  }

  /**
   * One input event from the live view. {@code type} picks the variant: {@code mouse} uses {@code
   * action}/{@code x}/{@code y}/{@code button}/{@code clickCount}; {@code wheel} uses {@code
   * x}/{@code y}/{@code deltaX}/{@code deltaY}; {@code key} uses {@code action}/{@code key}/{@code
   * modifiers}; {@code text} uses {@code text}.
   */
  public record Input(
      String type,
      String action,
      Double x,
      Double y,
      String button,
      Integer clickCount,
      Double deltaX,
      Double deltaY,
      String key,
      Integer modifiers,
      String text) {
    @Override
    public String toString() {
      return "Input[" + type + "]"; // Never render typed text (it may be a password).
    }
  }

  private final ObjectProvider<OpenTableSessionService> sessions;
  private final PermissionService permissions;

  public OpenTableSessionApplicationService(
      ObjectProvider<OpenTableSessionService> sessions, PermissionService permissions) {
    this.sessions = sessions;
    this.permissions = permissions;
  }

  /** False when the assisted connector is disabled ({@code opentable.api-enabled}). */
  public boolean isAvailable() {
    return sessions.getIfAvailable() != null;
  }

  public SessionState state(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return toState(session().snapshot());
  }

  public SessionState start(UserRole role, String mode) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    Mode parsed =
        mode == null ? Mode.INCREMENTAL : Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
    return toState(session().start(parsed));
  }

  /** Returns false if the event was dropped because the session isn't waiting for sign-in. */
  public boolean input(UserRole role, Input input) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    return session().input(toEvent(input));
  }

  public void end(UserRole role, boolean signOut) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    session().end(signOut);
  }

  /** Subscribes {@code listener}; the returned handle detaches it. */
  public Runnable watch(UserRole role, Listener listener) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    OpenTableSessionService session = session();
    Viewer viewer =
        new Viewer() {
          @Override
          public void onState(Snapshot snapshot) {
            listener.onState(toState(snapshot));
          }

          @Override
          public void onFrame(ScreencastFrame frame) {
            listener.onFrame(new Frame(frame.jpegBase64(), frame.width(), frame.height()));
          }

          @Override
          public void onClosed() {
            listener.onClosed();
          }
        };
    session.attach(viewer);
    return () -> session.detach(viewer);
  }

  private OpenTableSessionService session() {
    OpenTableSessionService session = sessions.getIfAvailable();
    if (session == null) {
      throw new IllegalStateException("OpenTable assisted sync is not enabled");
    }
    return session;
  }

  private static SessionState toState(Snapshot s) {
    return new SessionState(s.state().name(), s.mode().name(), s.runStatus(), s.detail(), s.at());
  }

  /**
   * Throws IllegalArgumentException (HTTP 400) for anything malformed or outside the allow-list.
   */
  static InputEvent toEvent(Input in) {
    if (in == null || in.type() == null) {
      throw new IllegalArgumentException("Input type is required");
    }
    return switch (in.type()) {
      case "mouse" ->
          new InputEvent.Mouse(
              InputEvent.MouseAction.valueOf(upper(in.action())),
              require(in.x()),
              require(in.y()),
              in.button() == null ? null : InputEvent.MouseButton.valueOf(upper(in.button())),
              in.clickCount() == null ? 0 : in.clickCount());
      case "wheel" ->
          new InputEvent.Wheel(
              require(in.x()), require(in.y()), orZero(in.deltaX()), orZero(in.deltaY()));
      case "key" ->
          new InputEvent.Key(
              InputEvent.KeyAction.valueOf(upper(in.action())),
              in.key(),
              in.modifiers() == null ? 0 : in.modifiers());
      case "text" -> new InputEvent.Text(in.text());
      default -> throw new IllegalArgumentException("Unknown input type");
    };
  }

  private static String upper(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Input action is required");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static double require(Double value) {
    if (value == null) {
      throw new IllegalArgumentException("Input coordinates are required");
    }
    return value;
  }

  private static double orZero(Double value) {
    return value == null ? 0 : value;
  }
}
