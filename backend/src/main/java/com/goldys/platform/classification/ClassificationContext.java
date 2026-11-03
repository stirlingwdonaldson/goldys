package com.goldys.platform.classification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Reviewed context is versioned with code; every run stores the exact bytes it used. */
final class ClassificationContext {
  static final String TEXT = load();

  private ClassificationContext() {}

  private static String load() {
    try {
      return new ClassPathResource("classification/business-context.txt")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Classification business context is unavailable", e);
    }
  }
}
