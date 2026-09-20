package com.goldys.platform.api;

import com.goldys.platform.auth.AuthService;
import com.goldys.platform.auth.StaffProfileSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service sign-up. Login and logout are handled by Spring Security's filter chain. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/signup")
  ResponseEntity<StaffProfileSummary> signup(@RequestBody SignupRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(authService.signup(body.email(), body.displayName(), body.password()));
  }

  record SignupRequest(String email, String displayName, String password) {}
}
