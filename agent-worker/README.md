# Ask Goldy's Pi worker

This is a local implementation slice, **not ready to enable for a pilot**. See
[implementation evidence](../docs/design/pi-workspace-implementation-2026-11-15.md).
It uses the released Pi 1.5.2 SDK and Bun 1.4.2. No Durable or upstream-main APIs.

## Verification

```sh
bun install --frozen-lockfile --ignore-scripts
bun run typecheck
bun test
bun run build
```

Tests use Pi's synthetic provider, empty in-memory credentials and disposable synthetic
working directories. They do not need a provider key, application database or sandbox.
Installation lifecycle scripts remain disabled. The CI lane runs the same checks.

## Runtime boundary

`src/main.ts` is a single-slot worker supervisor. It claims an authorized Spring job,
loads its Pi-native snapshot and Spring-generated reporting schemas, runs Pi and publishes
serialized native snapshots. A failure reports a fenced interruption immediately; lease expiry
is the fallback if Spring is unreachable. It never replays the attempt. Spring status polling
survives browser disconnection. Stop/revocation is observed at the next heartbeat (five seconds)
or before another model/tool call.

Do not run this worker on the production host or in an application checkout. The accepted
separate analysis VM, clean process environment and restricted egress remain operational
gates. No scripts, public-web tools, upload parsing or artifact filesystem are enabled.
The worker has no SQL/database connection and does not discover local Pi credentials,
project instructions, skills or extensions. Temporary working directories are removed.

The eventual deployment supplies these variables through its approved secret/config mechanism;
no real environment file is included or read during this implementation task:

- `GOLDYS_SPRING_ORIGIN`: fixed HTTPS origin, no credentials, query or path.
- `GOLDYS_WORKER_TOKEN`: private worker token, at least 32 characters; never exposed to browsers.
- `GOLDYS_MODEL_PROVIDER`, `GOLDYS_MODEL_ID`: explicit pinned-catalog model.
- `GOLDYS_MODEL_API_KEY`: isolated model credential, in-memory only.
- `GOLDYS_MODEL_REQUEST_BOUND_AUD`: reviewed **worst-case** charge for one full-context request,
  including cache pricing, reasoning/output charges and a conservative FX bound. Not an average.
- `GOLDYS_MODEL_MAX_OUTPUT_TOKENS`: reviewed provider output ceiling.

The worker rejects absent/invalid pricing and reserves each request's upper bound against A$5,
including compaction and failed attempts. Spring separately reserves A$5 per job against A$50
per venue day. Actual receipt reconciliation, provider-specific cap verification and midnight
accounting are still release gates. Never substitute an optimistic cost estimate to enable it.

`bun run start` disables automatic dotenv loading. Startup with a deliberately empty process
environment was checked to fail before contacting any service. No live worker/provider run has
been performed.

Durable follow-ups are picked up in FIFO order at settled Pi turn boundaries using the same
native session, A$5 reservation ceiling and original deadline. The atomic settle endpoint either
takes the next pending command or completes the job. Pending messages can be edited/removed;
Stop, revocation at claim and expiry cancel pending commands. Taken commands are never replayed
after a crash. A maximum of 20 follow-up submissions (including removed messages) bounds stored
commands per investigation. This is an engineering safety limit, not a new business spend grant.

Browser-facing UI migration and the remaining proposal slices are outstanding.

The browser API can rediscover private threads through `GET /api/workspace/threads`
(keyset `before` cursor, `limit` 1–50). Restricted history is explicit and includes no
latest job ID. Authorized job reads include the original admission prompt and derived
messages, never native snapshots. Live/demo frontend adapters exist; the current Ask
drawer has not yet switched to them.
