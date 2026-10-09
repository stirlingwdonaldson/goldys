# Frontend dependency decisions — proposal

Audited HEAD: `f6140b8`. No dependency changes in this audit. Resolved versions below are
from the existing Bun lock/install, not suggested upgrades. Restore with Bun 1.4.2 and
`bun install --frozen-lockfile`. Next is 15.5.25 and React is 19.3.0 at this checkout.

## Proposed additions and exclusions

| Dependency | Decision / introduction | Reason and rejected alternative |
| --- | --- | --- |
| `@tanstack/react-query` | Add in scoped server-state foundation | Cache, dedupe, lifecycle/invalidation; avoid extending a bespoke query framework. No token/form/UI state in Query. |
| `zod` | Add with selective trust-boundary validation | Versioned/discriminated widget and dashboard/auth schemas with diagnostics. Infer types to avoid maintaining a second handwritten type system. |
| `react-hook-form` | Add with dashboard editor migration | Dirty-state, validation and widget field arrays; simple login and other small forms need not migrate. |
| `@hookform/resolvers` | Add with RHF/Zod editor | Reuse schema validation; avoid custom adapter code. |
| `msw` (dev) | Add with transport/query integration tests | Exercise real adapter/HTTP behavior including malformed/permission/abort cases. Existing local Api unit stubs remain useful. |
| `@playwright/test` (dev) | Add with high-value E2E slice | Reproducible browser workflows and CI. Baseline's external cached harness is not a product dependency. |
| `nuqs` | Defer | Explorer currently needs a small parameter set; native Next URL primitives first. Adopt only if measured boilerplate and navigation correctness justify it. |
| OpenAPI generator | Defer | No current Springdoc dependency/export found. Evaluate a reproducible backend-derived spec before choosing generator/tooling. |
| Axios/alternative transport | Reject for this migration | Native fetch already serves JSON, cookies, CSRF and SSE; no measured replacement benefit. |
| Grid-layout/graph-layout libraries | Defer | Existing grid/keyboard controls and React Flow work. Drag/resize is not an approved new product requirement. |
| Redux/Zustand/competing UI framework | Reject for this migration | No demonstrated need for broad global state or second design system. |

Package versions for new additions must be pinned through Bun's lockfile after checking the
official compatibility documentation against React 19, Next 15, Vitest 5 and Bun 1.4.2.
Do not invent a tested version or bundle benefit before installation and verification.
No removals are justified by the audit. Remove useApiData only after its last consumer migrates.

## Existing runtime inventory (30 packages; retain)

| Package | Resolved version | Responsibility |
| --- | --- | --- |
| `@radix-ui/react-avatar` | 1.2.6 | identity primitive |
| `@radix-ui/react-checkbox` | 1.3.11 | form primitive |
| `@radix-ui/react-collapsible` | 1.1.21 | disclosure primitive |
| `@radix-ui/react-dialog` | 1.2.0 | dialog primitive |
| `@radix-ui/react-dropdown-menu` | 2.1.24 | menu primitive |
| `@radix-ui/react-hover-card` | 1.1.24 | disclosure primitive |
| `@radix-ui/react-label` | 2.1.15 | form labels |
| `@radix-ui/react-radio-group` | 1.4.7 | selection primitive |
| `@radix-ui/react-select` | 2.3.7 | selection primitive |
| `@radix-ui/react-separator` | 1.1.15 | layout primitive |
| `@radix-ui/react-slot` | 1.4.0 | composition primitive |
| `@radix-ui/react-tabs` | 1.1.22 | view selection |
| `@radix-ui/react-toggle` | 1.1.19 | form primitive |
| `@radix-ui/react-toggle-group` | 1.1.20 | grouped selection |
| `@radix-ui/react-tooltip` | 1.2.16 | accessible hints |
| `@sentry/nextjs` | 11.5.0 | monitoring and self-hosted relay integration |
| `@tanstack/react-table` | 8.21.3 | existing reusable table |
| `@xyflow/react` | 12.12.0 | trusted graph views |
| `class-variance-authority` | 0.7.1 | variants |
| `clsx` | 2.1.1 | conditional classes |
| `cmdk` | 1.1.1 | command palette |
| `geist` | 1.7.2 | current typography |
| `lucide-react` | 0.460.0 | icons |
| `next` | 15.5.25 | App Router/build/server |
| `react` | 19.3.0 | UI runtime |
| `react-dom` | 19.3.0 | renderer |
| `recharts` | 2.15.4 | charts; exact package pin retained |
| `sonner` | 2.0.8 | existing action notifications |
| `tailwind-merge` | 2.6.1 | class composition |
| `tailwindcss-animate` | 1.0.7 | existing motion |

## Existing development inventory (15 packages; retain)

| Package | Resolved version | Responsibility |
| --- | --- | --- |
| `@eslint/eslintrc` | 3.3.7 | lint config compatibility |
| `@testing-library/jest-dom` | 7.0.1 | DOM assertions |
| `@testing-library/react` | 16.3.3 | component tests |
| `@types/node` | 22.20.2 | server typings |
| `@types/react` | 19.3.0 | React typings |
| `@types/react-dom` | 19.3.0 | renderer typings |
| `@vitejs/plugin-react` | 6.1.1 | test JSX transform |
| `autoprefixer` | 10.5.5 | CSS pipeline |
| `eslint` | 9.39.5 | lint |
| `eslint-config-next` | 15.5.25 | framework lint |
| `jsdom` | 30.1.0 | unit/component DOM |
| `postcss` | 8.5.28 | CSS pipeline |
| `tailwindcss` | 3.4.19 | design tokens/utilities |
| `typescript` | 5.9.3 | compile-time contracts |
| `vitest` | 5.0.1 | existing test runner |

Source: `frontend/package.json:16–63`, `frontend/bun.lock`, `bun pm ls` and installed package
metadata. Total direct declarations: 45. Do not sweep unused primitives out solely from this
inventory; verify imports and product usage before a separately reviewable removal.
