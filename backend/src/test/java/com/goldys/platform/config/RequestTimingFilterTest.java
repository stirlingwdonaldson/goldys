package com.goldys.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTimingFilterTest {

  private final RequestTimingFilter filter = new RequestTimingFilter();

  @Test
  void invokesTheRestOfTheChainForApiRequests() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sales/latest");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean called = new AtomicBoolean(false);

    filter.doFilter(request, response, (req, res) -> called.set(true));

    assertThat(called).isTrue();
  }

  @Test
  void rethrowsExceptionsFromTheChainInsteadOfSwallowingThem() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sales/latest");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (req, res) -> {
                      throw new ServletException("boom");
                    }))
        .isInstanceOf(ServletException.class)
        .hasMessage("boom");
  }
}
