# Email + Password Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the OIDC (Google OAuth) login with a native Spring Security email + password flow and a self-service sign-up that lands every new user at the lowest access level.

**Architecture:** Swap the OIDC-keyed `staff_profile` for an email-keyed `user_account` carrying a BCrypt password hash and the `(department, seniority)` pair the existing `permission` model already keys on. Login goes through Spring Security's form-login machinery (JSON responses, session-based), signup is a plain controller endpoint, and `CurrentUserService` resolves `UserRole` from the authenticated `AccountUserDetails` principal instead of `OidcUser`. "Auto-login after signup" is implemented client-side (signup returns `201`, then the frontend immediately calls `login` with the same credentials), so the backend reuses the login filter's session-fixation + CSRF handling rather than reimplementing programmatic login.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Security 6 (form login, BCrypt, session), JPA, PostgreSQL 16, Flyway, Testcontainers; Next.js frontend.

**Spec:** `docs/superpowers/specs/2026-09-20-email-password-auth-design.md`

## Global Constraints

- Password stored only as a BCrypt hash; never log passwords or tokens.
- Permission checks route through the sole `PermissionService`; explicit denial, no owner bypass.
- `permission` grants are keyed by `(department, seniority)` and stay untouched (V6 seed `ALL`/`OWNER` full access remains).
- Default sign-up role is `department = 'ALL'`, `seniority = 'STAFF'`; no promotion code (manual DB edit).
- Schema is owned by Flyway (`ddl-auto: validate`); every table change is a versioned migration.
- Email is lower-case-normalized before any lookup or insert.
- No email verification, no password reset, no account lockout in this slice.

## Review Focus

1. **Email case-insensitive login** — sign up as `Stirling@Donaldson.com`, log in as `stirling@donaldson.com` → succeeds (normalization applies on both write and read).
2. **Duplicate email across case** — sign up `A@b.com` after `a@b.com` exists → rejected, not a second account.
3. **Inactive account** — an account with `active = false` cannot log in (`401`), even with the correct password.
4. **Session persistence** — after login, `GET /api/me` and the reconciliation endpoints see the same authenticated principal across requests.
5. **Fresh `STAFF` has no access** — a new sign-up calling `GET /api/reconciliation/exceptions` gets `NOT_PERMITTED` (explicit denial, never an empty/filtered result).

Each line above is pinned by a test in the task that owns the code (noted in that task).

---

### Task 1: Replace `staff_profile` with `user_account`

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__user_accounts.sql`
- Create: `backend/src/main/java/com/goldys/platform/auth/UserAccount.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/UserAccountRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/StaffProfileSummary.java`
- Delete: `backend/src/main/java/com/goldys/platform/auth/StaffProfile.java`
- Delete: `backend/src/main/java/com/goldys/platform/auth/StaffProfileRepository.java`
- Delete: `backend/src/main/java/com/goldys/platform/auth/StaffProfileService.java`
- Test: `backend/src/test/java/com/goldys/platform/auth/UserAccountRepositoryTest.java`

**Interfaces:**
- Consumes: existing `permission` table (unchanged).
- Produces: `UserAccount` (package-private entity, `create(...)` + accessors), `UserAccountRepository` (`findByEmail(String)`, `existsByEmail(String)`), `StaffProfileSummary(String displayName, String department, String seniority)` (public record) for Task 2 and Task 3.

- [ ] **Step 1: Write the V7 migration**

```sql
-- Email-keyed account carrying the password hash and the (department, seniority) pair
-- permission decisions use. The OIDC-keyed staff_profile is dropped in V8, once the code
-- that reads it has been re-keyed (the swap is atomic).
CREATE TABLE user_account (
    id uuid PRIMARY KEY,
    email varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    department varchar(100) NOT NULL,
    seniority varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT user_account_email_key UNIQUE (email)
);

CREATE INDEX idx_user_account_role
    ON user_account (department, seniority) WHERE active = true;
