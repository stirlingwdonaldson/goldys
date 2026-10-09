---
name: frontend-api-method
description: Add a backend call to the Goldy's frontend through the typed Api contract, with both live and demo implementations. Use whenever a screen or component needs data from a new or changed backend endpoint.
---

Screens never call `fetch` directly. They go through the `Api` interface so demo mode
(`NEXT_PUBLIC_DEMO_MODE`, default on) and live mode behave the same.

## Steps (all in `frontend/lib/api/`)

1. **`types.ts`**: add the response type (mirror the backend record's JSON exactly;
   nullable fields stay `| null`, never defaulted) and the method on `interface Api`.
2. **`live.ts`**: implement it with `fetchApi<T>(url, init?)` from `client.ts`.
   `fetchApi` handles CSRF, JSON parsing and error normalization into `ApiError`.
3. **`demo.ts`**: implement it with realistic fixture data and `await delay(300)`.
   Fixtures must exercise the hard states too: a missing value (`null`), a conflict,
   an empty list. Never let demo data look like production data without the banner.
4. **Consume it** with `useApiData((api) => api.method(args), [deps])` and render all
   three states: loading, error (including `NOT_PERMITTED` as an explicit locked
   state), and data. Missing values render as missing (`lib/format.ts`), never as 0.

## Rules

- Permission denials are shown, not hidden: use the owner-only/locked states in
  `components/states/`, never an empty or partial view.
- Any widget payload from the backend goes through `components/widgets/parse.ts`
  before rendering. Never render AI-produced code.
- Add or extend a test (`demo.test.ts` for fixtures, a component test for rendering).

## Verify

`bun run typecheck && bun run lint && bun run test` from `frontend/`.
