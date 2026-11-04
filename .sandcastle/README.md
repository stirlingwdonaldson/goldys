# Sandcastle weekly report trial

Pinned to [Sandcastle 0.14.0](https://github.com/mattpocock/sandcastle), using Codex
and Bubblewrap on Linux. This tooling package is separate from the frontend.

Run from this isolated checkout, never the production checkout:

```sh
bun install --cwd .sandcastle --frozen-lockfile
bun run --cwd .sandcastle check
bun run --cwd .sandcastle preflight
bun run --cwd .sandcastle login
SANDCASTLE_MODEL=<your-codex-model> bun run --cwd .sandcastle weekly-ui
```

`login` runs Codex device authentication with a fresh, ignored CODEX_HOME in
`.sandcastle/codex-home/`. Complete the browser sign-in yourself with your ChatGPT
subscription. It does not copy your normal host login. Do not paste credentials
into chat or create a `.sandcastle/.env`; this trial refuses that file. The CLI
owns its authentication files. Treat the ignored directory as credentials.
The runner requires a ChatGPT login and does not configure API billing.

`preflight` checks the real sandbox and toolchain without starting a coding agent
or requesting a model. It creates and removes a clean preflight worktree; its
local branch may remain. Run it outside another process sandbox if nested user
namespaces are blocked. Never change host security settings just to run it.

The UI pass runs once, with a 45-minute cancellation deadline, in a local worktree
on `codex/sandcastle-weekly-ui-pass`. Review its commits, browser evidence and
`.sandcastle/weekly-ui-result.md` before publishing. There is no automatic merge,
push, deploy or issue closing. The fixed branch is for this one trial; do not run
two copies concurrently. The model is explicit rather than tied to an old default.

## GitHub

Optional: set `SANDCASTLE_ISSUE` to an existing Goldy's issue number alongside the
model. The host's `gh issue view` fetches its title/body read-only and supplies the
text to the trial. No GitHub credential is passed into the sandbox. An issue is not
required for the first UI experiment. Creating issues or publishing the resulting
branch/PR requires the applicable authorization in AGENTS.md.

The design brief is [prompt.md](prompt.md). It requires before/after screenshots,
existing design tokens, all report fields and qualifications, meaningful tests,
and the frontend checks. Authentication and a sandbox smoke check alone do not
establish that the UI pass has succeeded.

## Limits

Bubblewrap shares this host's network, including localhost. Production access is
prohibited by the brief, but network isolation is not an enforced firewall. Use a
separate development machine or an isolated VM if enforced production network
separation is required. No Docker socket is mounted. The dedicated Codex home and the resolved Codex executable directory
(read-only) are added as mounts. Logs/worktrees/auth/node_modules are ignored; do not force-add
them. CaptureSessions is disabled so Sandcastle does not copy the trial login home
into the regular host Codex home.

References: [agent providers](https://github.com/mattpocock/sandcastle/blob/main/docs/guide/agent-providers.md),
[native sandboxes](https://github.com/mattpocock/sandcastle/blob/main/docs/guide/sandbox-providers.md),
[Codex authentication](https://learn.chatgpt.com/docs/auth).

Codex 0.162.1 no longer accepts `-a` after `exec`. The runner adapts upstream's
`-a on-request` to `-c 'approval_policy="on-request"'`, retaining the auto-review
configuration and Bubblewrap. Assertions fail closed if upstream's command changes.
