package com.goldys.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.header.HeaderWriter;

/**
 * Adds {@code no-transform} to {@code Cache-Control} on Server-Sent Events responses.
 *
 * <p>The Next.js proxy gzip-compresses responses, and compressing a stream buffers it: the browser
 * receives no events (or delayed bursts) and the stream appears dead. {@code no-transform} tells
 * the proxy, and any CDN in front of it, to leave the stream uncompressed. Requests matched by
 * content type so every SSE endpoint is covered without listing paths.
 */
public final class StreamingCacheControlHeaderWriter implements HeaderWriter {
  private static final String EVENT_STREAM = "text/event-stream";
  private static final String NO_TRANSFORM = "no-transform";

  @Override
  public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
    String contentType = response.getContentType();
    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(EVENT_STREAM)) {
      return;
    }
    String cacheControl = response.getHeader(HttpHeaders.CACHE_CONTROL);
    if (cacheControl == null) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, " + NO_TRANSFORM);
    } else if (!cacheControl.contains(NO_TRANSFORM)) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl + ", " + NO_TRANSFORM);
    }
  }
}
