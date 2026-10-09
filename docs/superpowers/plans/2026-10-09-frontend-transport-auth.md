# Frontend Transport and Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Execute only after human review and selection of an execution method.

**Goal:** Make the existing fetch transport and staff identity lifecycle reliable enough to support the subsequent scoped server-state migration.

**Architecture:** Preserve native fetch, the typed Api interface, demo/live adapters and Spring session authentication. Extend transport failures additively, make response-body expectations explicit, validate authentication responses at their trust boundary, and fence profile requests so stale identities cannot be republished. This is the first independently reviewable implementation PR, not an executable plan for all eleven migration slices.

**Tech Stack:** Next.js 15.5.25, React 19.3.0, TypeScript 5.9.3, Bun 1.4.2, Node 22.22.1, Vitest 5.0.1, existing Radix/shadcn and Sonner; proposed Zod 4.6.5 runtime and MSW 3.0.2 development additions.

**Spec:** [Approved frontend target architecture](../../frontend/target-architecture.md), specifically transport/failure model and identity freshness; scope is PR 1 of [migration roadmap](../../frontend/migration-plan.md). Source evidence and outstanding live limitations: [audit](../../frontend/audit.md), [verification](../../frontend/verification.md).

**Execution status:** Native execution approved and all seven tasks implemented on
`fix/frontend-transport-auth`. Fresh whole-branch review's important signup recovery finding
was reproduced and fixed; current verification and deferred limitations are in the linked
verification document. The checklists below preserve the approved execution instructions.

## Global Constraints

- Preserve Spring Boot API compatibility unless a coordinated contract change is explicitly necessary.
- Preserve all role and permission boundaries.
- Keep backend authorization authoritative.
- Preserve demo mode.
- Preserve correlation and provenance information.
- Do not silently replace missing or failed data with zeros.
- Do not conceal backend contract failures.
- Do not introduce broad global state management without evidence.
- Do not create redundant abstractions that simply wrap another dependency.
- Do not remove tests merely because the implementation changed.
- Do not merge major refactors without regression verification.
- Do not claim test or performance success without running the relevant checks.
- Keep native fetch, existing UI tokens/components and session form authentication; no backend authorization or endpoint changes in this PR.
- Query, RHF, Playwright, a global event bus and persisted authentication storage belong to later slices.
- Use Bun 1.4.2 and the frontend Bun lockfile; preserve the pre-existing untracked root package-lock.
- Run typecheck and build sequentially: both consume `.next/types`, and parallel execution caused an observed audit race.
- Do not push, merge, deploy, or delete infrastructure without explicit approval. Use a task-specific implementation branch; preserve unrelated user work.

## Review Focus

1. Valid error code/message with malformed optional fields: retain valid code, status and header correlation without trusting invalid fields (Task 3).
2. Cancellation after fetch resolves, during body consumption: propagate cancellation, not NETWORK_ERROR/UNPARSEABLE_RESPONSE, and never publish a profile (Tasks 3 and 5).
3. Followed redirects ending at unexpected or cross-origin pages: never accept HTML or infer sign-out from text containing “login” (Task 3).
4. Strict Mode or overlapping profile refreshes resolving out of order: obsolete requests cannot restore Owner presentation or overwrite newer results (Task 5).
5. Signup succeeds but auto-login fails: explain that the account exists and offer sign-in without repeating account creation (Task 6).

---

## Scope and acceptance

Reviewed audit commit: `d876f41`; product baseline: `f6140b8`. Revalidate the execution base before editing. Use an isolated workspace/task branch `fix/frontend-transport-auth` based on the approved audit/planning ancestry. Do not commit product changes to main or the planning branch.

Acceptance: structured available HTTP metadata; all HeadersInit forms; preserved CSRF/credentials; explicit JSON/void/no-content semantics; diagnosable malformed/redirected responses; only latest verified identity published; consumed login/signup/logout failures; no automatic write retries; retained existing tests plus MSW regressions enforced in CI; actual verification evidence. Mocked sessions do not certify live authentication.

Follow-on executable plans, after this PR's verification: scoped Query/Overview; dashboard library/actions; dashboard/widget schemas; editor; rules/reconciliation; explorer; remaining reads; streaming/conversations; demonstrated presentation consolidation; performance/observability/E2E completion. Each gets its own feature-specific spec/plan. This plan authorizes no later slice.

## File structure and ownership

All paths below are repository-relative. Test files sit beside their subjects, matching current conventions.

