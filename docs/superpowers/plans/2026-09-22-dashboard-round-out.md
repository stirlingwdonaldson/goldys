# Dashboard round-out — implementation checklist

Bounded change to round out the existing Dashboard screen (approved design).

## Backend
- [x] `IngestionActivityPoint` record (date, clean, failed)
- [x] `IngestionRunRepository.findByStartedAtGreaterThanEqual(Instant)`
- [x] `IngestionService.activity(int days)` + pure `activityWindow(...)`
- [x] `DashboardController` `GET /api/dashboard/activity`
- [x] Tests: `IngestionServiceActivityTest`, `DashboardControllerTest.activity*`

## Frontend
- [x] `types.ts` + `index.ts`: `ActivityPoint`, `Api.getDashboardActivity()`
- [x] `demo.ts` / `live.ts`: implement `getDashboardActivity()`
- [x] `ConnectorStatusBadge` (extract from connectors page)
- [x] `ActivityChart` component (+ test)
- [x] `ConnectorHealthStrip` component
- [x] `OpenConflicts` component
- [x] `dashboard/page.tsx` wiring (4 fetches)
- [x] `reconciliation/page.tsx` deep-link via `useSearchParams` + `Suspense`

## Verify
- [x] `./gradlew test` (unit tests green; 35 integration tests fail only on missing Docker — pre-existing)
- [x] `./gradlew spotlessCheck`
- [x] `bun run typecheck` / `bun run lint` / `bun run build` / `bun run test` (11 tests green)
