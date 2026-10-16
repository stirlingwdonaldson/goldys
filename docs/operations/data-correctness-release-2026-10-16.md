# Data-correctness release: merge and redeploy

This is the runbook for the correction chain through `96c69eb` plus the release-preparation commit.
It supersedes the generic upgrade/rollback examples for this release. **These are operator commands,
not operations already performed.** Merging the PR authorises no production action. Obtain separate
approval for the maintenance window/migrations/deployment and, separately, for any historical repair.

## What will change

- V83–V98 (16 migrations) add nullable unknown amounts, approved reporting grants, forecast snapshots,
  supplier/product identity and analytical queries, document/observation relationships and receipts.
  V74–V80 are intentionally absent; do not enable out-of-order mode, baseline, or Flyway repair.
- Every backend boot rebuilds disposable projections from current canonical facts and accepted
  overrides/rules. Daily sales now participates even when populated, applying the corrected GST/net
  comparison. No raw-source replay is needed for that rebuild. A completion marker is logged.
- Existing startup alias seeding and review-classification backfill remain normal startup behaviour;
  the latter can create internal classification runs/use its configured provider. The safety overlay
  disables the opt-in raw ingestion/re-enrichment/backfill runners, not every internal startup task.
- Backend and frontend change together: dashboard trend/ranking endpoints return qualified envelopes.
  Invoice detail/document APIs use UUIDs. Incomplete data remains qualified; historical facts are not
  automatically repaired. Old forecast runs keep unknown input snapshots; new runs freeze their input.
- No forecast/browser/classification sidecar or Postgres image upgrade is part of this release.

## Measured baseline and stop conditions

The [impact report](../audits/data-foundation/2026-10-16-release-impact.md) records the read-only baseline.
Production was PostgreSQL 16.15, with 75 successful migrations through V82; every applied checksum
matched. Before deployment, rerun the gate. Stop for any mismatch, V74–V80 history, failed migration,
duplicate current product source reference, missing inventory raw reference, or role failure.

Existing legacy invoice lines receive a nullable parent column in V86; they remain
unattributed until a separately approved repair. Their known purchases remain in aggregate reporting,
but invoice-specific line panels/supplier assignment cannot be certified complete. A unique
current number candidate is **not** proof of original issuer attribution. If that interim
qualification is unacceptable, postpone deployment until the scoped parent repair is approved and
rehearsed. No line disappears from canonical storage.

## 1. Merge only after CI passes

Review the full PR against main, including the existing implementation reports. Do not merge an old
foundation branch carrying V74–V80 alongside it. Confirm GitHub reports the release PR mergeable and
all backend/frontend/image checks pass. This runbook does not merge the PR or deploy it.

After merging, use an isolated checkout rather than pulling over the shared production checkout's
untracked requirements and plans. Run the following in one Bash session on the VM:

```bash
set -euo pipefail
umask 077
cd /srv/ai/projects/goldys
git fetch --prune origin
RELEASE_SHA=$(gh pr view codex/data-correctness-main --repo stirlingwdonaldson/goldys \
  --json state,mergeCommit --jq 'select(.state == "MERGED") | .mergeCommit.oid')
test -n "$RELEASE_SHA"
git merge-base --is-ancestor "$RELEASE_SHA" origin/main
# This selects the PR's exact merge commit, even if main advanced afterwards.
git show -s --format='%H %s' "$RELEASE_SHA"
RELEASE_DIR="/srv/ai/projects/goldys-releases/$RELEASE_SHA"
mkdir -p /srv/ai/projects/goldys-releases
git worktree add --detach "$RELEASE_DIR" "$RELEASE_SHA"
cd "$RELEASE_DIR"
git diff --exit-code
REL="$RELEASE_DIR/ops/releases/2026-10-16-data-correctness"
ENV_FILE=/srv/ai/projects/goldys/.env.prod
BACKUP_DIR="/srv/ai/backups/goldys/data-correctness-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
python3 "$REL/preflight.py" --container goldys-prod-postgres-1 --expected-version 82 > "$BACKUP_DIR/preflight-before.json"
```

