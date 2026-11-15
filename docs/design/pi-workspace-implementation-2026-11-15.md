# Pi workspace implementation evidence — 15 November 2026

Implementation starts from proposal commit `98b8eef9`, following [ADR 0002](../decisions/0002-pi-agent-workspace.md). Local implementation and commits are authorized by the implementation request; production access, provisioning, push, merge and deployment are not.

## Released SDK compatibility

`agent-worker/` pins Bun 1.4.2 and `@earendil-works/pi-coding-agent@1.5.2`, with a committed transitive Bun lockfile. Installation used `--ignore-scripts`. The tests execute the installed npm package, using Pi's synthetic provider with an empty in-memory credential store and no catalog refresh. No real model or data is used.

Observed: four tests pass, covering explicit empty tools, no project AGENTS/extension discovery, native `message_end`/`agent_settled`, complete native tree restoration including branches/null leaf/context edits/compaction, tool AbortSignal propagation, clearing queued follow-ups before abort, and manual compaction through the synthetic provider. Typecheck and Bun build pass. `session.ts` stores detached native snapshots rather than a separately writable transcript.

This establishes a usable released SDK seam, not sandbox containment or production readiness. The worker process still needs a sanitized environment, restricted filesystem/network and Spring admission/authorization/billing boundaries. Snapshots are trusted server records, never a browser-supplied import format. Compaction retains original entries and is not a revocation mechanism. No built-in script tool is exposed.

## Outstanding acceptance

Persisted job orchestration, authorization/data bridge, sandbox execution, attachments/artifacts, controlled web research, live/demo UX, reviewed dataset extraction and operational transition remain delivery work. Actual separate-VM containment, provider pricing/data handling, representative extraction quality, restore/retention and owner pilot are unverified. Manager access remains blocked by the unapproved permission matrix. No production operation or live provider request has been performed.