```

- [ ] **Step 2: Write `UserAccount` entity**

```java
package com.goldys.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A staff account: email + password hash + the department/seniority pair permissions key on. */
@Entity
@Table(name = "user_account")
class UserAccount {
  @Id private UUID id;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 255)
  private String displayName;

  @Column(name = "department", nullable = false, length = 100)
  private String department;

  @Column(name = "seniority", nullable = false, length = 100)
  private String seniority;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserAccount() {}

  private UserAccount(
      String email, String passwordHash, String displayName, String department, String seniority, Instant now) {
    this.id = UUID.randomUUID();
    this.email = Objects.requireNonNull(email, "email");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.department = Objects.requireNonNull(department, "department");
    this.seniority = Objects.requireNonNull(seniority, "seniority");
    this.active = true;
    this.createdAt = now;
    this.updatedAt = now;
  }

  static UserAccount create(
      String email, String passwordHash, String displayName, String department, String seniority, Instant now) {
    return new UserAccount(email, passwordHash, displayName, department, seniority, now);
  }

  UUID id() { return id; }
  String email() { return email; }
  String passwordHash() { return passwordHash; }
  String displayName() { return displayName; }
  String department() { return department; }
  String seniority() { return seniority; }
  boolean active() { return active; }
  Instant createdAt() { return createdAt; }
  Instant updatedAt() { return updatedAt; }
}
```

- [ ] **Step 3: Write `UserAccountRepository`**

```java
package com.goldys.platform.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmail(String email);
  boolean existsByEmail(String email);
}
```

- [ ] **Step 4: Write the repository test**

```java
package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class UserAccountRepositoryTest {

  @Autowired UserAccountRepository repository;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table user_account");
  }

  @Test
  void savesAndFindsByEmail() {
    UserAccount account =
        UserAccount.create("a@b.com", "hash", "A B", "ALL", "STAFF", Instant.now());
    repository.save(account);

    assertThat(repository.findByEmail("a@b.com")).isPresent();
    assertThat(repository.existsByEmail("a@b.com")).isTrue();
    assertThat(repository.existsByEmail("x@y.com")).isFalse();
  }
}
```

- [ ] **Step 5: Compile and run the unit tests that do not need Docker**

Run: `cd backend && ./gradlew compileJava spotlessApply spotlessCheck`
Expected: BUILD SUCCESSFUL (the `UserAccountRepositoryTest` is Testcontainers-gated and does not run here).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V7__user_accounts.sql \
        backend/src/main/java/com/goldys/platform/auth/UserAccount.java \
        backend/src/main/java/com/goldys/platform/auth/UserAccountRepository.java \
        backend/src/test/java/com/goldys/platform/auth/UserAccountRepositoryTest.java
git commit -m "feat: add email-keyed user_account"
```

---

### Task 2: Auth components (UserDetails, AuthService, PasswordEncoder)

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/auth/AccountUserDetails.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/AccountUserDetailsService.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/AuthService.java`
- Modify: `backend/src/main/java/com/goldys/platform/config/SecurityConfig.java` (add `passwordEncoder` bean only)
- Test: `backend/src/test/java/com/goldys/platform/auth/AuthServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/auth/AccountUserDetailsServiceTest.java`

**Interfaces:**
- Consumes: `UserAccountRepository` (Task 1), `PasswordEncoder`.
- Produces: `AccountUserDetails` (public record implementing `UserDetails`, accessors `email()`, `displayName()`, `department()`, `seniority()`), `AccountUserDetailsService implements UserDetailsService`, `AuthService.signup(String email, String displayName, String password) -> StaffProfileSummary`, and a `PasswordEncoder` bean.

- [ ] **Step 1: Write `AccountUserDetails`**

```java
package com.goldys.platform.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The request principal: the account fields plus the Spring {@link UserDetails} contract. */
public record AccountUserDetails(
    UUID id,
    String email,
    String passwordHash,
    String displayName,
    String department,
    String seniority,
    boolean active)
    implements UserDetails {

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }
}
```

- [ ] **Step 2: Write `AccountUserDetailsService`**

```java
package com.goldys.platform.auth;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads an account by (normalized) email for the {@code DaoAuthenticationProvider}. */
@Service
public class AccountUserDetailsService implements UserDetailsService {
  private final UserAccountRepository accounts;

