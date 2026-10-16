# Data-foundation verification

This directory contains code-derived schema/lineage inventories, synthetic fixtures, read-only
probe tooling and implementation verification reports. Production audit exports and live repair
candidate reports are retained locally by the operator and intentionally excluded from this public PR.

Use the [release runbook](../../operations/data-correctness-release-2026-10-16.md) for migration checks,
backup/restore rehearsal, deployment and rollback. [Release prerequisites](2026-10-16-release-impact.md)
explain what is verified and what requires live evidence/approval.
The [release verification record](2026-10-16-release-verification.md) lists executed gates and limits.

Implementation records:

- [Foundation repairs](2026-10-16-repairs.md)
- [Invoice reads](2026-10-16-invoice-read-implementation.md)
- [Product/coverage/observations](2026-10-16-product-coverage-observations.md)
- [Stock/count/waste replay](2026-10-16-stock-waste-replay.md)
- [Dashboard/ranking coverage](2026-10-16-dashboard-ranking-coverage.md)

Code-derived audit checks (no database or external packages):

```bash
python3 -m unittest discover -s docs/audits/data-foundation/tests -v
```

The audit scripts regenerate inventories. Any name-based lineage heuristics are inspection aids,
not proof of complete source mapping or a substitute for parser/integration evidence. Fixture values
are synthetic examples, not venue policies. Never publish operator probe output or database backups.
