package com.goldys.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs the duration of every API request so slow endpoints surface in the logs instead of staying
 * anecdotal. At DEBUG it records every {@code /api/**} call; at WARN it flags the slow ones.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);

  /** Requests taking at least this long are logged at WARN so they are easy to spot. */
  private static final long WARN_THRESHOLD_MS = 1000;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!path.startsWith("/api/")) {
      chain.doFilter(request, response);
      return;
    }

    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;
      int status = response.getStatus();
      if (elapsedMs >= WARN_THRESHOLD_MS) {
        log.warn("SLOW {} {} -> {} ({}ms)", request.getMethod(), path, status, elapsedMs);
      } else {
        log.debug("{} {} -> {} ({}ms)", request.getMethod(), path, status, elapsedMs);
      }
    }
  }
}