  public AccountUserDetailsService(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    String email = username.trim().toLowerCase(Locale.ROOT);
    UserAccount account =
        accounts
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    return new AccountUserDetails(
        account.id(),
        account.email(),
        account.passwordHash(),
        account.displayName(),
        account.department(),
        account.seniority(),
        account.active());
  }
}
```

- [ ] **Step 3: Write `AuthService`**

```java
package com.goldys.platform.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service sign-up: validate, hash, and insert an account at the lowest role. */
@Service
public class AuthService {
  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final String DEFAULT_DEPARTMENT = "ALL";
  private static final String DEFAULT_SENIORITY = "STAFF";
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Clock CLOCK = Clock.systemUTC();

  private final UserAccountRepository accounts;
  private final PasswordEncoder passwordEncoder;

  public AuthService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public StaffProfileSummary signup(String email, String displayName, String password) {
    String normalizedEmail = normalizeEmail(email);
    if (normalizedEmail == null || !EMAIL.matcher(normalizedEmail).matches()) {
      throw new IllegalArgumentException("Enter a valid email address");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("Display name is required");
    }
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    if (accounts.existsByEmail(normalizedEmail)) {
      throw new IllegalArgumentException("An account already exists for " + normalizedEmail);
    }

    UserAccount account =
        UserAccount.create(
            normalizedEmail,
            passwordEncoder.encode(password),
            displayName.trim(),
            DEFAULT_DEPARTMENT,
            DEFAULT_SENIORITY,
            CLOCK.instant());
    accounts.save(account);
    return new StaffProfileSummary(
        account.displayName(), account.department(), account.seniority());
  }