Do not read, copy, print or source the real env file. Compose consumes it directly below. If the
application uses a different database principal from the migration principal, arrange a DBA-reviewed
`GRANT goldys_ro TO <actual_application_role>` before enabling analytical reads; this stack currently
uses the same `POSTGRES_USER` for both. The preflight checks that connected principal, not arbitrary roles.

## 2. Preserve rollback images and build before downtime

The shell functions fix the production project name explicitly. Use the safety overlay on subsequent
restarts too; otherwise old one-off values in the operator's configuration could reactivate runners.
The temporary maintenance overlay pauses CTB scheduled and SFTP pulls while validation runs.

```bash
prod_safe() {
  docker compose -p goldys-prod --env-file "$ENV_FILE" \
    -f "$RELEASE_DIR/docker-compose.prod.yml" -f "$REL/safety.yml" "$@"
}
prod_maintenance() {
  docker compose -p goldys-prod --env-file "$ENV_FILE" \
    -f "$RELEASE_DIR/docker-compose.prod.yml" -f "$REL/safety.yml" -f "$REL/maintenance.yml" "$@"
}
OLD_BACKEND=$(docker inspect --format '{{.Image}}' goldys-prod-backend-1)
OLD_FRONTEND=$(docker inspect --format '{{.Image}}' goldys-prod-frontend-1)
ROLLBACK_TAG="pre-data-correctness-$(date -u +%Y%m%dT%H%M%SZ)"
docker tag "$OLD_BACKEND" "goldys-rollback-backend:$ROLLBACK_TAG"
docker tag "$OLD_FRONTEND" "goldys-rollback-frontend:$ROLLBACK_TAG"
printf '%s\n' "$OLD_BACKEND" "$OLD_FRONTEND" "$ROLLBACK_TAG" > "$BACKUP_DIR/images.txt"
export SENTRY_RELEASE="$RELEASE_SHA"
prod_maintenance build backend frontend
```

A build failure here leaves running containers unchanged. Do not prune images/volumes or restart
sidecars. Ensure sufficient disk for the backup, restore rehearsal and images before stopping apps.

## 3. Approved maintenance window: quiesce and back up

Stop frontend/backend only. External webhook/upload requests will fail during maintenance; arrange
retry/retained delivery and record the window. Do not start the window unless upstream HTTP delivery
is paused or its retry/retention is confirmed; do not assume a failed webhook will be retried. SFTP continues buffering files in its existing volume.
No one should run ingestion, edits or manual database writes during the window.

```bash
prod_maintenance stop frontend backend
python3 "$REL/preflight.py" --container goldys-prod-postgres-1 --expected-version 82 > "$BACKUP_DIR/preflight-quiesced.json"
docker exec goldys-prod-postgres-1 sh -c \
  'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "$BACKUP_DIR/database.dump"
test -s "$BACKUP_DIR/database.dump"
sha256sum "$BACKUP_DIR/database.dump" > "$BACKUP_DIR/database.dump.sha256"
docker exec -i goldys-prod-postgres-1 pg_restore --list \
  < "$BACKUP_DIR/database.dump" > "$BACKUP_DIR/archive-list.txt"
```

The backup contains raw/business/personal data. Keep it access-controlled and off-repository; ensure
an approved off-VM copy and retention policy. Do not include the live OpenTable browser profile.
Listing an archive is not a restore test: complete the isolated rehearsal below before upgrading.

## 4. Rehearse this backup and migration offline

These uniquely named containers publish no ports, use no production volumes/env files and share an
isolated network namespace with no external route. The backend gets no connector/provider secrets.
Do not point either rehearsal container at the production database. If either name already exists,
stop and identify it rather than deleting something by guess.

