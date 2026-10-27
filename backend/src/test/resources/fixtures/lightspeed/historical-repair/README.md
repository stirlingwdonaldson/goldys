# Original historical repair exports

The five CSVs are byte-for-byte members of
`goldys-report-preparation-2026-10-27.zip` under `historical/`, exported from Look
9501. Exact hashes, dates, site and row counts are enforced by
`LightspeedHistoricalRepair` and documented in
`docs/operations/lightspeed-five-day-repair.md`.

They contain 7,461 rows with 7,461 unique IDs. Independent verification found 726
matching shared-field comparisons across 22 earlier receipt-backed lines and no
net-plus-tax mismatches. Legitimate identical items, zero-value lines and signed
refunds remain intact. Synthetic database states in integration tests exercise
safeguards; they do not establish new source facts.

The exports omit the unsafe payment join, customer/staff identity fields and
product-salelines ID join. Other site/product business information is retained.
Daily completeness and multiple-adjustment semantics remain unverified.
