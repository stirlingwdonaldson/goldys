package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RecipePlainTextTest {
  @Test
  void preservesInstructionsAndRemovesMarkupAndExecutableContent() {
    String text =
        RecipePlainText.extract(
            "<style>body{color:red}</style><p>Preheat to 165 &amp; stir.</p><ol><li>Bake 40 minutes.</li><li>Cool &lt; 10 minutes.</li></ol><script>alert('bad')</script>");
    assertThat(text)
        .contains("Preheat to 165 & stir.", "1. Bake 40 minutes.", "2. Cool < 10 minutes.", "\n")
        .doesNotContain("<p>", "alert", "color:red");
    assertThat(RecipePlainText.extract("<br>")).isEmpty();
    assertThat(RecipePlainText.extract(null)).isNull();
  }
}