```bash
docker run -d --name goldys-release-restore-check --network none \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=goldys postgres:16
for attempt in $(seq 1 60); do
  if docker exec goldys-release-restore-check pg_isready -U postgres -d goldys >/dev/null; then break; fi
  sleep 2
done
docker exec goldys-release-restore-check pg_isready -U postgres -d goldys
docker exec -i goldys-release-restore-check pg_restore -U postgres -d goldys \
  --no-owner --no-acl --exit-on-error < "$BACKUP_DIR/database.dump"
python3 "$REL/preflight.py" --container goldys-release-restore-check --expected-version 82 \
  > "$BACKUP_DIR/rehearsal-before.json"
docker run -d --name goldys-release-rehearsal --network container:goldys-release-restore-check \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/goldys \
  -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=unused \
  -e APP_SCHEDULING_CTB_ENABLED=false -e CTB_SFTP_ENABLED=false \
  -e SPRING_AI_MODEL_CHAT=none goldys-prod-backend:latest
```

Wait for the completion marker **and** confirm the container is still running. `Started ...` or the
constant `/api/health` response alone does not prove migration/startup projections finished.

```bash
ready=false
for attempt in $(seq 1 120); do
  if docker logs goldys-release-rehearsal 2>&1 | grep -F 'Resolved projections rebuilt from current canonical facts' >/dev/null; then
    ready=true; break
  fi
  sleep 5
done
test "$ready" = true
test "$(docker inspect --format '{{.State.Running}}' goldys-release-rehearsal)" = true
python3 "$REL/preflight.py" --container goldys-release-restore-check --expected-version 98 \
  > "$BACKUP_DIR/rehearsal-after.json"
```

Review before/after aggregates: canonical current/history counts, amounts and business dates should
not change from this deployment. Structural receipt tables and permissions are expected additions;
resolved results may change according to corrected reconciliation. Legacy parent candidates remain
until explicit repair. Check new `goldys_ro` SELECT access and denied raw/write access using the
application tests/SQL in the impact report. The rehearsal cannot certify real login/UI/provider access.
On failure keep production stopped, investigate, or resume old images on the untouched V82 database.

## 5. Deploy the pair and validate before restoring pulls

Only proceed after the backup restore/migration rehearsal and approval of expected impact.

```bash
DEPLOY_STARTED=$(date -u +%Y-%m-%dT%H:%M:%SZ)
prod_maintenance up -d --no-deps --no-build backend
ready=false
for attempt in $(seq 1 120); do
  if docker logs --since "$DEPLOY_STARTED" goldys-prod-backend-1 2>&1 | grep -F 'Resolved projections rebuilt from current canonical facts' >/dev/null; then
    ready=true; break
  fi
  sleep 5
done
test "$ready" = true
test "$(docker inspect --format '{{.State.Running}}' goldys-prod-backend-1)" = true
python3 "$REL/preflight.py" --container goldys-prod-postgres-1 --expected-version 98 \
  > "$BACKUP_DIR/preflight-after.json"
prod_maintenance up -d --no-deps --no-build frontend
curl --fail --silent --show-error https://platform.swd.sh/api/health
```

Stop on startup loops, migration failure, preflight blockers, changed canonical counts without an
explained concurrent writer, unexpected permission grants, or unqualified/null-to-zero display.
Never run Flyway repair, change old migration checksums, delete raw records, or truncate projections
to force startup. The 16 migrations do not recover previously coerced/misattributed facts.

Owner smoke checks (use existing accounts; no account writes): log in; load Home/Sales; check net ex-GST
labels, requested dates, empty/partial qualifiers and top sellers; open two invoice UUID details;
verify historical missing-parent lines are disclosed; open the correct authorised PDF, and verify
ambiguous/multiple documents require explicit choice. Open a saved widget and Ask Goldy's for the
same period and compare known values/qualifications. Read an old completed forecast and inspect a
new forecast only if separately authorised (creating one writes a run and invokes the model).

With an existing STAFF account, verify forecasting/supporting aggregate access and denied detailed
financial/raw access. For a manager account, if one exists, verify aggregate sales/bookings/labour
and denied individual pay/raw records. If no manager account exists, do not claim that live
check passed or create/promote accounts as part of the deployment. Generic permission tests cover it.