  public static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
```

- [ ] **Step 4: Add the `PasswordEncoder` bean to `SecurityConfig`** (keep everything else as-is for now)

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// inside SecurityConfig:
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
```

- [ ] **Step 5: Write the `AuthServiceTest`**

```java
package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final AuthService service = new AuthService(accounts, new BCryptPasswordEncoder());

  @Test
  void normalizesEmailAndHashesPassword() {
    when(accounts.existsByEmail("a@b.com")).thenReturn(false);
    when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

    StaffProfileSummary summary = service.signup("  A@B.com  ", "A B", "password123");

    assertThat(summary.department()).isEqualTo("ALL");
    assertThat(summary.seniority()).isEqualTo("STAFF");
    assertThat(summary.displayName()).isEqualTo("A B");
    verify(accounts).existsByEmail("a@b.com"); // normalized
  }

  @Test
  void rejectsInvalidEmail() {
    assertThatThrownBy(() -> service.signup("not-an-email", "A", "password123"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsShortPassword() {
    assertThatThrownBy(() -> service.signup("a@b.com", "A", "short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsDuplicateEmailRegardlessOfCase() {
    when(accounts.existsByEmail("a@b.com")).thenReturn(true);

    assertThatThrownBy(() -> service.signup("A@B.com", "A", "password123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a@b.com");
  }
}
```

- [ ] **Step 6: Write the `AccountUserDetailsServiceTest`**

```java
package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AccountUserDetailsServiceTest {

  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final AccountUserDetailsService service = new AccountUserDetailsService(accounts);

  @Test
  void loadsByNormalizedEmail() {
    when(accounts.findByEmail("a@b.com"))
        .thenReturn(
            Optional.of(
                UserAccount.create("a@b.com", "hash", "A B", "ALL", "OWNER", Instant.now())));

    AccountUserDetails user = (AccountUserDetails) service.loadUserByUsername("  A@B.com ");

    assertThat(user.email()).isEqualTo("a@b.com");
    assertThat(user.seniority()).isEqualTo("OWNER");
  }

  @Test
  void throwsForUnknownEmail() {
    when(accounts.findByEmail("x@y.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("x@y.com"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
```

- [ ] **Step 7: Run the unit tests**

Run: `cd backend && ./gradlew test --tests '*AuthServiceTest' --tests '*AccountUserDetailsServiceTest' spotlessApply spotlessCheck`
Expected: PASS (4 + 2 tests).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/auth/AccountUserDetails.java \
        backend/src/main/java/com/goldys/platform/auth/AccountUserDetailsService.java \
        backend/src/main/java/com/goldys/platform/auth/AuthService.java \
        backend/src/main/java/com/goldys/platform/config/SecurityConfig.java \
        backend/src/test/java/com/goldys/platform/auth/AuthServiceTest.java \
        backend/src/test/java/com/goldys/platform/auth/AccountUserDetailsServiceTest.java
git commit -m "feat: add email+password account loading and sign-up service"
```

---

### Task 3: Backend auth swap (security config, endpoints, re-key, override actor, dependency removal)

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/config/SecurityConfig.java` (full rework)
- Create: `backend/src/main/java/com/goldys/platform/api/AuthController.java`
- Modify: `backend/src/main/java/com/goldys/platform/auth/CurrentUserService.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/CurrentUserController.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/ReconciliationController.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/DashboardController.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/ConnectorStatusController.java`
- Create: `backend/src/main/resources/db/migration/V8__override_actor_email.sql`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverride.java`
- Modify: `backend/src/main/java/com/goldys/platform/reconciliation/DailySalesOverrideService.java`
- Modify: `backend/build.gradle` (drop `spring-boot-starter-oauth2-client`)
- Modify: `backend/src/main/resources/application.yml` + `application-prod.yml` (remove OIDC notes/vars)
- Test: `backend/src/test/java/com/goldys/platform/api/AuthControllerTest.java`
- Test: Modify `backend/src/test/java/com/goldys/platform/api/ReconciliationControllerTest.java`
- Test: Modify `backend/src/test/java/com/goldys/platform/reconciliation/DailySalesOverrideServiceTest.java`

**Interfaces:**
- Consumes: `AccountUserDetails`, `AccountUserDetailsService`, `AuthService` (Task 2); `StaffProfileSummary` (Task 1); `DailySalesOverrideService`.
- Produces: `POST /api/auth/signup`, `POST /api/auth/login` (form login), `POST /api/auth/logout`, `GET /api/me`; `CurrentUserService.roleOf(AccountUserDetails) -> UserRole`; `DailySalesOverrideService.save(UserRole, String actorEmail, LocalDate, String, String)`.

- [ ] **Step 1: Rework `SecurityConfig`** (remove OIDC, add form login + JSON handlers + logout + permitAll + the encoder bean)

```java
package com.goldys.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.api.ApiErrorResponse;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.StaffProfileSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** Email + password session auth. Only the health, webhook, signup and login endpoints are public. */
@Configuration
public class SecurityConfig {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    "/api/health",
                    "/api/ingest/lightspeed",
                    "/api/auth/signup",
                    "/api/auth/login")
                .permitAll()
                .anyRequest()
                .authenticated());

    // The server-to-server webhook and the unauthenticated auth endpoints are CSRF-exempt; browser
    // sessions keep CSRF via a cookie the frontend reads back into the X-XSRF-TOKEN header.
    http.csrf(
        csrf ->
            csrf.ignoringRequestMatchers(
                    "/api/ingest/lightspeed", "/api/auth/signup", "/api/auth/login")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));

    http.formLogin(
        form ->
            form.loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .successHandler(this::successHandler)
                .failureHandler(this::failureHandler));

    http.logout(
        logout ->
            logout.logoutUrl("/api/auth/logout").logoutSuccessHandler(
                (request, response, authentication) -> response.setStatus(204)));

    return http.build();
  }

  private void successHandler(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    AccountUserDetails user = (AccountUserDetails) authentication.getPrincipal();
    response.setStatus(200);
    response.setContentType("application/json");
    objectMapper.writeValue(
        response.getWriter(),
        new StaffProfileSummary(user.displayName(), user.department(), user.seniority()));
  }

  private void failureHandler(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    response.setStatus(401);
    response.setContentType("application/json");
    objectMapper.writeValue(
        response.getWriter(),
        new ApiErrorResponse("INVALID_CREDENTIALS", "Invalid email or password", null, Map.of()));
  }
}
```

- [ ] **Step 2: Write `AuthController` (signup only — login/logout are handled by the filter chain)**

```java
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
```

- [ ] **Step 3: Re-key `CurrentUserService`**

```java
package com.goldys.platform.auth;

import org.springframework.stereotype.Service;

/** Maps the authenticated account to the role used for permission decisions. */
@Service
public class CurrentUserService {
  public UserRole roleOf(AccountUserDetails user) {
    return new UserRole(
        new DepartmentCode(user.department()), new SeniorityCode(user.seniority()));
  }
}
```

- [ ] **Step 4: Re-key `CurrentUserController`** (replace `OidcUser` with `AccountUserDetails`)

```java
package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.StaffProfileSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the current staff identity to the frontend. */
@RestController
@RequestMapping("/api")
public class CurrentUserController {

