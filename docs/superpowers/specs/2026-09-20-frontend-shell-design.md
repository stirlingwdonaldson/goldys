# Goldy's Frontend Shell & Non-Intrusive Error States

**Status:** Draft — for review
**Date:** 2026-09-20
**Scope:** `frontend/` only. No backend, schema, or auth changes.

## 1. Objective

Build out the Phase 1 frontend from its current bare shell into a working
single-venue application: a shared navigation shell, current-staff identity,
and the four Phase 1 screens — Dashboard, Reconciliation, Connectors, Settings.
Every surface renders real backend contracts where they exist (`GET /api/health`,
`GET /api/me`) and, where a data contract does not exist yet, shows an honest,
calm "no data yet" state rather than fabricated operational data.

The frontend must "work and show non-intrusive errors": failures and missing
data are visible and recoverable, but never block the page, never show a blank
screen, and never present a partially-rendered or silently-filtered view of
protected content.

## 2. Sources of Truth and Current Baseline

Architecture decisions come from `docs/system-context.md`; scope and acceptance
criteria from `docs/prd.md`; visual/interaction treatment from
`docs/design-system.md`. The Phase 1 rebuild spec and foundation plan
(`docs/superpowers/specs/2026-09-19-phase-one-mvp-design.md`,
`docs/superpowers/plans/2026-09-19-phase-one-foundation.md`) define the backend
the frontend must render against.

Baseline facts that shape this design:

- The backend exposes exactly two HTTP endpoints: `GET /api/health` →
  `{status:"UP"}`, and `GET /api/me` → `{displayName, department, seniority}` or
  `403` with `code: NOT_PERMITTED`.
- All API errors share one envelope: `{code, message, correlationId, fields}`.
- The backend serves on `localhost:8080`; the frontend has no `/api` rewrite yet.
- The frontend is a bare shell: `app/layout.tsx`, a static `app/page.tsx`, and a
  `globals.css` with no shadcn CSS variables (so `hsl(var(--primary))` and the
  status tokens currently resolve to nothing). No `components/`, no `lib/`, no
  typed API client.
- `components.json` (slate, default style, CSS variables) and the `@/*` path
  alias are already configured. `tailwind.config.ts` already wires the
  `status-*` and `sidebar-*` color groups.

## 3. Scope

### In scope

- shadcn CSS-variable theme restoration plus semantic status and sidebar tokens.
- Shared application shell (sidebar navigation + header + current-user identity).
- Typed API client that understands the backend error envelope.
- Non-intrusive error system: inline error state, dismissible toast, explicit
  permission-denied state, and render error boundaries.
- Four screens (Dashboard, Reconciliation, Connectors, Settings) with loading,
  empty, error, and permission-denied states.
- Local-dev `/api` rewrite to the backend.

### Out of scope

- Any backend endpoint, migration, or schema change.
- Connectors, reconciliation rules, resolved views, or dashboard data (these are
  later vertical slices gated on stakeholder inputs).
- Fabricated operational data (mock sales, runs, conflicts).
- Conversational BI, Smart Exporter, Automation Hub (Phase 2).
- Component-test harness (Vitest/Testing Library) — deferred to the
  reconciliation vertical slice.
- Dark mode.

## 4. Architecture

Next.js App Router with server components for static layout and small client
components for interactive data. No data-fetching library — plain `fetch`
through a thin typed wrapper; SWR/react-query is added later only when real
revalidation needs justify it.

### 4.1 Routing

```text
app/
  layout.tsx          root <html>/<body>, metadata, globals.css
  page.tsx            redirect to /dashboard
  (app)/
    layout.tsx        authenticated shell: sidebar + header + providers + toast
    dashboard/page.tsx
    reconciliation/page.tsx
    connectors/page.tsx
    settings/page.tsx
```

The `(app)` layout renders the shell regardless of auth state; identity and
data errors surface contextually rather than hard-failing the whole app. This
is deliberate: the app must remain navigable and non-blocking when `/api/me` is
unavailable (e.g. local dev with no OIDC provider configured).

### 4.2 Components

```text
components/
  ui/                 shadcn primitives: button, card, badge, separator,
                      avatar, skeleton, tooltip, sidebar, sheet
  app-shell/          AppSidebar, AppHeader, UserMenu
  states/             EmptyState, ErrorState, PermissionDenied, LoadingState
  feedback/           ToastProvider, ToastViewport (dismissible toasts)
lib/
  utils.ts            cn()
  api/
    types.ts          CurrentUser, HealthResponse, ApiErrorResponse
    errors.ts         ApiError
    client.ts         fetchApi<T>()
    index.ts          getCurrentUser(), getHealth()
```

### 4.3 Typed API client

`fetchApi<T>` performs `fetch`, and on a non-2xx response parses the body as
`ApiErrorResponse` and throws `ApiError` carrying `code`, `message`,
`correlationId`, and `fields`. On a network failure it throws a synthesized
`ApiError` with `code: NETWORK_ERROR`. Callers never inspect raw HTTP status or
string-match error text; they branch on `ApiError.code`.

