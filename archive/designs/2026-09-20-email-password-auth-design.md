# Goldy's Email + Password Authentication

**Status:** Draft — for review
**Date:** 2026-09-20
**Scope:** Replace the OIDC (Google OAuth) login with a native Spring Security email + password flow and a self-service sign-up. The downstream permission model (`permission` grants keyed by `(department, seniority)`) is unchanged.

## 1. Objective

Give the venue's staff a self-service sign-up: a new user enters a name, an email
address, and a password, and lands with the **lowest access level** until an
administrator promotes them. This removes the dependency on an external OIDC
provider entirely and replaces the OIDC-keyed `staff_profile` with an
email-keyed `user_account`.

## 2. Sources of Truth and Current Baseline

- Architecture invariants and the permission model: `docs/system-context.md`.
- The auth layer being replaced: `staff_profile` (V3, keyed by `oidc_issuer` /
  `oidc_subject`), `StaffProfileService`, `StaffProfileRepository`, and the
  `SecurityConfig` OIDC branch (`spring-boot-starter-oauth2-client`,
  conditional `oauth2Login()`).
- The permission mechanism that stays: `permission` (V1) + `PermissionService`
  + `PermissionLookup`, keyed by `(department, seniority)`, with the V6 seed
  granting `ALL`/`OWNER` full access to `reconciliation.sales` and `connectors`.
- Frontend auth shell: `getCurrentUser()` → `GET /api/me`,
  `CurrentUserProvider`, and the `UserMenu` "Sign in" affordance that currently
  links to `/oauth2/authorization/goldys`.

## 3. Decisions

1. **Drop OIDC entirely.** No Google sign-in; an email address is a username,
   not an OAuth identity.
2. **Native Spring Security** (not betterAuth). Auth already lives in the Spring
   Boot backend; the permission model is auth-mechanism-agnostic; the features
   betterAuth would add (email verification, password reset) are not needed
   now.
3. **Self-service sign-up**, collecting **name, email, password** only. No bulk
   import and no pre-seeded profiles.
4. **Everyone defaults to the lowest role** (`ALL` / `STAFF`). There is no
   special "first user becomes admin" path.
5. **Administrator promotion is manual**: the owner edits the `user_account`
   row directly (e.g. `UPDATE user_account SET seniority = 'OWNER' WHERE
   email = ...`). No promotion code is built.
6. **No email verification.** The user base is a small, trusted set of venue
   managers using work addresses; an unverified account holds no permission
   grants, so a bogus sign-up has no access.

## 4. Scope

### In scope

- `user_account` table (email-keyed, password-hashed) replacing `staff_profile`.
- `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout`,
  and the existing `GET /api/me` re-keyed to the email principal.
- `SecurityConfig` reworked: remove OIDC, add email + password auth, keep CSRF
  and the webhook `permitAll`.
- Frontend `/login` and `/signup` pages, and the `UserMenu` re-wired to them.

### Out of scope

- Email verification, password reset/forgot-password, and account-lockout.
- A promotion/admin UI (promotion is a manual DB edit for now).
- Any change to the `permission` table, `PermissionService`, or the
  reconciliation/canonical layers.

## 5. Data model

`V7__user_accounts.sql` creates `user_account` and drops `staff_profile`:

| column | type | notes |
|---|---|---|
| `id` | uuid | PK |
| `email` | varchar(255) | unique, lower-case-normalized |
| `password_hash` | varchar(255) | BCrypt |
| `display_name` | varchar(255) | |
| `department` | varchar(100) | default `ALL` |
| `seniority` | varchar(100) | default `STAFF` |
| `active` | boolean | default true |
| `created_at` / `updated_at` | timestamptz | |

The `permission` table is untouched; `(ALL, OWNER)` from V6 remains the only
grant, so a fresh `STAFF` account has no access until promoted.

## 6. Backend components (package `auth`)

- `UserAccount` (entity, package-private) + `UserAccountRepository`
  (`findByEmail`).
- `AccountUserDetails implements UserDetails` — carries `email`, `passwordHash`,
  `displayName`, `department`, `seniority`, `active`; this is the request
  principal.
- `AccountUserDetailsService` — `loadUserByUsername(email)` from
  `UserAccountRepository`.
- `AuthService` — sign-up: validate a basic email format and a password of at
  least 8 characters, reject a duplicate email, BCrypt-hash, insert at
  `ALL`/`STAFF`, then authenticate the new account.
- `PasswordEncoder` bean (BCrypt).
- `CurrentUserService` re-keyed from `OidcUser` to the `AccountUserDetails`
  principal; `roleOf(Authentication)` builds `UserRole` from the principal's
  `department`/`seniority`.
- `StaffProfileService` / `StaffProfile` / `StaffProfileRepository` are removed;
  `StaffProfileSummary(displayName, department, seniority)` is retained as the
  public view and now sourced from `user_account`.

## 7. Endpoints and security config

| endpoint | access | behavior |
|---|---|---|
| `POST /api/auth/signup` | permitAll | `{email, displayName, password}` → create account, auto-login, return the user |
| `POST /api/auth/login` | permitAll | `{email, password}` → establish session, return the user |
| `POST /api/auth/logout` | authenticated | invalidate session |
| `GET /api/me` | authenticated | `{displayName, department, seniority}` |

- `SecurityConfig`: remove the `oauth2Login()` branch and the
  `ClientRegistrationRepository` plumbing; remove the
  `spring-boot-starter-oauth2-client` dependency.
- `permitAll`: `/api/health`, `/api/ingest/lightspeed`, `/api/auth/signup`,
  `/api/auth/login`. Everything else `authenticated()`.
- Signup and login are CSRF-exempt (unauthenticated, low risk); all
  authenticated mutating endpoints keep the cookie-based CSRF.
- All `@AuthenticationPrincipal OidcUser` usages become the email principal.

## 8. Error handling

- Wrong password or unknown email → `401` with the stable error envelope.
- Duplicate email at sign-up → `409` (or `400 VALIDATION_FAILED`).
- Malformed email / too-short password → `400 VALIDATION_FAILED`.

## 9. Frontend

- New `/login` and `/signup` routes (the shell already renders the
  unauthenticated state).
- `UserMenu`: "Sign in" links to `/login`; add a "Sign out" action.
- `lib/api` gains `signup`, `login`, `logout`; `getCurrentUser()` and
  `CurrentUserProvider` stay as-is.

## 10. Testing

- Unit: `AuthService` (password hashing, email normalization, duplicate
  rejection, validation).
- `@WebMvcTest`: signup and login happy path; wrong password → 401;
  duplicate email → 409; `/api/me` shape.
- Testcontainers integration: signup → login → `/api/me` → role resolution →
  `PermissionService` (a `STAFF` role is denied, a promoted `OWNER` role is
  allowed on `reconciliation.sales`).

## 11. Boundaries

### Always

- Route permission checks through the sole `PermissionService`; no owner bypass.
- Explicit denial (never a silently filtered result).
- Passwords only as BCrypt hashes; never store or log plaintext.

### Ask first

- New `permission` grants, department/seniority codes, or roles.
- Email verification, password reset, or account lockout.
- Any self-service change to department/seniority at sign-up.

### Never

- Hard-code credentials or a master password.
- Guess permission seed data.
- Log passwords or tokens.
