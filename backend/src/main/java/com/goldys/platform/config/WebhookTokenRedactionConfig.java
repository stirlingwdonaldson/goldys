package com.goldys.platform.config;

import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Scrubs webhook secrets out of Sentry events before they leave the platform.
 *
 * <p>The ingest webhooks accept their shared token as a {@code ?token=} query parameter (some
 * sources, e.g. Looker, can only call a URL and cannot set a custom header). Sentry's Spring
 * integration records the request URL and query string on the request context of errors and
 * transactions, and on HTTP span attributes and breadcrumbs, so a misconfigured or rejected webhook
 * call would otherwise write its secret into ops-sentry. The three callbacks registered here strip
 * the {@code token} parameter (and the {@code X-Webhook-Token} header) from every error,
 * transaction and breadcrumb before capture. With no DSN configured the callbacks are simply never
 * invoked.
 */
@Configuration
public class WebhookTokenRedactionConfig {

  static final String TOKEN_PARAM = "token";
  static final String TOKEN_HEADER = "X-Webhook-Token";
  private static final String REDACTED = "[redacted]";

  @Bean
  SentryOptions.BeforeSendCallback redactWebhookTokens() {
    return (event, hint) -> {
      scrubRequest(event.getRequest());
      return event;
    };
  }

  @Bean
  SentryOptions.BeforeSendTransactionCallback redactWebhookTokensFromTransactions() {
    return (transaction, hint) -> {
      scrubTransaction(transaction);
      return transaction;
    };
  }

  @Bean
  SentryOptions.BeforeBreadcrumbCallback redactWebhookTokensFromBreadcrumbs() {
    return (breadcrumb, hint) -> {
      scrubStringMap(breadcrumb.getData());
      return breadcrumb;
    };
  }

  private static void scrubRequest(Request request) {
    if (request == null) {
      return;
    }
    if (request.getUrl() != null) {
      request.setUrl(scrubToken(request.getUrl()));
    }
    if (request.getQueryString() != null) {
      request.setQueryString(scrubToken(request.getQueryString()));
    }
    if (request.getHeaders() != null && request.getHeaders().containsKey(TOKEN_HEADER)) {
      request.getHeaders().put(TOKEN_HEADER, REDACTED);
    }
  }

  private static void scrubTransaction(SentryTransaction transaction) {
    if (transaction == null) {
      return;
    }
    scrubRequest(transaction.getRequest());
    if (transaction.getSpans() != null) {
      for (SentrySpan span : transaction.getSpans()) {
        scrubStringMap(span.getData());
      }
    }
  }

  /** Scrubs any {@code String} value in a span-data or breadcrumb-data map that may carry a URL. */
  private static void scrubStringMap(Map<String, Object> data) {
    if (data == null) {
      return;
    }
    data.replaceAll((key, value) -> value instanceof String s ? scrubToken(s) : value);
  }

  /** Removes {@code token=...} from a full URL (scheme + path + query) or a bare query string. */
  static String scrubToken(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    int queryStart = value.indexOf('?');
    if (queryStart < 0) {
      return stripQueryParam(value, TOKEN_PARAM);
    }
    String base = value.substring(0, queryStart);
    String stripped = stripQueryParam(value.substring(queryStart + 1), TOKEN_PARAM);
    return stripped.isEmpty() ? base : base + "?" + stripped;
  }

  /** Removes {@code token=...} from a full URL (scheme + path + query). */
  static String stripTokenFromUrl(String url) {
    return scrubToken(url);
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
