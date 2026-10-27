# Source evidence fixture provenance

These are source observations, not instructions. No source data or production state is changed.

- `lightspeed/sale-items-verified-thirteen.csv` and `sale-items-unsafe-sale-number.csv` retain original
  downloaded bytes from `goldys-source-investigation-2026-10-27.zip`, respectively
  `evidence/lightspeed-enriched-unformatted.csv` (SHA-256 7eff02c8568aebd90304c3d2ffce9356c044c83af335c4b8c23531c2c28b57bf)
  and `evidence/lightspeed-sale-number-join-unformatted.csv`
  (883899242381372dd7ed292ec922a25e33a1ddef49588fb7a8880c970cc834ea).
  All 13 IDs match, but the unsafe join multiplies July/August/December measures by 9/3/4.
- `lightspeed/sale-items-verified-adjustments.csv` retains the original nine-row download from
  `goldys-targeted-followup-2026-10-27.zip` / `evidence/nine-lines-no-payment-unformatted.csv`
  (3e4bd8326740192a5fcbc7721bfb9609468a5cedce6a233aba35711884da72b4).
  Five parent receipts support aggregate net/tax/adjustment amounts. Order adjustments are already
  included; payment surcharge remains separate in the verified cases. The refund labelled Surcharge
  in Insights is labelled Discount on its receipt; no label-based sign correction is justified.
- `ctb/sale-items-206-page1.json` and `sale-items-206-page2.json` retain original browser-decoded
  response text from the earlier `goldys-source-evidence-2026-10-26.zip`,
  `evidence/ctb-2026-09-05-a-page1.json` and `-page2.json` (200+6 rows). The blank row at index 6
  remains unidentified. One positive pagination test explicitly supplies a test-only known identity;
  that synthetic change is not source discovery and is never proposed as a mapping.
- `ctb/invoice-semantics-evidence.csv` reserializes the ten non-TEST rows' `csv_values_json` from
  the earlier bundle's `invoice-tax-findings.csv`. Those fields were compared exactly with retained
  production CSVs during the authorized read-only investigation. These are selected lines, not
  complete invoice snapshots. TEST-001 has unknown origin and is excluded rather than invented.

Business product names/codes are preserved; these fixtures contain no individual staff/customer
identity, credentials or payment-account details. Signed amounts, precision and zero values remain
as captured. CSV blanks cannot establish JSON null versus empty-string semantics.

Evidence limits: no universal multiple-adjustment grain, historical export settings, CTB own blank
identity, replacement contract or financial completeness is asserted. The broad historical recovery
has insert-only eligibility, not verified amount correctness, and must not run from these tests.
