# DESIGN.md

Entry point for design tools and agents (impeccable reads this file). The approved,
authoritative design docs are:

- [`docs/design/ui-direction.md`](docs/design/ui-direction.md): visual direction, tokens,
  responsive rules, shared building blocks. Tokens live in `frontend/app/globals.css`.
- [`docs/design/design-system.md`](docs/design/design-system.md): principles, screen
  inventory, reconciliation / data-table / node-graph interaction patterns.

Read both before changing UI. Refinement preserves this direction; replacing it needs an
approved spec first.

## Constraints that override generic design advice

- **Calm and trustworthy, not flashy.** This is a reconciliation and reporting tool for
  money and labour data, closer to a banking dashboard than a marketing site. Don't apply
  "bolder", "delight" or "overdrive" treatments, decorative motion, or landing-page
  patterns.
- **Ink plus one gold.** `--primary` is near-black; `--brand` gold is for at most one
  action per screen.
- **Never hide absence or conflict.** "No data from source X", conflicts and permission
  denials are first-class visible states. A missing value is never rendered as 0.
- **Status vs identity colours.** Status pills (`success`, `conflict`, `missing`,
  `info`, `failed`) carry state; source tiles (Lightspeed violet, CTB teal, OpenTable
  magenta, Deputy blue) carry identity only. Conflicts are orange, never gold.
- **Desktop-first, light mode only**, shadcn/ui primitives, Geist type, `tabular-nums` on
  every figure, en-AU formatting via `frontend/lib/format.ts`.
- **Detail opens in a right-hand Sheet** with its state in the URL, not a page swap.
- Platform: `web` only.
