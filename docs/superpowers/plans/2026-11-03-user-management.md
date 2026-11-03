# Owner-Only User Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace public sign-up with owner-only account management for a single `owner` role, including forced first-login password change and immediate session revocation.

**Architecture:** An owner is the existing `ALL × OWNER` permission pair, so no role column or second identity table is added. A new `auth.UserManagementService` owns account CRUD behind a `users` permission resource; Spring's `SessionRegistry` ends live sessions on deactivation/reset; a security filter enforces the forced password change on the server.

**Tech Stack:** Spring Boot 3.5 / Java 25, PostgreSQL 16 + Flyway, Spring Security 6 (`SessionRegistry`, `ConcurrentSessionFilter`), Next.js + TypeScript + TanStack Query, Vitest + MSW.

**Spec:** `docs/superpowers/specs/2026-11-03-user-management-design.md`

## Global Constraints

- Branch: `feat/user-management` in worktree `.worktrees/user-management`.
- An owner is exactly `department='ALL'`, `seniority='OWNER'`. No other role and no new role column.
- Every protected read/write authorizes through `PermissionService.require(...)` in the service that owns the use case.
- Schema changes only through Flyway (`V100`); Hibernate stays `validate`.
- API errors use the existing `ApiErrorResponse` envelope.
- Every `Api` method needs a live and a demo implementation.
- Do not add dependencies.
- Conventional Commits, one task per commit.
- Backend: `./gradlew test spotlessCheck` from `backend/`. Frontend: `bun run typecheck && bun run lint && bun run test && bun run build` from `frontend/`.

## Review Focus

- A deactivated or password-reset user with a live session: every subsequent request must fail, not continue on the cached principal.
- A logged-in user with `mustChangePassword=true`: every endpoint except `/api/me`, `/api/auth/change-password`, `/api/auth/login`, `/api/auth/logout` and `/api/health` must return `403 PASSWORD_CHANGE_REQUIRED`, including direct API calls.
- Duplicate email in different case: must be rejected as a validation error, not a database 500.
- Self-deactivation and deactivating the last active owner: must be refused with a clear 400; the platform must never reach zero active owners.
- Unknown account id on update/reset, and missing/blank JSON fields: must return `400 VALIDATION_FAILED`, never a 500 or an empty success.
- Demo mode: create/rename/reset/deactivate must work against fixtures without a backend.

---

### Task 1: Account flag, principal field and migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V100__user_management.sql`
- Modify: `backend/src/main/java/com/goldys/platform/auth/UserAccount.java`
- Modify: `backend/src/main/java/com/goldys/platform/auth/AccountUserDetails.java`
- Modify: `backend/src/main/java/com/goldys/platform/auth/AccountUserDetailsService.java`
- Test: `backend/src/test/java/com/goldys/platform/auth/UserAccountRepositoryTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `UserAccount.mustChangePassword()`, `UserAccount.rename(String, Instant)`, `UserAccount.setActive(boolean, Instant)`, `UserAccount.setPassword(String, boolean, Instant)`, `UserAccount.clearMustChangePassword(Instant)`; `AccountUserDetails(..., boolean active, boolean mustChangePassword)` plus a 7-arg convenience constructor defaulting the flag to `false`.

- [ ] **Step 1: Write the failing repository test**

Add to `UserAccountRepositoryTest`:

```java
@Test
void persistsTheForcedPasswordChangeFlag() {
  UserAccount account =
      UserAccount.create("a@b.com", "hash", "A B", "ALL", "OWNER", Instant.now());
  account.setPassword("new-hash", true, Instant.now());
  repository.save(account);

  UserAccount stored = repository.findByEmail("a@b.com").orElseThrow();
  assertThat(stored.mustChangePassword()).isTrue();
  assertThat(stored.passwordHash()).isEqualTo("new-hash");
}
```

- [ ] **Step 2: Run it and confirm failure**

Run: `./gradlew test --tests 'com.goldys.platform.auth.UserAccountRepositoryTest'`
Expected: compilation failure — `setPassword` / `mustChangePassword` do not exist.

- [ ] **Step 3: Add the migration and model fields**

`V100__user_management.sql`:

```sql
ALTER TABLE user_account
    ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;

UPDATE user_account SET department = 'ALL', seniority = 'OWNER';

INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'users', true, true);
```

In `UserAccount`: add the `must_change_password` column field, a getter, and the four mutators from the Interfaces block. `setPassword(hash, mustChange, now)` sets both the hash and the flag; `clearMustChangePassword(now)` updates `updatedAt`.

In `AccountUserDetails`: append `boolean mustChangePassword` as the last record component and add the 7-arg secondary constructor delegating with `false`, so existing call sites compile. In `AccountUserDetailsService`, pass `account.mustChangePassword()`.

- [ ] **Step 4: Re-run the test**

Run: `./gradlew test --tests 'com.goldys.platform.auth.UserAccountRepositoryTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V100__user_management.sql \
  backend/src/main/java/com/goldys/platform/auth/UserAccount.java \
  backend/src/main/java/com/goldys/platform/auth/AccountUserDetails.java \
  backend/src/main/java/com/goldys/platform/auth/AccountUserDetailsService.java \
  backend/src/test/java/com/goldys/platform/auth/UserAccountRepositoryTest.java
git commit -m "feat(auth): add forced password change flag and owner promotion"
```

---

### Task 2: Self-service password change; remove sign-up

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/auth/AuthService.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/AuthController.java`
- Modify: `backend/src/main/java/com/goldys/platform/auth/StaffProfileSummary.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/CurrentUserController.java`
- Modify: `backend/src/main/java/com/goldys/platform/config/SecurityConfig.java` (permit-all and CSRF lists only)
- Create: `backend/src/test/java/com/goldys/platform/support/TestAccounts.java`
- Modify: `backend/src/test/java/com/goldys/platform/support/PostgresContainerConfiguration.java`
- Test/Modify: `backend/src/test/java/com/goldys/platform/auth/AuthServiceTest.java`
- Test/Modify: `backend/src/test/java/com/goldys/platform/api/AuthControllerTest.java`
- Test/Modify: `backend/src/test/java/com/goldys/platform/auth/AuthenticationIntegrationTest.java`
- Test/Modify: `backend/src/test/java/com/goldys/platform/api/ForecastingControllerIntegrationTest.java`
- Test/Modify: `backend/src/test/java/com/goldys/platform/classification/ReviewPermissionBoundaryTest.java`
- Test/Modify: `backend/src/test/java/com/goldys/platform/classification/ProductCategoryPermissionBoundaryTest.java`

**Interfaces:**
- Consumes: Task 1's entity and principal flag.
- Produces: `AuthService.changePassword(AccountUserDetails principal, String currentPassword, String newPassword): AccountUserDetails`; package-private statics `AuthService.normalizeEmail(String)`, `AuthService.validateEmail(String)`, `AuthService.validatePassword(String)`, `AuthService.validateDisplayName(String)`; `StaffProfileSummary(String displayName, String department, String seniority, boolean mustChangePassword)`; `TestAccounts.create(String email, String displayName, String password, String department, String seniority)` and `createOwner(...)` / `createStaff(...)`.

- [ ] **Step 1: Write failing change-password tests**

Replace the sign-up tests in `AuthServiceTest` with:

```java
@Test
void changePasswordRejectsTheWrongCurrentPassword() {
  UserAccount account =
      UserAccount.create("a@b.com", new BCryptPasswordEncoder().encode("password123"),
          "A B", "ALL", "OWNER", Instant.now());
  when(accounts.findById(account.id())).thenReturn(Optional.of(account));

  assertThatThrownBy(
          () -> service.changePassword(details(account), "wrong-password", "newpassword123"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Current password");
}

@Test
void changePasswordRejectsReuseAndClearsTheFlagOnSuccess() {
  BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  UserAccount account =
      UserAccount.create("a@b.com", encoder.encode("password123"), "A B", "ALL", "OWNER",
          Instant.now());
  account.setPassword(encoder.encode("password123"), true, Instant.now());
  when(accounts.findById(account.id())).thenReturn(Optional.of(account));

  assertThatThrownBy(
          () -> service.changePassword(details(account), "password123", "password123"))
      .isInstanceOf(IllegalArgumentException.class);

  AccountUserDetails refreshed =
      service.changePassword(details(account), "password123", "newpassword123");

  assertThat(refreshed.mustChangePassword()).isFalse();
  assertThat(encoder.matches("newpassword123", account.passwordHash())).isTrue();
}
```

