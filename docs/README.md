# Project documentation

Read the smallest reference that answers the task.

| Need | Authority |
|---|---|
| Product scope and priority | [PRD](prd.md) |
| Behaviour and acceptance | [SRS](srs.md) |
| What exists and its limits | [App state](app-state.md) |
| Outstanding work and decisions | [GitHub issue index](roadmap.md) |
| Full requirement detail by ID | [Scoped source records](requirements/README.md) |
| Shared business meanings | [Glossary](../GLOSSARY.md) |
| Architecture and constraints | [System context](system-context.md), [current packages](architecture/current-state.md), [backend invariants](../.claude/rules/architecture-invariants.md) |
| Source access and identity | [Access paths](connectors/source-access.md), [matching](connectors/matching-and-identity.md), [OpenTable](connectors/opentable.md) |
| Metrics and AI/UI contracts | [Metric catalogue](metrics/catalog.md), [AI tools](contracts/ai-tool-boundary.md), [widgets](contracts/widget-spec.schema.json), [dashboards](contracts/dashboard-document.schema.json) |
| UI changes | [Design entry](../DESIGN.md), [visual direction](design/ui-direction.md), [interaction system](design/design-system.md) |
| Operations | [Testing](operations/testing.md), [deployment](operations/deployment.md), [weekly-report accuracy reviews](operations/weekly-report-review.md) |
| Agent workflow | [Skills](agents/skills.md), [GitHub tracker](agents/issue-tracker.md), [domain docs](agents/domain.md), [review commands](agents.md) |
| Durable choices | [Owner scope decisions](decisions/2026-11-04-product-scope.md), [governed conversational planning](decisions/0001-governed-conversational-planning.md), existing files in `decisions/` |
| Dated evidence | [Sales/payment discrepancy audit](audits/sales-payment-2026-11-12/README.md), [CTB stock/cost audit](audits/ctb-stock-cogs-2026-11-03/README.md), [historical archive](archive.md) |

Update definitions in place when approved behaviour changes. GitHub owns delivery
status; PRD/SRS remain reference contracts. Detailed source snapshots and dated
audits preserve evidence, not current approvals. Current code never overrides an
architectural invariant merely by disagreeing with it.