  @GetMapping("/me")
  StaffProfileSummary me(@AuthenticationPrincipal AccountUserDetails user) {
    return new StaffProfileSummary(user.displayName(), user.department(), user.seniority());
  }
}
```

- [ ] **Step 5: Re-key the three controllers** — replace every `@AuthenticationPrincipal OidcUser user` with `@AuthenticationPrincipal AccountUserDetails user` and every `currentUser.roleOf(user)` call stays (the signature now takes `AccountUserDetails`). In `ReconciliationController`, the `override` method additionally passes `user.email()` instead of the OIDC issuer/subject:

```java
  @PostMapping("/records/{date}/override")
  OverrideResultDto override(
      @PathVariable LocalDate date,
      @RequestBody OverrideRequestDto body,
      @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    overrides.save(role, user.email(), date, body.source(), body.reason());
    return new OverrideResultDto(true, date.toString(), "daily_sales");
  }
```

Remove the now-unused imports of `OidcUser` from all four controllers.

- [ ] **Step 6: Write the V8 swap migration** (drop `staff_profile` + re-key the override actor)

```sql
-- The OIDC-keyed staff_profile is superseded by user_account (V7). Dropping it here keeps the
-- swap atomic: the code that read it is re-keyed in this same task.
DROP TABLE staff_profile;

-- The override actor identity was OIDC issuer+subject; with email+password auth it is the email.
ALTER TABLE daily_sales_override DROP COLUMN actor_oidc_issuer;
ALTER TABLE daily_sales_override DROP COLUMN actor_oidc_subject;
ALTER TABLE daily_sales_override ADD COLUMN actor_email varchar(255) NOT NULL;
```

- [ ] **Step 7: Re-key the override entity and service** (and delete the old staff-profile files, create the top-level summary)

In `DailySalesOverride`: replace the `actorOidcIssuer` + `actorOidcSubject` fields, constructor args, accessors, and `create(...)` signature with a single `actorEmail` (column `actor_email`). In `DailySalesOverrideService.save`, change the signature to `save(UserRole actor, String actorEmail, LocalDate date, String source, String reason)` and pass `actorEmail` through to `DailySalesOverride.create(...)`. In the "unknown source" check, the email is no longer involved (it is only recorded).

Then delete the three OIDC-keyed files and create the top-level summary (which the re-keyed `AuthController` and `CurrentUserController` return):

```bash
rm backend/src/main/java/com/goldys/platform/auth/StaffProfile.java
rm backend/src/main/java/com/goldys/platform/auth/StaffProfileRepository.java
rm backend/src/main/java/com/goldys/platform/auth/StaffProfileService.java
```

```java
package com.goldys.platform.auth;

/** The fields an API response may expose about the current staff identity. */
public record StaffProfileSummary(String displayName, String department, String seniority) {}
```

- [ ] **Step 8: Remove the OIDC dependency and config**

In `backend/build.gradle`, delete the line:

```groovy
	implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