`details(UserAccount)` is a private helper returning `AccountUserDetails` from the account.

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew test --tests 'com.goldys.platform.auth.AuthServiceTest'`
Expected: FAIL — `changePassword` does not exist.

- [ ] **Step 3: Implement change-password and remove sign-up**

- `AuthService`: delete `signup` and the `StaffProfileSummary` return. Keep and expose the validation helpers from the Interfaces block. Add `changePassword`: load by id, `passwordEncoder.matches(current, hash)` or throw `IllegalArgumentException("Current password is incorrect")`, validate the new password, reject `encoder.matches(new, hash)` with `"Choose a password you have not used"`, write the hash with `mustChange=false`, return the refreshed `AccountUserDetails`.
- `AuthController`: remove the sign-up mapping and record. Add `POST /change-password`, body record `ChangePasswordRequest(String currentPassword, String newPassword)`, returning `204`. Inject `SecurityContextRepository` and save the refreshed principal into the context so the session no longer reports the flag.
- `StaffProfileSummary`: add `mustChangePassword`. Update `CurrentUserController` and the login success handler in `SecurityConfig` to pass `user.mustChangePassword()`.
- `SecurityConfig`: remove `/api/auth/signup` from both the permit-all and CSRF-exempt matchers. Add `@Bean SecurityContextRepository securityContextRepository()` returning `HttpSessionSecurityContextRepository`, and wire it with `http.securityContext(sc -> sc.securityContextRepository(repo))`.
- `TestAccounts`: JDBC insert into `user_account` with `must_change_password = false`, encoding the password with the injected `PasswordEncoder`. Register it as a bean in `PostgresContainerConfiguration`.

- [ ] **Step 4: Replace every `authService.signup` test call**

Use `TestAccounts` (`createOwner`, `createStaff`, or `create` with a department/seniority pair) in the four integration test files listed above. Remove the now-redundant `update user_account set seniority = 'OWNER'` statements. Delete the sign-up tests from `AuthControllerTest` and replace them with a `204` delegation test for `POST /api/auth/change-password` using a mocked `AuthService` and `.with(authentication(...))` / `.with(csrf())`.

- [ ] **Step 5: Update `AuthenticationIntegrationTest`**

Update expectations from `STAFF` to the role the fixture created, and add:

```java
@Test
void meReportsTheForcedPasswordChangeFlag() throws Exception {
  testAccounts.create("new@example.com", "New", "password123", "ALL", "OWNER");
  jdbc.update("update user_account set must_change_password = true where email = 'new@example.com'");
  MockHttpSession session = login("new@example.com", "password123");
  mvc.perform(get("/api/me").session(session))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.mustChangePassword").value(true));
}
```

- [ ] **Step 6: Run the auth and boundary tests**

Run: `./gradlew test --tests 'com.goldys.platform.auth.*' --tests 'com.goldys.platform.api.AuthControllerTest' --tests 'com.goldys.platform.api.ForecastingControllerIntegrationTest' --tests 'com.goldys.platform.classification.*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/auth backend/src/main/java/com/goldys/platform/api/AuthController.java \
  backend/src/main/java/com/goldys/platform/api/CurrentUserController.java \
  backend/src/main/java/com/goldys/platform/config/SecurityConfig.java \
  backend/src/test/java/com/goldys/platform
git commit -m "feat(auth): replace public signup with self-service password change"
```

---

### Task 3: User management service

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/auth/ManagedUserSummary.java`
- Create: `backend/src/main/java/com/goldys/platform/auth/UserManagementService.java`
- Modify: `backend/src/main/java/com/goldys/platform/auth/UserAccountRepository.java`
- Test: `backend/src/test/java/com/goldys/platform/auth/UserManagementServiceTest.java`

**Interfaces:**
- Consumes: `AuthService` validation statics, `UserAccount` mutators, `PermissionService`.
- Produces:

```java
public record ManagedUserSummary(
    UUID id, String email, String displayName, boolean active, Instant createdAt, boolean self) {}

public List<ManagedUserSummary> list(UserRole actorRole, UUID actorId)
public ManagedUserSummary create(UserRole actorRole, UUID actorId, String email,
    String displayName, String password)
public ManagedUserSummary update(UserRole actorRole, UUID actorId, UUID targetId,
    String displayName, Boolean active)
public void resetPassword(UserRole actorRole, UUID targetId, String password)
```

- [ ] **Step 1: Write the failing service tests**

