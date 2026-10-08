package com.goldys.platform.config;

import io.sentry.Sentry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request one correlation id, resolved once: the caller's {@code X-Correlation-ID} if
 * it looks sane, otherwise a fresh UUID. The id is echoed in the response header, exposed to the
 * error envelope via {@link #ATTRIBUTE}, and tagged on the request's Sentry scope, so an id read
 * off a manager's screenshot finds the matching Sentry event.
 *
 * <p>Ordered just after Sentry's own request filter (which forks the per-request scope at {@link
 * Ordered#HIGHEST_PRECEDENCE}), so the tag lands on this request's scope rather than a global one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CorrelationIdFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Correlation-ID";
  public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

  /** Accept only short, plain ids from callers; anything else is replaced, never echoed. */
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String provided = request.getHeader(HEADER);
    String id =
        provided != null && SAFE_ID.matcher(provided).matches()
            ? provided
            : UUID.randomUUID().toString();
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader(HEADER, id);
    Sentry.setTag("correlation_id", id);
    chain.doFilter(request, response);
  }

  /** The id resolved for this request, or a fresh one if the filter did not run (e.g. in tests). */
  public static String of(HttpServletRequest request) {
    Object id = request.getAttribute(ATTRIBUTE);
    return id instanceof String s ? s : UUID.randomUUID().toString();
  }
}