| File | Action / responsibility |
| --- | --- |
| `frontend/lib/api/errors.ts`, `errors.test.ts` | Extend errors additively; test legacy compatibility and diagnostics |
| `frontend/lib/api/client.ts`, `client.test.ts` | Native request/response policies; network/JSON/Headers/abort/redirect tests |
| `frontend/lib/api/auth-schema.ts`, `auth-schema.test.ts` | One runtime profile schema; inferred type and compatibility tests |
| `frontend/lib/api/types.ts` | Re-export inferred CurrentUser; correct obsolete OIDC comment |
| `frontend/lib/api/index.ts`, `index.test.ts` | Auth schema application, profile signal, explicit logout void contract; real-adapter tests |
| `frontend/lib/api/live.ts` | Explicit void contracts and reservation's permitted no-content result |
| `frontend/tests/api-server.ts` | Opt-in MSW server lifecycle and test-only browser-relative URL bridge |
| `frontend/vitest.config.mts` | Deterministic jsdom origin |
| `frontend/package.json`, `frontend/bun.lock` | Zod/MSW additions; review lock diff |
| `frontend/components/app-shell/current-user-provider.tsx` | Abort/generation-guarded verification and explicit identity clear |
| `frontend/components/app-shell/current-user-provider.test.tsx`, `current-user-provider.race.test.tsx` | HTTP states and noncooperative stale-request/StrictMode tests |
| `frontend/components/app-shell/user-menu.tsx`, `user-menu.test.tsx` | Logout states, profile retry and action feedback |
| `frontend/app/login/page.tsx`, `login-page.test.tsx` | Guarded form action, safe feedback and routing tests |
| `frontend/app/signup/page.tsx`, `signup-page.test.tsx` | Explicit partial signup and action tests |
| `.github/workflows/ci.yml` | Run frontend Vitest; pin compatible Node runtime |
| `docs/frontend/verification.md`, `docs/frontend/dependency-decisions.md` | Installed versions, results, intentional changes and rollback evidence |

Files ending `.test.ts`/`.test.tsx` and `auth-schema.ts`/`api-server.ts` are new. Other files are modifications. Existing global `frontend/tests/setup.ts` remains the Recharts/DOM setup; MSW is opt-in. Business components are not relocated. Chat's duplicate CSRF helper is consolidated with its streaming lifecycle in its own plan.

Trust boundaries relevant to this slice: HTTP JSON/envelopes and CSRF cookie → transport;
profile → role-sensitive presentation; async completion → current mounted identity. Assets
are session credentials and staff/business visibility. The tests below cover spoofed/malformed
Owner profiles, stale-request identity replacement, unsafe error disclosure and duplicate
auth actions. Backend permissions remain the authoritative enforcement point.

## Shared verification conventions

Commands run from `frontend/` unless marked repository-root. `bun run test <file>` invokes Vitest, not Bun's native runner. Observe a behavioral test fail before implementing its fix; preserve every existing test. Every implementation task has a reviewable/testable deliverable and its own commit.