```java
class UserManagementServiceTest {
  // mocks: UserAccountRepository, PasswordEncoder, PermissionService, SessionRegistry
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void createNormalizesEmailAndCreatesAnOwnerAwaitingPasswordChange() {
    when(accounts.existsByEmail("a@b.com")).thenReturn(false);
    when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ManagedUserSummary created = service.create(OWNER, UUID.randomUUID(), "  A@B.com ", "A B", "password123");

    assertThat(created.email()).isEqualTo("a@b.com");
    assertThat(created.active()).isTrue();
    verify(permissions).require(OWNER, new ResourceKey("users"), PermissionAction.WRITE);
  }

  @Test
  void createRejectsADuplicateEmailRegardlessOfCase() {
    when(accounts.existsByEmail("a@b.com")).thenReturn(true);
    assertThatThrownBy(() -> service.create(OWNER, UUID.randomUUID(), "A@B.com", "A B", "password123"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateRefusesSelfDeactivation() {
    UUID actor = UUID.randomUUID();
    when(accounts.findById(actor)).thenReturn(Optional.of(activeAccount(actor)));
    assertThatThrownBy(() -> service.update(OWNER, actor, actor, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("your own account");
  }

  @Test
  void updateRefusesDeactivatingTheLastActiveOwner() {
    UUID actor = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    when(accounts.findById(target)).thenReturn(Optional.of(activeAccount(target)));
    when(accounts.countByDepartmentAndSeniorityAndActiveTrue("ALL", "OWNER")).thenReturn(1L);
    assertThatThrownBy(() -> service.update(OWNER, actor, target, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("last active owner");
  }

  @Test
  void deactivationAndResetRevokeLiveSessions() {
    UUID target = UUID.randomUUID();
    when(accounts.findById(target)).thenReturn(Optional.of(activeAccount(target)));
    when(accounts.countByDepartmentAndSeniorityAndActiveTrue("ALL", "OWNER")).thenReturn(2L);
    SessionInformation session = mock(SessionInformation.class);
    AccountUserDetails principal = principalFor(target);
    when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
    when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of(session));

    service.update(OWNER, UUID.randomUUID(), target, null, false);
    service.resetPassword(OWNER, target, "newpassword123");

    verify(session, times(2)).expireNow();
  }

  @Test
  void listAuthorizesRead() {
    when(accounts.findAllByOrderByEmailAsc()).thenReturn(List.of());
    service.list(OWNER, UUID.randomUUID());
    verify(permissions).require(OWNER, new ResourceKey("users"), PermissionAction.READ);
  }
}
```

Also assert that `create` sets `mustChangePassword=true`, `update` of an unknown id throws `IllegalArgumentException("Account not found")`, and a blank/absent update body throws.

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew test --tests 'com.goldys.platform.auth.UserManagementServiceTest'`
Expected: compilation failure — `UserManagementService` and `ManagedUserSummary` do not exist.

- [ ] **Step 3: Implement the service and repository methods**

- Repository: `List<UserAccount> findAllByOrderByEmailAsc()` and
  `long countByDepartmentAndSeniorityAndActiveTrue(String department, String seniority)`.
- `SecurityConfig`: add `@Bean SessionRegistry sessionRegistry()` returning `new SessionRegistryImpl()`, `@Bean HttpSessionEventPublisher httpSessionEventPublisher()`, and enable `sessionManagement(sm -> sm.maximumSessions(-1).sessionRegistry(sessionRegistry))` so logins are registered for revocation.
- `UserManagementService` constructor: `UserAccountRepository`, `PasswordEncoder`, `PermissionService`, `SessionRegistry`.
- `list` requires `users` READ; maps each account to `ManagedUserSummary` with `self = account.id().equals(actorId)`.
- `create` requires WRITE, validates email/name/password through the shared `AuthService` statics, rejects duplicates, saves `ALL × OWNER` with `mustChangePassword=true`, returns `self=false`.
- `update` requires WRITE, rejects an empty body (`displayName == null && active == null`) with `"Nothing to update"`, loads the target, applies self-deactivation and last-active-owner guards, updates the entity, and revokes sessions when `active` is false.
- `resetPassword` requires WRITE, validates the password, writes the hash with `mustChangePassword=true`, revokes sessions.
- Private `revokeSessions(UUID accountId)` iterates `sessionRegistry.getAllPrincipals()`, filters `AccountUserDetails` by id, and calls `expireNow()` on all sessions from `getAllSessions(principal, false)`.

- [ ] **Step 4: Run the service tests**

Run: `./gradlew test --tests 'com.goldys.platform.auth.UserManagementServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/auth/ManagedUserSummary.java \
  backend/src/main/java/com/goldys/platform/auth/UserManagementService.java \
  backend/src/main/java/com/goldys/platform/auth/UserAccountRepository.java \
  backend/src/test/java/com/goldys/platform/auth/UserManagementServiceTest.java