## 5. Error Handling (non-intrusive)

Three surfaces plus a boundary. None blocks navigation; none shows a blank page.

1. **Inline `ErrorState`** — a calm card with an icon, one-line message, the
   correlation id (for support), and a **Retry** button. Used for page/section
   fetch failures.
2. **Dismissible toast** — bottom-right, auto-dismissing, for transient action
   errors (e.g. a failed save). Never used for page-load failures.
3. **`PermissionDenied`** — an explicit lock icon + message naming what is
   restricted. Rendered when an API returns `NOT_PERMITTED`, and never as a
   greyed-out or partially-rendered version of real content.
4. **Render error boundary** — wraps each screen; a render bug becomes an
   inline "something went wrong" card with Retry, not a blank page.

`NOT_PERMITTED` and any `401`/`403` from `/api/me` map to a calm "not signed in"
state in the header (and "sign in to view data" on data screens) rather than a
loud error — this is the expected local-dev state until OIDC is configured.

## 6. Screens and States

Every screen implements four states via the shared components: **loading**
(skeleton), **empty**, **error** (with retry), and **permission-denied**.

- **Dashboard** — a stat-card grid for the PRD's leading indicators (ingestion
  completeness, open conflicts, time-to-detect failure, override usage). A
  reusable `StatCard` supports loading/empty/value. Today: empty ("no data yet").
- **Reconciliation** — exception-first list layout (card/badge, not a dense
  table). Today: empty ("No conflicts to review yet").
- **Connectors** — lists the four in-scope Phase 1 sources (Lightspeed, Cooking
  the Books, OpenTable, Deputy) as *configured sources*, each with a calm "No
  ingestion runs yet" status. This is factual scope, not invented run data; the
  layout is real and ready for the connector-status slice.
- **Settings** — shows the signed-in identity from `/api/me` and a note that
  role/permission administration is deferred.

No screen hard-codes a guessed field list, matching tolerances, or permission
seed data (the two gated PRD inputs remain unguessed).

## 7. Visual Tokens

Restore shadcn's default slate CSS variables under `:root` (background,
foreground, primary, secondary, muted, accent, destructive, card, popover,
border, input, ring, chart-1..5, radius) — unmodified from shadcn defaults.
Add the `design-system.md` §5 status tokens:

```css
--status-success: 142 71% 35%;
--status-success-foreground: 0 0% 100%;
--status-warning: 38 92% 50%;
--status-warning-foreground: 222.2 84% 4.9%;
--status-missing: 215 16% 65%;
--status-missing-foreground: 0 0% 100%;
```

Add the `--sidebar-*` tokens from `design-system.md` §5. Light mode only; the
dormant `.dark` block stays absent during Phase 1. `--destructive` remains
reserved for genuine system failures, not conflicts.

## 8. Dev Wiring

Add to `next.config.ts` a rewrite so the frontend's `/api/*` calls reach the
backend same-origin (session cookies work in dev):

```ts
async rewrites() {
  return [{ source: "/api/:path*", destination: "http://localhost:8080/api/:path*" }];
}
```

## 9. Verification

Install Bun 1.4.2 (`frontend/package.json` `packageManager`), then:

```bash
cd frontend && bun install --frozen-lockfile
bun run typecheck
bun run lint
bun run build
```

Then a browser pass over all four screens plus the loading/empty/error/
permission-denied states, and a manual check that `/api/me` and `/api/health`
round-trip through the rewrite. No backend tests change; `./gradlew test` is
unaffected and need not be re-run for frontend-only work.

## 10. File Structure

```text
frontend/
  app/
    layout.tsx
    page.tsx
    globals.css
    (app)/
      layout.tsx
      dashboard/page.tsx
      reconciliation/page.tsx
      connectors/page.tsx
      settings/page.tsx
  components/
    ui/
    app-shell/
    states/
    feedback/
  lib/
    utils.ts
    api/
  next.config.ts
```

## 11. Commit Discipline

Atomic Conventional Commits, one logical change each, passing typecheck/lint/
build where applicable. Examples:

```text
feat: restore shadcn theme and shared ui primitives
feat: add typed api client and error envelope
feat: build application shell and current-user identity
feat: add non-intrusive error and state components
feat: render phase one screens with honest empty states
```

Never stage the pre-existing unrelated untracked files (`.opencode/`, `data/`,
root `opencode.jsonc.backup.*`, root `package-lock.json`).

## 12. Boundaries

### Always

- Render backend contracts; never invent operational data.
- Return explicit missing-source and denied states, never blank or partial views.
- Route all fetches through the typed client; branch on `ApiError.code`.
- Keep light mode and shadcn defaults unmodified.

### Ask first

- Add production dependencies or change pinned framework versions.
- Change authentication/session architecture.
- Touch the backend, schema, or migrations.

### Never

- Fabricate reconciliation, run, or dashboard data and present it as real.
- Guess matching keys or field-to-role permission mappings.
- Store OIDC tokens in the browser.
- Introduce a data-fetching or test harness dependency without a stated need.