After acceptance restore configured CTB schedules, keeping all historical replay switches off:

```bash
prod_safe up -d --no-deps --no-build backend
# Wait for the new startup completion marker again, then:
curl --fail --silent --show-error https://platform.swd.sh/api/health
prod_safe ps backend frontend postgres
```

CTB SFTP resumes only if enabled in the existing configuration; scheduled CTB uses its existing
configured/default schedule. Observe next normal ingestion stages and failures; do not trigger a
manual/full-history ingestion to make a smoke check pass. Record the final SHA, image IDs, V98
history, start/end times and qualifications. Keep the release checkout: it now supplies Compose paths.

## 6. Historical repair is separate

Do not enable invoice-parent, PDF, product, stock/waste or sales replay flags during the above steps.
If invoice-parent repair is separately approved, review its candidate identities/source/date evidence
and rehearse `app.invoice-logical-id-backfill=true` on the offline copy first. The existing runner
appends reparented versions and leaves ambiguous/orphan relationships unresolved. A later one-off
Compose override would set `APP_INVOICE_LOGICAL_ID_BACKFILL: "true"` after the safety overlay for that
single boot, then be removed. Define the approved count/identity set and rollback evidence first;
this runbook does not authorise candidates merely because their numbers are unique today.
PDF parent qualification and old supplier identity need their own evidence; do not number-join them
in SQL. Product histories lacking evidenced request dates cannot be repaired by invented dates.

## Rollback

Before the production backend starts/migrates, resume the old images on the unchanged database.
After V83–V98, **do not simply run the old code against the upgraded/written database**: it assumes
non-null facts and old identity/replay contracts. Prefer a forward fix where safe. Restoring the
quiesced backup loses all post-backup writes; obtain explicit restore/data-loss approval first and
retain a new failed-state backup for investigation. Do not drop the production Docker volume.

For an approved full database restore, keep apps stopped and verify backup SHA/restore rehearsal:

```bash
prod_maintenance stop frontend backend
sha256sum --check "$BACKUP_DIR/database.dump.sha256"
# DESTRUCTIVE: execute only after the explicit database-restore approval.
docker exec goldys-prod-postgres-1 sh -c \
  'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" -O "$POSTGRES_USER" "$POSTGRES_DB"'
docker exec -i goldys-prod-postgres-1 sh -c \
  'exec pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error' \
  < "$BACKUP_DIR/database.dump"
python3 "$REL/preflight.py" --container goldys-prod-postgres-1 --expected-version 82 \
  > "$BACKUP_DIR/rollback-preflight.json"
```

Create an image-only overlay (no source checkout switch):

```bash
cat > "$BACKUP_DIR/rollback.yml" <<YAML
services:
  backend:
    image: goldys-rollback-backend:$ROLLBACK_TAG
  frontend:
    image: goldys-rollback-frontend:$ROLLBACK_TAG
YAML
docker compose -p goldys-prod --env-file "$ENV_FILE" \
  -f "$RELEASE_DIR/docker-compose.prod.yml" -f "$REL/safety.yml" \
  -f "$REL/maintenance.yml" -f "$BACKUP_DIR/rollback.yml" \
  up -d --no-deps --no-build backend frontend
curl --fail --silent --show-error https://platform.swd.sh/api/health
```

The new NOLOGIN `goldys_ro` cluster role/membership may remain after a database restore. Do not drop
it blindly; no raw grants should exist, and restored V82 has no analytical executor. Have a DBA
review it before a future upgrade. Revalidate old UI/account access and only then restore normal
pull schedules using the rollback image overlay (never accidentally selecting the new images).
Keep all backup/release evidence. Clean up only the two explicitly named rehearsal containers after
review; their temporary databases contain production data. Never use `down -v` on `goldys-prod`.

After acceptance, securely remove only the named rehearsal containers and their temporary database:

```bash
docker rm -f goldys-release-rehearsal
docker rm -f -v goldys-release-restore-check
```