git commit -m "feat(users): add owner-only user management service"
```

---

### Task 4: User management API and permission boundary

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/api/UserManagementController.java`
- Modify: `backend/src/main/java/com/goldys/platform/api/CurrentUserController.java` (already carries the flag from Task 2; no change if done there)
- Test: `backend/src/test/java/com/goldys/platform/api/UserManagementControllerTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/UserManagementIntegrationTest.java`
- Test/Modify: `backend/src/test/java/com/goldys/platform/auth/PermissionBoundaryIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3 signatures and `CurrentUserService.roleOf(AccountUserDetails)`.
- Produces: the HTTP contract in the spec (`GET/POST /api/users`, `PATCH /api/users/{id}`, `POST /api/users/{id}/password-reset`).

- [ ] **Step 1: Write failing controller and integration tests**

`UserManagementControllerTest` (`@WebMvcTest(UserManagementController.class)`, mocked service and current-user service):

```java
@Test
void listReturnsUsers() throws Exception {
  when(currentUser.roleOf(any())).thenReturn(ownerRole());
  when(users.list(any(), any())).thenReturn(List.of(
      new ManagedUserSummary(UUID.randomUUID(), "a@b.com", "A B", true, Instant.parse("2026-11-03T00:00:00Z"), false)));

  mvc.perform(get("/api/users").with(authenticated(owner())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].email").value("a@b.com"))
      .andExpect(jsonPath("$[0].active").value(true));
}
```

Add tests for `POST` returning 201, `PATCH` returning 200, and `password-reset` returning 204, each verifying the service call and using `.with(csrf())` on mutations.

`UserManagementIntegrationTest` (`@SpringBootTest @AutoConfigureMockMvc`, Testcontainers, `TestAccounts`):
- anonymous `GET /api/users` → 401;
- an `ALL × STAFF` account → 403 `NOT_PERMITTED`;
- owner lists, creates (`201`), renames, deactivates (`200`), and the deactivated account cannot log in;
- duplicate email in different case → 400 `VALIDATION_FAILED`;
- unknown id on `PATCH` → 400 `VALIDATION_FAILED`.

Add to `PermissionBoundaryIntegrationTest.ownerCanReadAllNewDomainResources` a `users` READ/WRITE assertion.

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew test --tests 'com.goldys.platform.api.UserManagementControllerTest'`
Expected: compilation failure — controller does not exist.

- [ ] **Step 3: Implement the controller**

Thin adapter only: inject `UserManagementService` and `CurrentUserService`, take `@AuthenticationPrincipal AccountUserDetails user`, and delegate. Request records: `CreateUserRequest(String email, String displayName, String password)`, `UpdateUserRequest(String displayName, Boolean active)`, `ResetPasswordRequest(String password)`. No authorization or validation logic in the controller.

- [ ] **Step 4: Run the tests**

Run: `./gradlew test --tests 'com.goldys.platform.api.UserManagement*' --tests 'com.goldys.platform.auth.PermissionBoundaryIntegrationTest'`
Expected: PASS (Docker must be reachable for the integration test).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/api/UserManagementController.java \
  backend/src/test/java/com/goldys/platform/api/UserManagementControllerTest.java \
  backend/src/test/java/com/goldys/platform/api/UserManagementIntegrationTest.java \
  backend/src/test/java/com/goldys/platform/auth/PermissionBoundaryIntegrationTest.java
git commit -m "feat(users): expose owner-only user management API"
```

---

### Task 5: Session revocation wiring and forced-change filter

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/config/MustChangePasswordFilter.java`
- Modify: `backend/src/main/java/com/goldys/platform/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/goldys/platform/auth/ForcedPasswordChangeIntegrationTest.java`

**Interfaces:**
- Consumes: `AccountUserDetails.mustChangePassword()`, Task 3's session revocation.
- Produces: `403 PASSWORD_CHANGE_REQUIRED` on blocked requests; `SessionRegistry` bean; expired sessions after deactivation/reset.

- [ ] **Step 1: Write the failing integration test**

`ForcedPasswordChangeIntegrationTest`:
- create an owner via `TestAccounts`, then set `must_change_password = true` for that email with `JdbcTemplate`;
- log in (login itself must return 200 and `mustChangePassword: true`);
- `GET /api/users` → `403` with `$.code == "PASSWORD_CHANGE_REQUIRED"`;
- `POST /api/auth/change-password` with the current and a new password and `.with(csrf())` → `204`;
- `GET /api/me` on the same session → `mustChangePassword == false`;
- `GET /api/users` → `200`.

Second test with the same setup:
- log in, capture the principal from `sessionRegistry.getAllPrincipals()`;
- create a second owner and deactivate the first through `UserManagementService`;
- assert every `SessionInformation` for that principal is expired.

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew test --tests 'com.goldys.platform.auth.ForcedPasswordChangeIntegrationTest'`
Expected: FAIL — the blocked request returns 200 instead of 403.

- [ ] **Step 3: Implement the filter**

- `MustChangePasswordFilter extends OncePerRequestFilter`: when the authentication principal is `AccountUserDetails` with the flag, and the request path is not one of `/api/me`, `/api/auth/change-password`, `/api/auth/login`, `/api/auth/logout`, `/api/health`, write `403` JSON `ApiErrorResponse("PASSWORD_CHANGE_REQUIRED", "Change your password before continuing", correlationId, Map.of())`.
- `SecurityConfig`: add the filter after `AuthorizationFilter`. The `SessionRegistry` bean and `maximumSessions` wiring already exist from Task 3.

- [ ] **Step 4: Run the tests**

Run: `./gradlew test --tests 'com.goldys.platform.auth.ForcedPasswordChangeIntegrationTest' --tests 'com.goldys.platform.api.UserManagement*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/config/MustChangePasswordFilter.java \
  backend/src/main/java/com/goldys/platform/config/SecurityConfig.java \
  backend/src/test/java/com/goldys/platform/auth/ForcedPasswordChangeIntegrationTest.java
git commit -m "feat(auth): revoke sessions and enforce forced password change"
```

---

### Task 6: Frontend API contract

**Files:**
- Modify: `frontend/lib/api/auth-schema.ts`
- Modify: `frontend/lib/api/types.ts`
- Modify: `frontend/lib/api/index.ts`
- Modify: `frontend/lib/api/live.ts`
- Modify: `frontend/lib/api/demo.ts`
- Test: `frontend/lib/api/auth-schema.test.ts`
- Test: `frontend/lib/api/demo.test.ts`

**Interfaces:**
- Produces:

```ts
export interface ManagedUser {
  id: string; email: string; displayName: string; active: boolean;
  createdAt: string; self: boolean;
}
export interface CreateUserInput { email: string; displayName: string; password: string }
export interface UpdateUserInput { displayName?: string; active?: boolean }

listUsers(options?: { signal?: AbortSignal }): Promise<ManagedUser[]>;
createUser(input: CreateUserInput): Promise<ManagedUser>;
updateUser(id: string, input: UpdateUserInput): Promise<ManagedUser>;
resetUserPassword(id: string, password: string): Promise<void>;
```

- [ ] **Step 1: Write failing contract tests**

- `auth-schema.test.ts`: assert `currentUserSchema.parse({ displayName: "A", department: "ALL", seniority: "OWNER" }).mustChangePassword` is `false`, and that an explicit `true` is preserved.
- `demo.test.ts`: assert `demoApi.listUsers()` returns at least two fixtures, `createUser` adds one, `updateUser(id, {active:false})` flips it, and `resetUserPassword` resolves.

- [ ] **Step 2: Run and confirm failure**

Run: `bunx vitest run lib/api/auth-schema.test.ts lib/api/demo.test.ts`
Expected: FAIL — the methods/fields do not exist.

- [ ] **Step 3: Implement the contract**

- `auth-schema.ts`: add `mustChangePassword: z.boolean().default(false)`. The default keeps existing fixtures that omit it valid.
- `types.ts`: add the three interfaces, add the four methods to `Api`, and export the interfaces.
- `index.ts`: re-export the new types.
- `live.ts`: `fetchApi` calls — GET `/api/users`, POST `/api/users`, PATCH `/api/users/${id}`, POST `/api/users/${id}/password-reset`.
- `demo.ts`: a module-level `let demoUsers: ManagedUser[]` with three fixtures (one inactive), and implementations that mutate it and return copies. No backend calls.

- [ ] **Step 4: Run the contract tests**

Run: `bunx vitest run lib/api/auth-schema.test.ts lib/api/demo.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/api
git commit -m "feat(frontend): add user management API contract with demo fixtures"
```

---

### Task 7: Users settings screen

**Files:**
- Create: `frontend/app/(app)/settings/users/page.tsx`
- Create: `frontend/components/users/users-page-view.tsx`
- Modify: `frontend/app/(app)/settings/page.tsx`
- Test: `frontend/components/users/users-page-view.test.tsx`

**Interfaces:**
- Consumes: Task 6's `Api` methods and `ManagedUser`.
- Produces: `/settings/users` route and a link from `/settings`.

- [ ] **Step 1: Write the failing view tests**

Mock `@/lib/demo-mode` with a fake `useApi` returning a `listUsers` fixture and mutation spies. Assert:
- rows render name, email, `Owner`, `Active`/`Deactivated`;
- clicking `Add user` and submitting calls `createUser` with the form values;
- the row marked `self: true` has its `Deactivate` button disabled;
- a rejected mutation renders its message in an alert.

- [ ] **Step 2: Run and confirm failure**

Run: `bunx vitest run components/users/users-page-view.test.tsx`
Expected: FAIL — the component does not exist.

- [ ] **Step 3: Implement the screen**

- `page.tsx`: `"use client"`; use `useApi()` and `useQuery({ queryKey: ["listUsers"], queryFn: ({signal}) => api.listUsers({signal}) })`; render loading/error states with the existing components.
- `users-page-view.tsx`: `PageHeader` + `Table` (name, email, role `Owner`, status badge, actions). Dialogs for create, rename and reset password; a confirm dialog for deactivate/reactivate. Use the existing `Dialog`, `Table`, `Badge`, `Button`, `Input`, `Label` and `useToast`. After each successful mutation call `queryClient.invalidateQueries({ queryKey: ["listUsers"] })`.
- `settings/page.tsx`: replace the "Role and permission administration is not available yet" paragraph with a card/link to `/settings/users` titled "Users and access".

- [ ] **Step 4: Run the view tests**

Run: `bunx vitest run components/users/users-page-view.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/app/\(app\)/settings frontend/components/users
git commit -m "feat(frontend): add owner-only users settings screen"
```

---

### Task 8: Forced password change screen and routing

**Files:**
- Create: `frontend/app/(app)/change-password/page.tsx`
- Create: `frontend/components/auth/change-password-form.tsx`
- Modify: `frontend/lib/api/index.ts` (add `changePassword`)
- Modify: `frontend/components/app-shell/auth-gate.tsx`
- Modify: `frontend/app/login/page.tsx`
- Test: `frontend/components/auth/change-password-form.test.tsx`
- Test/Modify: `frontend/app/login/login-page.test.tsx`
- Test/Modify: `frontend/components/app-shell/auth-gate.test.tsx`

**Interfaces:**
- Consumes: `CurrentUser.mustChangePassword` from Task 6.
- Produces: `changePassword({ currentPassword, newPassword }): Promise<void>` and the `/change-password` route.

- [ ] **Step 1: Write failing tests**

- `change-password-form.test.tsx`: mock `changePassword`, `useCurrentUser().refresh` and `next/navigation`. Success calls `changePassword`, `refresh`, then `push("/dashboard")`; wrong current password shows the server message in an alert; mismatched confirmation blocks submission locally.
- `login-page.test.tsx`: a login response with `mustChangePassword: true` navigates to `/change-password`, not `nextPath`.
- `auth-gate.test.tsx`: an authenticated `/api/me` response with `mustChangePassword: true` on a normal path redirects to `/change-password`; on `/change-password` it does not redirect.

- [ ] **Step 2: Run and confirm failure**

Run: `bunx vitest run components/auth/change-password-form.test.tsx app/login/login-page.test.tsx components/app-shell/auth-gate.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implement the flow**

- `index.ts`: `changePassword` POSTs JSON to `/api/auth/change-password` with `responseType: "void"`.
- `change-password-form.tsx`: current password, new password, confirm; client checks match and minimum length; on success `refresh()` then `router.push("/dashboard")`; render `AuthFeedback` for errors.
- `page.tsx`: simple card wrapper with `PageHeader`.
- `auth-gate.tsx`: in the existing effect, if `status === "authenticated" && user?.mustChangePassword && pathname !== "/change-password"`, `router.replace("/change-password")`; return the checking state until the redirect happens.
- `login/page.tsx`: capture the `login` result and route a flagged user to `/change-password`.

- [ ] **Step 4: Run the tests**

Run: `bunx vitest run components/auth/change-password-form.test.tsx app/login/login-page.test.tsx components/app-shell/auth-gate.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/app/\(app\)/change-password frontend/components/auth/change-password-form.tsx \
  frontend/lib/api/index.ts frontend/components/app-shell/auth-gate.tsx frontend/app/login/page.tsx \
  frontend/components/auth/change-password-form.test.tsx frontend/app/login/login-page.test.tsx \
  frontend/components/app-shell/auth-gate.test.tsx
git commit -m "feat(frontend): add forced password change flow"
```

---

### Task 9: Remove frontend sign-up

**Files:**
- Delete: `frontend/app/signup/page.tsx`
- Delete: `frontend/app/signup/signup-recovery.tsx`
- Delete: `frontend/app/signup/signup-page.test.tsx`
- Modify: `frontend/app/login/page.tsx` (remove the sign-up link)
- Modify: `frontend/lib/api/index.ts` (remove `signup`, `SignupInput`)
- Test/Modify: `frontend/lib/api/index.test.ts`

**Interfaces:**
- Consumes: Task 2 removed the backend endpoint.
- Produces: no sign-up route, no `signup` API function.

- [ ] **Step 1: Remove the code and the sign-up tests**

Delete the three files, remove the `SignupInput`/`signup` function, remove the login page's "Sign up" paragraph/link, and delete the sign-up cases from `frontend/lib/api/index.test.ts`.

- [ ] **Step 2: Verify no references remain**

Run: `rg -n "signup|/signup" frontend --glob '!node_modules'`
Expected: no matches.

- [ ] **Step 3: Run the affected tests**

Run: `bunx vitest run lib/api/index.test.ts app/login/login-page.test.tsx`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add -A frontend/app/signup frontend/app/login/page.tsx frontend/lib/api/index.ts frontend/lib/api/index.test.ts
git commit -m "feat(frontend): remove public signup"
```

---

### Task 10: Living docs and full verification

**Files:**
- Modify: `docs/architecture/current-state.md`
- Modify: `docs/superpowers/README.md` (mark the plan link)

**Interfaces:**
- Consumes: everything above.
- Produces: documentation matching the shipped behaviour and recorded gate output.

- [ ] **Step 1: Update the living docs**

In `docs/architecture/current-state.md`:
- add `users` to the authorization resource list, granted to `ALL × OWNER` only;
- note that owner accounts are created through `/api/users`, public sign-up is removed, and a forced password change is enforced by a security filter;
- note that deactivation/reset expires live sessions.

In `docs/superpowers/README.md`, add the plan link to the 11-03 row.

- [ ] **Step 2: Run the full backend gate**

Run: `./gradlew test spotlessCheck` (from `backend/`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full frontend gate**

Run: `bun run typecheck && bun run lint && bun run test && bun run build` (from `frontend/`)
Expected: all commands exit 0.

- [ ] **Step 4: Record the rollout note**

Add one line to `docs/architecture/current-state.md`: the release deploys backend + migration before frontend; the restart clears old session principals, so existing users sign in again and receive `ALL × OWNER`.

- [ ] **Step 5: Commit**

```bash
git add docs/architecture/current-state.md docs/superpowers/README.md
git commit -m "docs: record owner-only user management"
```

---

## Execution Notes

- Integration tests need Docker (PostgreSQL 16 Testcontainers). Use `./gradlew fastTest` only for the unit loop.
- `AuthService.signup` is gone after Task 2; create test accounts with `TestAccounts`.
- The migration promotes every existing account to `ALL × OWNER`, including inactive ones. Do not add a conditional.
- Do not add a role picker, invitation email, hard delete, or audit log; they are explicitly out of scope.