Registry metadata inspected on 2026-10-09: Zod 4.6.5; MSW 3.0.2, Node >=22.12.0. These versions are proposed, not installed/tested. Official references: [Zod parsing/type inference](https://zod.dev/basics), [MSW Node integration](https://mswjs.io/docs/integrations/node/). Verify exact installed-major APIs before proceeding; no silent major upgrades to get tests green.

Node fetch needs absolute URLs while browser adapters correctly use relative `/api/...`. Set `test.environmentOptions.jsdom.url` to `http://localhost:3000` in Vitest config and use this test-only bridge:

```ts
// frontend/tests/api-server.ts
import { afterAll, afterEach, beforeAll, beforeEach, vi } from "vitest";
import { setupServer } from "msw/node";

export const server = setupServer();
export function useApiServer(): void {
  let interceptedFetch: typeof fetch;
  beforeAll(() => {
    server.listen({ onUnhandledRequest: "error" });
    interceptedFetch = globalThis.fetch;
  });
  beforeEach(() => {
    vi.stubGlobal("fetch", (input: RequestInfo | URL, init?: RequestInit) => {
      const resolved = typeof input === "string" && input.startsWith("/")
        ? new URL(input, window.location.origin) : input;
      return interceptedFetch(resolved, init);
    });
  });
  afterEach(() => {
    server.resetHandlers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    for (const cookie of document.cookie.split(";")) {
      document.cookie = `${cookie.split("=")[0].trim()}=; Max-Age=0; path=/`;
    }
  });
  afterAll(() => server.close());
}
```

Call useApiServer inside the HTTP describe block, not a file-wide scope that also mocks fetch. Capture MSW's intercepted fetch after listen; each bridge call must reach that captured implementation. Tests verify unhandled requests fail and never reach a real server. Do not change product transport URLs merely for Node tests.

## Task 1: Preserve structured failures without breaking callers

**Files:** Modify `frontend/lib/api/errors.ts`, `frontend/lib/api/index.ts`; create `frontend/lib/api/errors.test.ts`.

**Interfaces:** Retain `ApiError(code, message, correlationId?, fields?)` and `isApiError`; add optional fifth `details: ApiErrorDetails`. Types: `ApiErrorKind = "network" | "http" | "protocol" | "integrity" | "unexpected"`; `ApiDiagnostic = { path: readonly (string | number)[]; code: string }`; `ApiErrorDetails = { status?: number; kind?: ApiErrorKind; cause?: unknown; diagnostics?: readonly ApiDiagnostic[] }`. Export `isAbortError(error: unknown): boolean` through the existing API barrel. Error properties are readonly; diagnostics contain paths/codes, not values/body text.

- [ ] **Step 1: Add compatibility and metadata tests.**

```ts
import { expect, it } from "vitest";
import { ApiError, isAbortError, isApiError } from "./errors";

it("retains legacy construction", () => {
  const e = new ApiError("NOT_PERMITTED", "Denied", "req-1", { title: "Required" });
  expect(isApiError(e)).toBe(true);
  expect(e).toMatchObject({ code: "NOT_PERMITTED", correlationId: "req-1", fields: { title: "Required" } });
});
it("preserves cause, status and value-free diagnostics", () => {
  const cause = new SyntaxError("Invalid response JSON");
  const e = new ApiError("UNPARSEABLE_RESPONSE", "Could not read response", "req-2", undefined,
    { status: 502, kind: "protocol", cause, diagnostics: [{ path: ["fields"], code: "invalid_type" }] });
  expect(e).toMatchObject({ status: 502, kind: "protocol", diagnostics: [{ path: ["fields"], code: "invalid_type" }] });
  expect(e.cause).toBe(cause);
});
it("recognizes cancellation across realms", () => {
  expect(isAbortError(new DOMException("Cancelled", "AbortError"))).toBe(true);
  expect(isAbortError({ name: "AbortError" })).toBe(true);
  expect(isAbortError(new TypeError("offline"))).toBe(false);
  expect(isAbortError(null)).toBe(false);
});
```

- [ ] **Step 2:** Run `bun run test lib/api/errors.test.ts`; new metadata/predicate tests must fail. Record actual output.
- [ ] **Step 3: Implement additive metadata and cancellation predicate.** Use `super(message, { cause: details.cause })`, keep the first four arguments, assign `fields ?? {}`, `kind ?? "unexpected"`, `diagnostics ?? []`. NETWORK_ERROR has undefined status. Add documented client codes REQUEST_CONFIGURATION_ERROR, AUTH_REQUIRED, UNEXPECTED_REDIRECT, UNEXPECTED_CONTENT_TYPE, INVALID_RESPONSE; keep code open-ended.

```ts
export function isAbortError(error: unknown): boolean {
  return typeof error === "object" && error !== null &&
    "name" in error && error.name === "AbortError";
}
// index.ts re-export: export { ApiError, isApiError, isAbortError } from "./errors";
```

- [ ] **Step 4:** Run focused tests and `bun run typecheck`; old constructors and type exports must compile.
- [ ] **Step 5:** Stage only Task 1 files and commit `refactor: preserve structured API failure metadata`.

## Task 2: Correct Headers, CSRF, credentials and request cancellation

**Files:** Modify `frontend/lib/api/client.ts`; create `frontend/lib/api/client.test.ts` (request describe first).

**Interfaces:** Consume Task 1 errors. Preserve `fetchApi<T>(path: string, init?: RequestInit): Promise<T>`. Forward native RequestInit.signal unchanged; response policies arrive in Task 3.

- [ ] **Step 1: Add failing request tests with restored mocks/cookies.**

```ts
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchApi } from "./client";

describe("API request construction", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  });
  it.each([
    { name: "record", headers: { "X-Feature": "dashboard", Accept: "application/vnd.goldys+json" } },
    { name: "Headers", headers: new Headers({ "X-Feature": "dashboard", Accept: "application/vnd.goldys+json" }) },
    { name: "tuples", headers: [["X-Feature", "dashboard"], ["Accept", "application/vnd.goldys+json"]] },
  ] satisfies { name: string; headers: HeadersInit }[])("retains $name headers", async ({ headers }) => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ ok: true }));
    vi.stubGlobal("fetch", fetch);
    await fetchApi("/api/fixture", { headers });
    const sent = new Headers(fetch.mock.calls[0][1].headers);
    expect(sent.get("x-feature")).toBe("dashboard");
    expect(sent.get("accept")).toBe("application/vnd.goldys+json");
  });
  it("preserves decoded CSRF, signal and same-origin credentials", async () => {
    document.cookie = "XSRF-TOKEN=fixture%2Bcsrf; path=/";
    const controller = new AbortController();
    const fetch = vi.fn().mockResolvedValue(Response.json({ ok: true }));
    vi.stubGlobal("fetch", fetch);
    await fetchApi("/api/fixture", { method: "POST", signal: controller.signal });
    expect(new Headers(fetch.mock.calls[0][1].headers).get("x-xsrf-token")).toBe("fixture+csrf");
    expect(fetch.mock.calls[0][1]).toMatchObject({ signal: controller.signal, credentials: "same-origin" });
  });
  it("propagates fetch cancellation unchanged", async () => {
    const aborted = new DOMException("Cancelled", "AbortError");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(aborted));
    await expect(fetchApi("/api/fixture")).rejects.toBe(aborted);
  });
  it("keeps network cause without exposing its message", async () => {
    const cause = new TypeError("fixture-internal-detail");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(cause));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ code: "NETWORK_ERROR", kind: "network", cause });
  });
});
```

Add `it.each(["GET", "HEAD"])` asserting no CSRF; `it.each(["POST", "PUT", "PATCH", "DELETE"])` asserting CSRF; missing-cookie writes omit it. Malformed-percent cookie and invalid-header-name tests expect REQUEST_CONFIGURATION_ERROR with cause, zero fetch calls and no token value in public message. FormData tests assert no manually added Content-Type.

- [ ] **Step 2:** Run `bun run test lib/api/client.test.ts`; observe Headers/abort/cause failures.
- [ ] **Step 3: Separate request configuration errors from fetch/network errors.**

```ts
const headers = new Headers(init?.headers);
if (!headers.has("Accept")) headers.set("Accept", "application/json");
const method = (init?.method ?? "GET").toUpperCase();
if (method !== "GET" && method !== "HEAD") {
  const token = csrfToken();
  if (token !== null) headers.set("X-XSRF-TOKEN", token);
}
// Header/cookie construction catch -> REQUEST_CONFIGURATION_ERROR, kind unexpected, cause.
// Distinct fetch catch -> rethrow AbortError; otherwise NETWORK_ERROR, kind network, cause.
// fetch(path, { ...init, headers, credentials: "same-origin" }) preserves the signal.
```

Do not decode CSRF on GET/HEAD or globally add JSON Content-Type. Preserve caller Accept and multipart boundary generation.

- [ ] **Step 4:** Run focused tests/typecheck; inspect all fetchApi callers for assumptions about plain-record headers.
- [ ] **Step 5:** Stage Task 2 files and commit `fix: preserve API headers and cancellation semantics`.

## Task 3: Explicit response contracts and MSW regression boundary

**Files:** Modify client.ts/client.test.ts, live.ts, index.ts, package.json/bun.lock, vitest.config.mts; create tests/api-server.ts. All frontend paths are listed in the structure table.

**Interfaces:** Consume Tasks 1–2. Export response options/overloads in client.ts; import Zod types only, letting callers own runtime schemas:

```ts
import type { ZodType } from "zod";
export interface JsonResponseOptions<T> { responseType?: "json"; schema?: ZodType<T>; }
export interface OptionalJsonResponseOptions<T> extends JsonResponseOptions<T> { allowNoContent: true; }
export interface VoidResponseOptions { responseType: "void"; }
export function fetchApi<T>(path: string, init: RequestInit | undefined, options: OptionalJsonResponseOptions<T>): Promise<T | undefined>;
export function fetchApi<T>(path: string, init?: RequestInit, options?: JsonResponseOptions<T>): Promise<T>;
export function fetchApi<T extends void = void>(path: string, init: RequestInit | undefined, options: VoidResponseOptions): Promise<T>;
// Implementation accepts the union of options and returns Promise<unknown>.
// Default absent options to { responseType: "json" }; handle/narrow void mode before schema access.
```

Optional JSON permits **204 only**, not empty 200. Void permits 204 and empty successful bodies; nonempty valid JSON acknowledgements may be parsed/discarded, but nonempty malformed/HTML bodies fail. Required JSON rejects 204/empty success. Erased generic `<void>` is not a runtime policy.

- [ ] **Step 1: Install declared additions with scripts disabled; incorporate test setup in this deliverable.**

```bash
bun add --exact --ignore-scripts zod@4.6.5
bun add --dev --exact --ignore-scripts msw@3.0.2
bun install --frozen-lockfile --ignore-scripts
```

Neither inspected package metadata declares install/postinstall scripts. Inspect pending transitive scripts before enabling any; no blanket approval. Add opt-in useApiServer above and deterministic jsdom origin. Preserve existing tests/setup.ts and checks. Lock diff contains only justified dependency changes.

- [ ] **Step 2: Add HTTP response tests in a dedicated describe using MSW.**

```ts
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { fetchApi } from "./client";
import { server, useApiServer } from "@/tests/api-server";
const endpoint = "http://localhost:3000/api/fixture";

describe("API response boundary", () => {
  useApiServer();
  it("salvages valid code/status/correlation without trusting malformed metadata", async () => {
    server.use(http.get(endpoint, () => HttpResponse.json({
      code: "OVERRIDE_CONFLICT", message: "Fixture conflict", fields: ["invalid"], correlationId: 42,
    }, { status: 409, headers: { "X-Correlation-ID": "req-header" } })));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({
      code: "OVERRIDE_CONFLICT", status: 409, correlationId: "req-header", fields: {},
      diagnostics: expect.arrayContaining([{ path: ["fields"], code: "invalid_type" }]),
    });
  });
  it.each([200, 204])("accepts empty %i in void mode", async status => {
    server.use(http.delete(endpoint, () => new HttpResponse(null, { status })));
    await expect(fetchApi<void>("/api/fixture", { method: "DELETE" }, { responseType: "void" })).resolves.toBeUndefined();
  });
  it.each([200, 204])("rejects empty %i in required JSON mode", async status => {
    server.use(http.get(endpoint, () => new HttpResponse(null, { status })));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ code: "UNPARSEABLE_RESPONSE", status, kind: "protocol" });
  });
  it("permits explicit optional-JSON 204", async () => {
    server.use(http.get(endpoint, () => new HttpResponse(null, { status: 204 })));
    await expect(fetchApi("/api/fixture", undefined, { allowNoContent: true })).resolves.toBeUndefined();
  });
  it.each([
    ["http://localhost:3000/login", "AUTH_REQUIRED"],
    ["http://localhost:3000/unexpected", "UNEXPECTED_REDIRECT"],
    ["https://external.invalid/login", "UNEXPECTED_REDIRECT"],
  ])("classifies only known same-origin auth redirects: %s", async (url, code) => {
    const response = new Response("<html>fixture login</html>", { status: 200, headers: { "Content-Type": "text/html", "X-Correlation-ID": "req-final" } });
    Object.defineProperties(response, { redirected: { value: true }, url: { value: url } });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(fetchApi("/api/fixture")).rejects.toMatchObject({ code, status: 200, correlationId: "req-final" });
  });
  it("propagates body-read cancellation", async () => {
    const aborted = new DOMException("Cancelled", "AbortError");
    const response = Response.json({ fixture: true });
    vi.spyOn(response, "text").mockRejectedValue(aborted);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
    await expect(fetchApi("/api/fixture")).rejects.toBe(aborted);
  });
});
```

Add concrete parameterized cases, keeping the same actual fetch path:

- 201 JSON object and application/problem+json/vendor +json media types succeed.
- Void nonempty valid JSON succeeds; void HTML fails UNEXPECTED_CONTENT_TYPE.
- Explicit text/html with JSON-looking content fails; absent Content-Type plus valid JSON remains readable for backwards compatibility.
- Malformed JSON at 200/400/500 retains status/header correlation and SyntaxError cause; non-2xx absent code/message yields UNPARSEABLE_RESPONSE.
- Valid envelopes at 400/401/403/409/503 retain valid code/fields; nullable metadata means absent; valid nonempty envelope correlation wins over header, malformed correlation falls back with a diagnostic.
- 500 message `fixture-secret-do-not-display` and unknown-code messages never appear in the public message; valid backend code still retained.
- Opaque/manual redirect produces UNEXPECTED_REDIRECT and undefined status, not invented HTTP 0.

- [ ] **Step 3:** Run client tests; new response policy/status/metadata cases must fail. Deliberately verify an unhandled fixture URL fails MSW rather than contacting localhost.
- [ ] **Step 4: Implement one body-read path and optional schema validation.** Read status/correlation first; diagnose redirects before body acceptance; read text once. Rethrow aborts from body consumption. Other read failures are safe protocol errors with status/correlation/cause. Parse JSON separately to retain SyntaxError.

```ts
// Apply only when a schema is explicitly supplied, after successful JSON parsing:
if (options.schema) {
  const result = options.schema.safeParse(parsed);
  if (!result.success) {
    throw new ApiError("INVALID_RESPONSE", "The server returned invalid data.", correlationId, undefined, {
      status, kind: "integrity", cause: result.error,
      diagnostics: result.error.issues.map(issue => ({
        path: issue.path.map(part => typeof part === "number" ? part : String(part)),
        code: issue.code,
      })),
    });
  }
  return result.data;
}
return parsed;
```

Envelope core must be a nonarray object with string code/message. Validate optional fields independently: only string-valued records enter fields; correlation is a nonempty string or header fallback; missing/null metadata is allowed. Malformed optional metadata records property-path diagnostics without erasing valid code. Do not retain raw envelopes/body values on the error. Safe fixed messages for INVALID_CREDENTIALS, NOT_PERMITTED, VALIDATION_FAILED, OVERRIDE_CONFLICT, RECOMPUTATION_PENDING; generic safe message for unknown codes/5xx. Causes remain available for diagnosis but must not later be serialized into telemetry without sanitization.

AUTH_REQUIRED recognition: original request is an API path; followed final URL is same origin with exact `/login` or prefixes `/oauth2/`, `/login/oauth2/`. All other redirects are UNEXPECTED_REDIRECT, including redirected JSON. Followed fetch exposes final status/headers only; the hidden original 302/status/correlation must not be fabricated. No text-search authentication heuristics.

- [ ] **Step 5: Update all permitted no-content callers and prove their actual adapter behavior.**

```ts
// index.ts logout; live.ts upload/rule/dashboard/thread delete:
fetchApi<void>(path, init, { responseType: "void" });
// live.ts getReservationSummary:
fetchApi<ReservationSummary>(path, undefined, { allowNoContent: true });
```

Exact live methods: uploadOpenTableCsv, deleteResolutionRule, deleteDashboard, deleteThread. Test these real liveApi methods against empty 200/204 handlers; reservation summary against 204/valid JSON/500. A reservation failure never resolves undefined. Inspect controllers: browser CSV is ConnectorStatusController (204), not public OpenTableCsvIngestController webhook; dashboard/thread void controller deletes may produce empty 200. Preserve FormData and unchanged paths/methods. Add these cases to client.test.ts rather than mocking Api methods.

- [ ] **Step 6:** Run client tests, `lib/api/demo.test.ts`, full `bun run test`, then typecheck. Existing code-first feature branches must remain compatible.
- [ ] **Step 7:** Commit setup/response behavior/caller changes/tests together: `fix: enforce API response contracts with integration coverage`.

## Task 4: Validate staff identity at authentication boundaries

**Files:** Create auth-schema.ts/auth-schema.test.ts/index.test.ts; modify index.ts/types.ts.

**Interfaces:** Consume Task 3 schema-enabled fetch. Produce `currentUserSchema` and inferred `CurrentUser`; retain type exports from both API barrel and types.ts. Additive profile signature: `getCurrentUser(options?: { signal?: AbortSignal }): Promise<CurrentUser>`. Login/signup signatures and wire bodies stay unchanged.

- [ ] **Step 1: Write schema and real-adapter auth tests.**

```ts
import { expect, it } from "vitest";
import { currentUserSchema } from "./auth-schema";

it("accepts Spring StaffProfileSummary without inventing id", () => {
  expect(currentUserSchema.parse({ displayName: "Fixture owner", department: "MANAGEMENT", seniority: "OWNER" }))
    .toEqual({ displayName: "Fixture owner", department: "MANAGEMENT", seniority: "OWNER" });
});
it.each([null, [], {}, { displayName: 42, department: "MANAGEMENT", seniority: "OWNER" },
  { displayName: "Fixture owner", department: "MANAGEMENT" }])("rejects malformed profile %#", value => {
  expect(currentUserSchema.safeParse(value).success).toBe(false);
});
it("permits future backend role strings", () => {
  expect(currentUserSchema.safeParse({ displayName: "Fixture", department: "NEW_DEPARTMENT", seniority: "NEW_ROLE" }).success).toBe(true);
});
```

Index MSW tests: profile AbortSignal forwarding; malformed success rejects INVALID_RESPONSE with status/correlation/`seniority` diagnostic; login POST form encoding preserves `+`, `&`, Unicode; signup POST JSON accepts 201; 401 INVALID_CREDENTIALS stays typed; logout POST CSRF accepts 204. Assert synthetic request bodies only, never log them. Fixture email `fixture+staff@example.invalid`, password `fixture-&-only`; no production data. Extra profile fields are discarded without rejecting additive backend changes.

- [ ] **Step 2:** Run schema/index tests; observe missing schema/validation/signal forwarding failures.
- [ ] **Step 3: Implement one schema/type source and apply at all three profile-returning endpoints.**

```ts
// auth-schema.ts
import { z } from "zod";
export const currentUserSchema = z.object({
  displayName: z.string(), department: z.string(), seniority: z.string(),
});
export type CurrentUser = z.infer<typeof currentUserSchema>;

// types.ts: replace only the handwritten CurrentUser interface with a type re-export.
export type { CurrentUser } from "./auth-schema";

// index.ts: runtime import currentUserSchema; existing CurrentUser type import/export retained.
export async function getCurrentUser(options?: { signal?: AbortSignal }): Promise<CurrentUser> {
  return fetchApi<CurrentUser>("/api/me", { signal: options?.signal }, { schema: currentUserSchema });
}
// login/signup fetch calls append { schema: currentUserSchema } as third argument.
```

Do not coerce/default/trim identity fields, require a nonexistent subject id, or impose a guessed role enum. Correct OIDC comments to session authentication. Compare with StaffProfileSummary.java, CurrentUserController.java, AuthController signup 201 and SecurityConfig login 200/401/logout 204.

- [ ] **Step 4:** Run focused tests/typecheck; all existing CurrentUser imports must compile.
- [ ] **Step 5:** Commit `fix: validate authentication response identities`.

## Task 5: Fence profile refreshes and consume logout failures

**Files:** Modify current-user-provider.tsx/user-menu.tsx; create current-user-provider.test.tsx/current-user-provider.race.test.tsx/user-menu.test.tsx.

**Interfaces:** Consume getCurrentUser({ signal }), ApiError metadata/isAbortError and existing useToast. Retain AuthStatus and refresh(): void; add clear(): void to CurrentUserContextValue. clear aborts/invalidates prior verification and sets user=null/status=unauthenticated/error=null. No Query/session-scope infrastructure yet.

- [ ] **Step 1: Add real-adapter provider tests.**

```tsx
import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { expect, it } from "vitest";
import { CurrentUserProvider, useCurrentUser } from "./current-user-provider";
import { server, useApiServer } from "@/tests/api-server";
useApiServer();
const me = "http://localhost:3000/api/me";
const owner = { displayName: "Fixture owner", department: "MANAGEMENT", seniority: "OWNER" };

it.each([401, 403, 500])("clears old identity on failed %i refresh", async status => {
  server.use(http.get(me, () => HttpResponse.json(owner)));
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  await waitFor(() => expect(result.current.status).toBe("authenticated"));
  server.use(http.get(me, () => HttpResponse.json({ code: status === 403 ? "NOT_PERMITTED" : "UNEXPECTED_STATUS", message: "fixture" }, { status })));
  act(() => result.current.refresh());
  expect(result.current.user).toBeNull();
  await waitFor(() => expect(result.current.status).toBe(status === 401 ? "unauthenticated" : "error"));
  expect(result.current.user).toBeNull();
});
it("treats malformed 200 profile as an integrity error, not sign-out", async () => {
  server.use(http.get(me, () => HttpResponse.json({ seniority: "OWNER" })));
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  await waitFor(() => expect(result.current.status).toBe("error"));
  expect(result.current.error).toMatchObject({ code: "INVALID_RESPONSE", status: 200 });
  expect(result.current.user).toBeNull();
});
```

For noncooperative late results use the separate race test file. Mock getCurrentUser only, retaining actual API exports via `vi.importActual`; use this deferred helper:

```ts
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
```

Two pending profile results: mount starts old request; refresh starts new; resolve new with non-Owner then old with Owner. Assert non-Owner remains and old supplied signal aborted. Repeat with old rejection, clear before old resolution, unmount and StrictMode double mount. Assert no stale error/identity. Also test AUTH_REQUIRED→unauthenticated; unexpected redirect/network/403→error. These tests must fail even if the mocked promises ignore AbortSignal.

- [ ] **Step 2:** Run provider/menu tests; observe retained-user/race/action errors before changing implementation.
- [ ] **Step 3: Implement latest-request ownership.**

```tsx
const generation = useRef(0);
const active = useRef<AbortController | null>(null);
const refresh = useCallback(() => {
  const current = ++generation.current;
  active.current?.abort();
  const controller = new AbortController();
  active.current = controller;
  setUser(null);
  setStatus("loading");
  setError(null);
  void getCurrentUser({ signal: controller.signal }).then(profile => {
    if (current !== generation.current) return;
    setUser(profile);
    setStatus("authenticated");
  }).catch((failure: unknown) => {
    if (current !== generation.current || isAbortError(failure)) return;
    const error = isApiError(failure) ? failure : new ApiError(
      "UNEXPECTED_STATUS", "Something went wrong loading your profile.", undefined, undefined,
      { kind: "unexpected", cause: failure },
    );
    setUser(null);
    setError(error);
    setStatus(error.status === 401 || error.code === "AUTH_REQUIRED" ? "unauthenticated" : "error");
  });
}, []);
const clear = useCallback(() => {
  ++generation.current;
  active.current?.abort();
  active.current = null;
  setUser(null);
  setError(null);
  setStatus("unauthenticated");
}, []);
useEffect(() => {
  refresh();
  return () => { ++generation.current; active.current?.abort(); };
}, [refresh]);
```

Add clear to the context interface, provider value and useMemo dependencies. Unexpected local
exceptions become safe ApiError UNEXPECTED_STATUS with kind unexpected/cause. refresh owns a
terminal catch; no returned rejected promise is handed to event handlers. No focus listener
is added before the next Query scope plan owns focus sequencing.

- [ ] **Step 4: Implement and test explicit UserMenu states.** Use a ref lock and state to prevent same-tick duplicates. Disable button with “Signing out…” while pending. Successful logout calls clear and success toast; uncertain/failed logout catches, shows feedback and refreshes profile.

```tsx
const busy = useRef(false);
const [signingOut, setSigningOut] = useState(false);
async function handleSignOut() {
  if (busy.current) return;
  busy.current = true;
  setSigningOut(true);
  try {
    await logout();
    clear();
    toast({ title: "Signed out", tone: "success" });
  } catch {
    toast({ title: "Couldn't confirm sign out", description: "Your session will be checked again. Try signing out once that finishes.", tone: "error" });
    refresh();
  } finally {
    busy.current = false;
    setSigningOut(false);
  }
}
```

Profile error presentation uses existing InlineError, correlation text and “Retry profile” button; an outage is not simply “Sign in”. Test within SidebarProvider/CurrentUserProvider, mocking useToast only for observing feedback and using MSW routes. Assert one logout request after two clicks, pending button, success/failure toasts, cleared identity, retry UI and null role-sensitive identity during verification. Backend authorization is unchanged.

- [ ] **Step 5:** Run provider/menu/race tests, existing Ask Goldy's drawer tests, full suite/typecheck.
- [ ] **Step 6:** Commit `fix: fence staff identity refresh and handle sign-out outcomes`.

## Task 6: Make login and partial signup outcomes explicit

**Files:** Modify login/signup pages; create login-page.test.tsx/signup-page.test.tsx.

**Interfaces:** Consume existing login/signup signatures validated by Task 4 and Next router push/refresh. No new global state/storage/events.

- [ ] **Step 1: Write form/routing/partial-success tests against real auth calls.** Mock next/navigation router, not API adapters. MSW synthetic responses assert action counts.

```tsx
// In signup-page.test.tsx, after render/filling existing form with synthetic credentials:
// Imports: render/fireEvent/screen from @testing-library/react, http/HttpResponse from msw,
// expect/it/vi from vitest, SignupPage from ./page, server/useApiServer from @/tests/api-server.
// Call useApiServer(), mock next/navigation router, render SignupPage and fill all controls
// via getByPlaceholderText before submitting (the baseline forms lack accessible labels).
let created = 0;
server.use(
  http.post("http://localhost:3000/api/auth/signup", () => {
    created++;
    return HttpResponse.json({ displayName: "Fixture staff", department: "GENERAL", seniority: "JUNIOR" }, { status: 201 });
  }),
  http.post("http://localhost:3000/api/auth/login", () => HttpResponse.json({ code: "INVALID_CREDENTIALS", message: "fixture" }, { status: 401 })),
);
fireEvent.submit(screen.getByRole("button", { name: "Sign up" }).closest("form")!);
await screen.findByText("Account created. Sign in to continue.");
expect(created).toBe(1);
expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/login");
expect(screen.queryByRole("button", { name: "Sign up" })).not.toBeInTheDocument();
```

Login cases: two same-tick submits→one request; pending disabled button; 401/500/malformed profile→safe accessible alert without raw message; validated success alone calls router.push('/dashboard') and refresh once. Signup cases: rejected signup→no login; successful signup+login→one navigation; uncertain network result→error without automatic retry; created account+login failure→sign-in link and no create form. Feedback includes correlation when available, never credentials in URLs.

- [ ] **Step 2:** Run new page tests; observe duplicate-submit and partial-signup feedback failures.
- [ ] **Step 3: Add submission ownership and transient account-created state.**

```tsx
const busy = useRef(false);
const [correlationId, setCorrelationId] = useState<string | undefined>();
const [accountCreated, setAccountCreated] = useState(false);
async function onSubmit(event: React.FormEvent) {
  event.preventDefault();
  if (busy.current || accountCreated) return;
  busy.current = true;
  setSubmitting(true);
  setError(null);
  setCorrelationId(undefined);
  try {
    await signup({ email, displayName, password });
    setAccountCreated(true);
    await login({ email, password });
    router.push("/dashboard");
    router.refresh();
  } catch (failure) {
    setError(isApiError(failure) ? failure.message : "Something went wrong. Try again.");
    setCorrelationId(isApiError(failure) ? failure.correlationId : undefined);
  } finally {
    busy.current = false;
    setSubmitting(false);
  }
}
// Before the existing form return, render confirmed creation with the same Card layout:
if (accountCreated) {
  return (
    <main className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader><CardTitle>Create your account</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <p role="status">{submitting ? "Signing in…" : "Account created. Sign in to continue."}</p>
          {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
          {correlationId ? <p className="text-xs text-muted-foreground">Reference: {correlationId}</p> : null}
          {!submitting ? <Link href="/login" className="underline">Sign in</Link> : null}
        </CardContent>
      </Card>
    </main>
  );
}
```

Keep failure feedback visible in both the ordinary form and the created-account branch, with
role=alert and correlation. For login, use the same
guard/finally/correlation flow with only `await login({ email, password })` before navigation;
do not add accountCreated there. For network-uncertain signup, use explicit “Couldn't confirm
account creation. If you already created an account, sign in.” feedback and an existing /login
link; never automatically repeat the POST. Test this message separately from confirmed creation.
Use transport-normalized ApiError messages otherwise. Add control aria-labels/labels,
preserving cards/tokens/layout. accountCreated is local form state, reset by navigation/remount.

- [ ] **Step 4:** Run page/index tests, full suite/typecheck.
- [ ] **Step 5:** Commit `fix: make authentication action outcomes explicit`.

## Task 7: Enforce frontend regressions in CI and record evidence

**Files:** Modify `.github/workflows/ci.yml`, `docs/frontend/verification.md`, `docs/frontend/dependency-decisions.md`.

**Interfaces:** Consume completed prior tasks and existing frontend CI job. Produce a Vitest CI gate and actual PR evidence; no deployment change.

- [ ] **Step 1: Pin compatible runtime and add unit/integration tests to current job.** Add actions/setup-node@v4 with node-version `22.22.1` before Bun; retain Bun 1.4.2/frozen install. Update job/step names to include tests and run sequentially:

```yaml
- name: Type check, lint, test, and build
  working-directory: frontend
  run: bun run typecheck && bun run lint && bun run test && bun run build
```

This prevents the new MSW/identity tests being ignored now. Playwright CI arrives in its separate E2E plan. Never label a remote workflow green without observing its actual result.

- [ ] **Step 2: Run complete checks and native dependency audit.**

```bash
bun install --frozen-lockfile --ignore-scripts
bun run typecheck
bun run lint
bun run test
bun run build
bun audit
```

Record exit codes/counts, triage audit findings by installed package/reachable path, and do not force-remediate or weaken checks. Preserve documented chart/jsdom/Vite warnings pending separate resolution. Compare route sizes with audited 341/363/268 kB (Overview/library/explorer); no unmeasured performance claims.

- [ ] **Step 3: Verify built UI in a real browser.** Use standalone output/server and static asset placement per current frontend Dockerfile, not the audit's unsupported next-start/standalone mismatch. Controlled synthetic API routes can verify UI but are marked mocked. Real browser tools or installed headless Chromium fallback are acceptable.

Check failed-profile retry vs stale Owner presentation; successful retry; safe login rejection; validated login navigation; created-account/failed-login sign-in state; logout pending/failure; demo business views during profile outage. Capture console/unhandled-rejection output and screenshots at desktop 1440×900 and a narrow viewport. No production account creation or destructive benchmark actions.

With approved live server/test credentials: inspect profile 200, unauthenticated status/redirect, login form 200/401, logout 204 and CSRF. If unavailable, document the blocker; mocked tests are not live API/auth evidence. Do not change backend authorization/contracts to get a browser test green.

- [ ] **Step 4: Review diff and record the required PR information.** Run `git diff --check`; inspect file list/lock diff/error-code compatibility, test retention, absence of suppressed checks or logged body values. During implementation run Ripwire change-check/quality-delta using their skills, and record actual results.

PR record: problem/affected files/root causes; native-fetch and additive-contract solution; rejected Axios/global-state alternatives; actual Zod/MSW versions; intentional 403-vs-signout and partial-signup changes; added tests/count; actual typecheck/lint/test/build/browser/CI evidence; available status/correlation/cause semantics; migration/rollback/residual risks; no request-savings claim.

Rollback transport policies and void callers together; schema and inferred-type changes together; provider/UI commits as their unit. Whole-PR revert removes Zod/MSW only after checking remaining imports and restoring the prior lock without disturbing unrelated packages. No persisted business document or Spring session change requires data rollback.

Residual risks: no stable profile subject or Query cache yet; existing useApiData/SSE/dashboard failures remain for their slices; same-profile external session replacement cannot be identified from profile fields; followed fetch hides original redirect status/headers; live tests may remain blocked; error causes require sanitization before later telemetry.

- [ ] **Step 5:** Commit `test: enforce frontend regressions and record transport migration evidence`. Request review; push only with explicit approval. Merge/deployment is outside this plan.

## Self-review and handoff

Coverage: error metadata→Task 1; Headers/CSRF/request cancellation→Task 2; body expectations/envelopes/redirects/MSW→Task 3; auth schemas→Task 4; stale role/logout→Task 5; auth form outcomes→Task 6; CI/evidence→Task 7. The five Review Focus cases have explicit owners/tests. Requirements outside PR 1 remain in the linked roadmap, not silently dropped from the overall migration.

The user selected **Native**. Implementation and the fresh whole-branch review are complete;
the single important finding was fixed through RED→GREEN tests and a passing full suite.
No push, merge or deployment is included. Subsequent roadmap slices require their own plans.
