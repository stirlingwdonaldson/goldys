package com.goldys.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void keepsAPlainCallerSuppliedId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sales");
    request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(CorrelationIdFilter.of(request)).isEqualTo("abc-123");
    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
  }

  @Test
  void replacesAnUnsafeIdRatherThanEchoingIt() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sales");
    request.addHeader(CorrelationIdFilter.HEADER, "<script>alert(1)</script>");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String id = CorrelationIdFilter.of(request);
    assertThat(id).doesNotContain("<").hasSize(36);
    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(id);
  }

  @Test
  void generatesAnIdWhenNoneIsSupplied() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sales");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(CorrelationIdFilter.of(request)).hasSize(36);
  }
}
