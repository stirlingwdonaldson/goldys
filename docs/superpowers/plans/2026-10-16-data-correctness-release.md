# Data-correctness release preparation

Scope: make the approved correction chain mergeable and provide exact operator redeployment steps.
No production mutation is authorised. Baseline main `54af254`; correction tip `96c69eb`.

1. Verify remote/main/PR state and production schema/grants with read-only aggregate probes.
2. Check all applied Flyway checksums and prerequisites; record bounded historical candidates.
3. Fix populated daily-sales startup invalidation with a PostgreSQL regression. Preserve canonical
   facts and accepted decisions; use the existing recomputable resolved projection architecture.
4. Add executable read-only preflight gates, replay-disabled Compose overlays and a release runbook
   covering isolated checkout, images, backup/restore rehearsal, paired deploy, smoke and rollback.
5. Run focused/full gates, release-tool tests, fixture Compose validation and Ripwire quality/review;
   publish a PR for the whole chain and watch remote backend/frontend/image checks through completion.

Evidence: [impact report](../../audits/data-foundation/2026-10-16-release-impact.md).
Runbook: [merge/redeploy](../../operations/data-correctness-release-2026-10-16.md).
Historical parent attribution, source replays and production rollout remain separately approved.
