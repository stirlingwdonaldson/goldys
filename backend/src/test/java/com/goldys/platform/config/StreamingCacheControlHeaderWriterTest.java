package com.goldys.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class StreamingCacheControlHeaderWriterTest {

  private final StreamingCacheControlHeaderWriter writer = new StreamingCacheControlHeaderWriter();
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void appendsNoTransformToAnEventStream() {
    response.setContentType("text/event-stream");
    response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");

    writer.writeHeaders(request, response);

    assertThat(response.getHeader("Cache-Control"))
        .contains("no-transform")
        .contains("no-cache")
        .contains("no-store");
  }

  @Test
  void addsCacheControlWhenTheStreamHasNone() {
    response.setContentType("text/event-stream");

    writer.writeHeaders(request, response);

    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache, no-transform");
  }

  @Test
  void leavesNonStreamingResponsesAlone() {
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-cache");

    writer.writeHeaders(request, response);

    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
  }

  @Test
  void doesNotDuplicateNoTransform() {
    response.setContentType("text/event-stream;charset=UTF-8");
    response.setHeader("Cache-Control", "no-cache, no-transform");

    writer.writeHeaders(request, response);

    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache, no-transform");
  }
}
