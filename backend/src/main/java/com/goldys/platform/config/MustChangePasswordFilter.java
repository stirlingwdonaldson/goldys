package com.goldys.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.api.ApiErrorResponse;
import com.goldys.platform.auth.AccountUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects every request from a principal that must change its password, except the endpoints needed
 * to change it or leave. Hiding the UI is not enforcement; a direct API call must fail too.
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {
  private static final Set<String> ALLOWED =
      Set.of(
          "/api/me",
          "/api/auth/change-password",
          "/api/auth/login",
          "/api/auth/logout",
          "/api/health");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AccountUserDetails user
        && user.mustChangePassword()
        && !ALLOWED.contains(request.getRequestURI())) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("application/json");
      objectMapper.writeValue(
          response.getWriter(),
          new ApiErrorResponse(
              "PASSWORD_CHANGE_REQUIRED",
              "Change your password before continuing",
              (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE),
              Map.of()));
      return;
    }
    chain.doFilter(request, response);
  }
}
