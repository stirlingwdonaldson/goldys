# User management with a single owner role

Date: 3 November 2026. Approved design for owner-only user management.

## Context

`user_account` already carries email, password hash, display name, `department`, `seniority` and
`active`. Permissions are granted to the `(department, seniority)` pair and every resource is
seeded for `ALL × OWNER` only. Public sign-up created accounts at `ALL × STAFF`, which grants
nothing, and the Settings page says role administration is not available.

There is no role-management screen, no way to deactivate a staff account, and no way to reset a
password. With a single owner role, every active account must be able to manage the others.

## Decisions

- An owner is the existing `ALL × OWNER` pair. No `role` column and no second identity table.
- Public sign-up is removed. Owners create accounts; there is no open registration path.
- Owners can list, create, rename, deactivate/reactivate and reset the password of any account.
  There is no hard delete.
- New and reset accounts receive a temporary password that must be changed at first login. There is
  no email infrastructure, so the owner communicates it out of band.
- Deactivating or resetting an account ends its live sessions immediately.
- An owner cannot deactivate themselves, and the last active owner cannot be deactivated.

## Data model

One migration, `V105__user_management.sql`:

```sql
ALTER TABLE user_account
    ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;

UPDATE user_account SET department = 'ALL', seniority = 'OWNER';

INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'users', true, true);
```

Every existing row is promoted, including inactive rows, so reactivating an old sign-up cannot
leave a `STAFF` account that sees nothing. The `users` resource gates all management endpoints:
`READ` for the list, `WRITE` for every mutation. No other role receives a grant.

`UserAccount` gains `mustChangePassword` plus package-private mutators for rename, active state,
password and clearing the flag. Flyway owns the schema; Hibernate still only validates it.

## Backend

### Components

- `auth/AuthService` — loses `signup`; keeps email normalization and password validation, and gains
  self-service `changePassword` (verify current, validate new, clear the flag).
- `auth/UserManagementService` — list, create, rename/activate, reset password, guardrails and
  session revocation. Every method authorizes through `PermissionService` on the caller's role.
- `api/UserManagementController` — thin adapter under `/api/users`.
- `api/AuthController` — drops the sign-up endpoint; the change-password endpoint lives here.
- `auth/AccountUserDetails` — carries `mustChangePassword`.
- `config/SecurityConfig` — removes sign-up from the permit-all and CSRF-exempt lists; registers a
  `SessionRegistry`, `HttpSessionEventPublisher`, unlimited concurrent sessions and the
  must-change filter.
- `config/MustChangePasswordFilter` — rejects requests from a principal with the flag, except
  `/api/me`, `/api/auth/change-password`, `/api/auth/login`, `/api/auth/logout` and `/api/health`,
  with `403 PASSWORD_CHANGE_REQUIRED`.

### API contract

| Endpoint | Gate | Behaviour |
|---|---|---|
| `GET /api/users` | `users` READ | List `{id, email, displayName, active, createdAt}` sorted by email. |
| `POST /api/users` | `users` WRITE | `{email, displayName, password}`; same validation as the removed sign-up; duplicate email is rejected; creates `ALL × OWNER` with `mustChangePassword=true`; `201` with the created summary. |
| `PATCH /api/users/{id}` | `users` WRITE | `{displayName?, active?}`; at least one field is required; rejects self-deactivation and deactivating the last active owner; deactivation revokes sessions. |
| `POST /api/users/{id}/password-reset` | `users` WRITE | `{password}`; stores the hash, sets `mustChangePassword=true`, revokes sessions; `204`. |
| `POST /api/auth/change-password` | authenticated | `{currentPassword, newPassword}`; verifies current, rejects reuse, applies the length rule, clears the flag, refreshes the session principal; `204`. |
| `GET /api/me` | authenticated | Adds `mustChangePassword`. |

Errors use the existing `ApiErrorResponse` envelope: `VALIDATION_FAILED` for invalid input and
guardrail violations, `NOT_PERMITTED` for a missing grant, `PASSWORD_CHANGE_REQUIRED` for the filter.
Login's success handler must include `mustChangePassword` so the client can route immediately.

### Session revocation

Add a `SessionRegistry` bean and `HttpSessionEventPublisher`, and enable session registration with
unlimited concurrency. On deactivation or password reset, expire every session whose principal has
the target account id. Without this, Spring's cached session principal keeps a deactivated user
authenticated until the session ends. A changed display name remains visible to that user until
their next login; that cosmetic staleness does not justify session surgery.

## Frontend

- `CurrentUser` gains `mustChangePassword` in the zod schema and every fixture.
- `Api` gains `listUsers`, `createUser`, `updateUser`, `resetUserPassword`, each with live and demo
  implementations. Demo mode keeps a module-level users fixture that create/update/reset mutate.
- `/settings/users` renders the management table with create, rename, reset-password and
  deactivate/reactivate actions using the existing Table, Dialog, Badge, Button and Toast
  components. Server errors are shown inline; the current user's own deactivate action is disabled.
- The Settings page replaces the "not available yet" text with a link to `/settings/users`; no new
  navigation group or sidebar item.
- `/change-password` lives inside `(app)` so the shell and current-user context are present.
  `AuthGate` redirects an authenticated flagged user there except when already on it. Success
  refreshes the current user and navigates to `/dashboard`.
- Login routes a flagged user to `/change-password` instead of the requested `nextPath`.
- Sign-up is deleted end to end: `app/signup/**`, its tests, the login page link, and all references
  in the API contract. `/signup` becomes a 404.

## Tests

Backend:

- `UserManagementServiceTest` — duplicate email, validation, guardrails, state changes.
- `AuthServiceTest` — change-password wrong current, short, reuse, success.
- `UserManagementIntegrationTest` (Testcontainers) — list/create/deactivate; anonymous 401; a
  non-owner role 403; `/api/me` flag; filter blocks then change succeeds; deactivation ends the
  session.
- `PermissionBoundaryIntegrationTest` — the `users` resource is seeded for `ALL × OWNER`.

Frontend:

- Users page: render, create, deactivate, reset, error display, own-row guard.
- Change-password page: success, wrong current password, reuse.
- Login redirect when flagged.
- Demo API mutations and the `mustChangePassword` schema.

## Rollout

- Deploy order is backend, migration, then frontend in the same release; the old frontend's sign-up
  link simply 404s until the new bundle lands.
- Existing sessions hold the old `STAFF` principal after promotion and lose access until the user
  signs in again. The deploy restart clears sessions, so no extra step is needed.
- No environment variables, schedules, connectors or production data changes.
- Gates: `./gradlew test spotlessCheck` and
  `bun run typecheck && bun run lint && bun run test && bun run build`.

## Out of scope

More roles, department-scoped grants, invitations/email delivery, hard delete, audit log of user
changes, and password recovery without an owner. Adding roles later means new permission rows and a
role picker, not reshaping `user_account`.
