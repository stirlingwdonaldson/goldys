package com.goldys.platform.connectors.opentable.session;

import java.util.Objects;
import java.util.Set;

/**
 * A live-view input event, validated at construction so nothing unbounded reaches the browser.
 * Coordinates are in the page's CSS viewport. Printable characters (and pastes) arrive as {@link
 * Text}; {@link Key} is limited to the editing and navigation keys a login form needs.
 *
 * <p>These carry what the owner types, including their OpenTable password: never log them.
 */
public sealed interface InputEvent {
  int MAX_COORDINATE = 4096;
  int MAX_TEXT_LENGTH = 256;

  enum MouseAction {
    DOWN,
    UP,
    MOVE
  }

  enum MouseButton {
    NONE,
    LEFT,
    MIDDLE,
    RIGHT
  }

  enum KeyAction {
    DOWN,
    UP
  }

  /** Keys allowed through {@link Key}; everything printable goes through {@link Text}. */
  Set<String> ALLOWED_KEYS =
      Set.of(
          "Enter",
          "Backspace",
          "Tab",
          "Escape",
          "Delete",
          "ArrowUp",
          "ArrowDown",
          "ArrowLeft",
          "ArrowRight",
          "Home",
          "End",
          "Shift",
          "Control",
          "Alt",
          "Meta");

  record Mouse(MouseAction action, double x, double y, MouseButton button, int clickCount)
      implements InputEvent {
    public Mouse {
      Objects.requireNonNull(action, "action");
      button = button == null ? MouseButton.NONE : button;
      x = clamp(x);
      y = clamp(y);
      clickCount = Math.max(0, Math.min(clickCount, 3));
    }
  }

  record Wheel(double x, double y, double deltaX, double deltaY) implements InputEvent {
    public Wheel {
      x = clamp(x);
      y = clamp(y);
      deltaX = Math.max(-2000, Math.min(deltaX, 2000));
      deltaY = Math.max(-2000, Math.min(deltaY, 2000));
    }
  }

  /** {@code modifiers} is the CDP bitmask: Alt=1, Ctrl=2, Meta=4, Shift=8. */
  record Key(KeyAction action, String key, int modifiers) implements InputEvent {
    public Key {
      Objects.requireNonNull(action, "action");
      if (key == null || !ALLOWED_KEYS.contains(key)) {
        throw new IllegalArgumentException("Key not allowed");
      }
      modifiers = modifiers & 0b1111;
    }
  }

  record Text(String value) implements InputEvent {
    public Text {
      if (value == null || value.isEmpty() || value.length() > MAX_TEXT_LENGTH) {
        throw new IllegalArgumentException("Text must be 1-" + MAX_TEXT_LENGTH + " characters");
      }
    }

    @Override
    public String toString() {
      return "Text[<redacted>]"; // Guards against accidental logging of typed passwords.
    }
  }

  private static double clamp(double value) {
    if (Double.isNaN(value)) {
      return 0;
    }
    return Math.max(0, Math.min(value, MAX_COORDINATE));
  }
}
