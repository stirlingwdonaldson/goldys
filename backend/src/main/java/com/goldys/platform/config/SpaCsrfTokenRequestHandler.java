package com.goldys.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF handler for a cookie-based SPA: force the token to be written on every request, and read the
 * raw (non-masked) token the frontend echoes back in the {@code X-XSRF-TOKEN} header.
 *
 * <p>Spring Security's default handler defers token generation until a CSRF-protected mutating
 * request, so a cookie-backed SPA never sees the token; and it masks the token, which a cookie
 * round-trip cannot unmask. This is the documented SPA pattern from the Spring Security reference.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
  private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    this.xor.handle(request, response, csrfToken);
    // Force generation so the CookieCsrfTokenRepository writes the XSRF-TOKEN cookie on every
    // response, including GETs, before the SPA ever needs to send it back.
    csrfToken.get();
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    String headerValue = request.getHeader(csrfToken.getHeaderName());
    return StringUtils.hasText(headerValue)
        ? this.plain.resolveCsrfTokenValue(request, csrfToken)
        : this.xor.resolveCsrfTokenValue(request, csrfToken);
  }
}