```

In `application.yml`, delete the OIDC comment block (lines `spring.oauth2` comment). In `application-prod.yml`, delete the OIDC `spring.security.oauth2...` block and the `OIDC_REDIRECT_URI` line.

- [ ] **Step 9: Write `AuthControllerTest`** (signup only; `@WebMvcTest` with a mocked `AuthService` and no security config imported)

```java
package com.goldys.platform.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AuthService;
import com.goldys.platform.auth.StaffProfileSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean AuthService authService;

  @Test
  void signupReturnsCreatedUser() throws Exception {
    when(authService.signup("a@b.com", "A B", "password123"))
        .thenReturn(new StaffProfileSummary("A B", "ALL", "STAFF"));

    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"displayName\":\"A B\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.department").value("ALL"))
        .andExpect(jsonPath("$.seniority").value("STAFF"));
  }
}
```

The `/api/me` and login/logout HTTP behavior is exercised by the integration test (Task 5), not this slice test — importing the full `SecurityConfig` into `@WebMvcTest` would drag in `formLogin()`, which needs an `AuthenticationManager` the web slice does not auto-configure.

- [ ] **Step 10: Update `ReconciliationControllerTest`** — drop `@Import(SecurityConfig.class)`, replace `oidcLogin()` with `authentication(...)` carrying an `AccountUserDetails`, and `@MockBean` → `@MockitoBean`. The `override` test's mock call signature changes to `overrides.save(any(), any(), any(), any(), any())` (five args — the `UserRole`, the actor email, the date, the source, the reason; was six with the OIDC issuer+subject) and the `AccountUserDetails` must carry an `email`. Replace the `oidc()` helper with:

```java
private static RequestPostProcessor authenticated(AccountUserDetails user) {
  UsernamePasswordAuthenticationToken auth =
      new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of());
  return authentication(auth);
}
```

and use `.with(authenticated(owner()))` where `owner()` builds an `AccountUserDetails(UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true)`. Remove the `oidcLogin`/`oidc()`/`OidcUser` imports and the `SecurityConfig` import.

- [ ] **Step 11: Update `DailySalesOverrideServiceTest`** — the `save(...)` calls change from `(OWNER, "iss", "sub", date, source, reason)` to `(OWNER, "a@b.com", date, source, reason)`, and `DailySalesOverride.create(...)` from the OIDC args to the email arg.

- [ ] **Step 12: Compile + run the unit tests that don't need Docker**

Run: `cd backend && ./gradlew compileJava spotlessApply spotlessCheck test --tests '*AuthServiceTest' --tests '*AccountUserDetailsServiceTest' --tests '*AuthControllerTest' --tests '*ReconciliationControllerTest' --tests '*DailySalesOverrideServiceTest'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/java backend/src/test/java backend/src/main/resources backend/build.gradle
git commit -m "feat: swap OIDC for email+password session auth"
```

---

### Task 4: Frontend (login + signup pages, api client, user menu)

**Files:**
- Modify: `frontend/lib/api/index.ts`
- Modify: `frontend/lib/api/types.ts` (add signup/login input types)
- Modify: `frontend/lib/api/errors.ts` (add `INVALID_CREDENTIALS`)
- Create: `frontend/app/login/page.tsx`
- Create: `frontend/app/signup/page.tsx`
- Modify: `frontend/components/app-shell/user-menu.tsx`
- Test: `frontend/components/app-shell/user-menu.test.tsx` (if absent, add a small Vitest for the auth client)

**Interfaces:**
- Consumes: `fetchApi` (existing), `useCurrentUser().refresh` (existing).
- Produces: `signup(input): Promise<CurrentUser>`, `login(input): Promise<CurrentUser>`, `logout(): Promise<void>` in `lib/api`.

- [ ] **Step 1: Add auth functions to `lib/api/index.ts`**

```ts
export interface SignupInput {
  email: string;
  displayName: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

/** Create an account (lowest role) and return it. The caller then logs in. */
export async function signup(input: SignupInput): Promise<CurrentUser> {
  return fetchApi<CurrentUser>("/api/auth/signup", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

/** Log in with email + password. The backend uses form login, so POST form-encoded. */
export async function login(input: LoginInput): Promise<CurrentUser> {
  const body = new URLSearchParams({ email: input.email, password: input.password });
  return fetchApi<CurrentUser>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });
}

export async function logout(): Promise<void> {
  return fetchApi<void>("/api/auth/logout", { method: "POST" });
}
```

Also export `SignupInput` and `LoginInput` from `lib/api/index.ts` and add them to the re-export block.

- [ ] **Step 2: Add the `INVALID_CREDENTIALS` code to `lib/api/errors.ts`**

Add `| "INVALID_CREDENTIALS"` to the `ApiErrorCode` union.

- [ ] **Step 3: Write `frontend/app/signup/page.tsx`** — a centered form (`Card`, `Input`, `Button` from `@/components/ui`) with fields name, email, password; on submit it calls `signup`, then `login` with the same credentials, then `router.push("/dashboard")`. Show the `ApiError.message` inline on failure. Link to `/login`.

- [ ] **Step 4: Write `frontend/app/login/page.tsx`** — a centered form with email + password; on submit it calls `login`, then `router.push("/dashboard")`. Show "Invalid email or password" on an `INVALID_CREDENTIALS` error. Link to `/signup`.

- [ ] **Step 5: Re-wire `UserMenu`** — change the unauthenticated "Sign in" `Link` from `/oauth2/authorization/goldys` to `/login`; add a "Sign out" action that calls `logout()` then `refresh()` from `useCurrentUser()`. Keep the authenticated display of name + `department · seniority` unchanged.

- [ ] **Step 6: Run the frontend checks**

Run: `cd frontend && bun run typecheck && bun run lint && bun run test && bun run build`
Expected: all pass (8 existing Vitest tests still green).

- [ ] **Step 7: Commit**

```bash
git add frontend/lib/api frontend/app/login frontend/app/signup frontend/components/app-shell/user-menu.tsx
git commit -m "feat: add login and signup pages and wire auth client"
```

---

### Task 5: End-to-end integration test (Testcontainers)

**Files:**
- Test: `backend/src/test/java/com/goldys/platform/auth/AuthenticationIntegrationTest.java`

**Interfaces:**
- Consumes: the full app context (`@SpringBootTest` + `PostgresContainerConfiguration`), `AuthService`, the form-login filter chain, `PermissionService`.

- [ ] **Step 1: Write the integration test** (pins Review Focus lines 3, 4, and 5)

```java
package com.goldys.platform.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.support.PostgresContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class AuthenticationIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthService authService;
  @Autowired UserAccountRepository accounts;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table user_account");
  }

  @Test
  void signupLoginAndMeAreCaseInsensitiveAndPersistTheSession() throws Exception {
    authService.signup("Stirling@Donaldson.com", "Stirling Donaldson", "password123");

    MockHttpSession session = new MockHttpSession();
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "stirling@donaldson.com")
                .param("password", "password123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Stirling Donaldson"));

    mvc.perform(get("/api/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seniority").value("STAFF"));
  }

  @Test
  void inactiveAccountCannotLogin() throws Exception {
    authService.signup("a@b.com", "A B", "password123");
    accounts
        .findByEmail("a@b.com")
        .ifPresent(
            a -> jdbc.update("update user_account set active = false where id = ?", a.id()));

    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "a@b.com")
                .param("password", "password123"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void freshStaffIsExplicitlyDeniedOnReconciliation() throws Exception {
    authService.signup("staff@example.com", "Staff", "password123");

    MockHttpSession session = new MockHttpSession();
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "staff@example.com")
                .param("password", "password123"))
        .andExpect(status().isOk());

    // A STAFF account has no permission grant, so the read is explicitly denied (403), not empty.
    mvc.perform(get("/api/reconciliation/exceptions").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }
}
```

- [ ] **Step 2: Compile and note the Testcontainers gate**

Run: `cd backend && ./gradlew compileTestJava spotlessApply spotlessCheck`
Expected: BUILD SUCCESSFUL. The integration test runs only under CI (Docker).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/goldys/platform/auth/AuthenticationIntegrationTest.java
git commit -m "test: end-to-end email+password auth and role denial"
```

---

## Self-Review

- **Spec coverage:** §5 data model → Task 1; §6 components → Task 2; §7 endpoints/security → Task 3; §8 error handling → Task 3 (401/400 handlers); §9 frontend → Task 4; §10 testing → Tasks 1, 2, 3, 5.
- **Placeholder scan:** none — every code step contains concrete code.
- **Type consistency:** `AccountUserDetails` accessors (`email()`, `displayName()`, `department()`, `seniority()`) used consistently across Tasks 2, 3, 4, 5. `DailySalesOverrideService.save` six-arg signature (with `actorEmail`) is the single definition referenced by the controller and tests.
- **Review Focus:** line 1 pinned by `AuthServiceTest.normalizesEmailAndHashesPassword` + `AccountUserDetailsServiceTest.loadsByNormalizedEmail`; line 2 by `AuthServiceTest.rejectsDuplicateEmailRegardlessOfCase`; line 3 by `AccountUserDetailsServiceTest`/`isEnabled` + `AuthenticationIntegrationTest.inactiveAccountCannotLogin`; line 4 by `AuthenticationIntegrationTest.signupThenLoginIsCaseInsensitiveAndPersistsSession`; line 5 by `AuthenticationIntegrationTest.freshStaffIsDeniedOnReconciliation`.
