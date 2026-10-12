package com.goldys.platform.config;

import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Scrubs webhook secrets out of Sentry events before they leave the platform.
 *
 * <p>The ingest webhooks accept their shared token as a {@code ?token=} query parameter (some
 * sources, e.g. Looker, can only call a URL and cannot set a custom header). Sentry's Spring
 * integration records the request URL and query string on the event's request context, so a
 * misconfigured or rejected webhook call would otherwise write its secret into ops-sentry. This
 * {@link SentryOptions.BeforeSendCallback} strips the {@code token} parameter (and the {@code
 * X-Webhook-Token} header) from every event before capture. With no DSN configured the callback is
 * simply never invoked.
 */
@Configuration
public class WebhookTokenRedactionConfig {

  static final String TOKEN_PARAM = "token";
  static final String TOKEN_HEADER = "X-Webhook-Token";
  private static final String REDACTED = "[redacted]";

  @Bean
  SentryOptions.BeforeSendCallback redactWebhookTokens() {
    return (event, hint) -> {
      Request request = event.getRequest();
      if (request == null) {
        return event;
      }
      if (request.getUrl() != null) {
        request.setUrl(stripTokenFromUrl(request.getUrl()));
      }
      if (request.getQueryString() != null) {
        request.setQueryString(stripQueryParam(request.getQueryString(), TOKEN_PARAM));
      }
      if (request.getHeaders() != null && request.getHeaders().containsKey(TOKEN_HEADER)) {
        request.getHeaders().put(TOKEN_HEADER, REDACTED);
      }
      return event;
    };
  }

  /** Removes {@code token=...} from a full URL (scheme + path + query). */
  static String stripTokenFromUrl(String url) {
    int queryStart = url.indexOf('?');
    if (queryStart < 0) {
      return url;
    }
    String base = url.substring(0, queryStart);
    String query = url.substring(queryStart + 1);
    String stripped = stripQueryParam(query, TOKEN_PARAM);
    return stripped.isEmpty() ? base : base + "?" + stripped;
  }

  /** Removes a {@code param=...} pair from a bare {@code a=1&b=2} query string. */
  static String stripQueryParam(String query, String param) {
    if (query == null || query.isEmpty()) {
      return query;
    }
    String[] parts = query.split("&");
    List<String> kept = new ArrayList<>(parts.length);
    for (String part : parts) {
      if (part.equals(param) || part.startsWith(param + "=")) {
        continue;
      }
      kept.add(part);
    }
    return String.join("&", kept);
  }
}
