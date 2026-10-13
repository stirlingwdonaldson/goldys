package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.application.OpenTableSessionApplicationService.Input;
import com.goldys.platform.connectors.opentable.session.InputEvent;
import org.junit.jupiter.api.Test;

/** The input translation is the trust boundary for what reaches the sidecar browser. */
class OpenTableSessionApplicationServiceTest {

  private static Input mouse(String action, Double x, Double y) {
    return new Input("mouse", action, x, y, "left", 1, null, null, null, null, null);
  }

  @Test
  void translatesMouseAndClampsCoordinates() {
    InputEvent event = OpenTableSessionApplicationService.toEvent(mouse("down", -5.0, 99999.0));

    assertThat(event)
        .isInstanceOfSatisfying(
            InputEvent.Mouse.class,
            m -> {
              assertThat(m.action()).isEqualTo(InputEvent.MouseAction.DOWN);
              assertThat(m.x()).isZero();
              assertThat(m.y()).isEqualTo(InputEvent.MAX_COORDINATE);
              assertThat(m.button()).isEqualTo(InputEvent.MouseButton.LEFT);
            });
  }

  @Test
  void allowsListedKeysOnly() {
    Input enter = new Input("key", "down", null, null, null, null, null, null, "Enter", 0, null);
    Input f12 = new Input("key", "down", null, null, null, null, null, null, "F12", 0, null);

    assertThat(OpenTableSessionApplicationService.toEvent(enter))
        .isInstanceOf(InputEvent.Key.class);
    assertThatThrownBy(() -> OpenTableSessionApplicationService.toEvent(f12))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void boundsTextLength() {
    Input tooLong =
        new Input("text", null, null, null, null, null, null, null, null, null, "x".repeat(257));

    assertThatThrownBy(() -> OpenTableSessionApplicationService.toEvent(tooLong))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnknownTypesAndMissingCoordinates() {
    Input unknown = new Input("drag", null, null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> OpenTableSessionApplicationService.toEvent(unknown))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> OpenTableSessionApplicationService.toEvent(mouse("down", null, 1.0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toStringNeverIncludesTypedText() {
    Input password =
        new Input("text", null, null, null, null, null, null, null, null, null, "hunter2");

    assertThat(password.toString()).doesNotContain("hunter2");
  }
}
