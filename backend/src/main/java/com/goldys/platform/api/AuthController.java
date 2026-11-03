package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service credential changes. Login and logout are handled by Spring Security's filter chain.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final SecurityContextRepository securityContexts;

  public AuthController(AuthService authService, SecurityContextRepository securityContexts) {
    this.authService = authService;
    this.securityContexts = securityContexts;
  }

  @PostMapping("/change-password")
  ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AccountUserDetails user,
      @RequestBody ChangePasswordRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    AccountUserDetails refreshed =
        authService.changePassword(user, body.currentPassword(), body.newPassword());
    var authentication = new UsernamePasswordAuthenticationToken(refreshed, null, List.of());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContexts.saveContext(context, request, response);
    return ResponseEntity.noContent().build();
  }

  record ChangePasswordRequest(String currentPassword, String newPassword) {}
}
