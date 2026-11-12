# Sales detail: sales versus payments investigation

Checked 12 November 2026, Australia/Melbourne, against source at `a1c961b44` and the production database in sessions with `default_transaction_read_only=on`. Final numerical queries were repeated together in one repeatable-read, read-only transaction. No production records, reports, schedules, services or configuration were changed.

## Finding

The screenshot is reproduced exactly by **14 October–11 November 2026**:

- Merchandise: **$674,088.80**.
- Payments: **$673,710.80**.
- Payments minus merchandise: **−$378.00**.
- Deleted orders: **39, worth $531.70**.
- Every tender total also matches the screenshot, including gift cards $340, manual Lightspeed $76 and Me&U Refunds −$431.98.

The entire $378 is localized to four receipt-number groups whose merchandise totals are exact multiples of payments. Their six financial lines are already inflated in three retained Lightspeed CSVs. Those CSVs join merchandise to payment fields. This is strong evidence of the same payment-join fanout independently documented in the [source evidence guard](../../connectors/source-access.md#source-evidence-guards-27-october-2026). It is not an arithmetic error introduced by the graph or its day projections. A clean financial re-export remains necessary before correcting authoritative historical amounts.

A second confirmed defect creates warnings on balanced data: JavaScript floating-point totals are compared using `difference === 0`. In a reproduction using the actual graph implementation, 146 of 243 exactly balanced observed 2026 days display a warning. On 11 November the difference is approximately −0.000000000001819 dollars and displays as **−$0**.

## Exact screenshot bridge

| Closed date | Receipt number | Stored merchandise | Payment amount | Payment count | Merchandise excess |
| --- | --- | ---: | ---: | ---: | ---: |
| 21 October | SP-96 1021082405 | $260 | $130 | 2 | $130 |
| 22 October | SP-9 1022074935 | $125 | $25 | 5 | $100 |
| 24 October | SP-182 1024055847 | $88 | $44 | 2 | $44 |
| 24 October | SP-213 1024064823 | $208 | $104 | 2 | $104 |
| Total | | $681 | $303 | | **$378** |

The 21 October financial rows have amounts $168, $32 and $60. The other receipts each have one financial row. The five-payment raffle receipt has quantity 25 and amount $125 in the source CSV itself. All six source rows report zero adjustment. This rules out a discount calculation as the explanation for those specific rows.

Retained source evidence:

| Raw record UUID | Received UTC | Affected closed dates |
| --- | --- | --- |
| e1b13f9e-39c3-4971-80f3-fa35d977d2d4 | 21 October 19:04:37 | 21 October |
| 59e827b1-144f-4f5c-bc3d-608bd2634876 | 23 October 19:03:42 | 22 October |
| f2b53a38-58b9-45d7-980d-a03a7f4eadc1 | 24 October 19:03:51 | 24 October |

Each has `salelines.sale_date = 3 days`, query timezone `Australia/Sydney`, and CSV headers including `Saleline Payments Sale Number` and multiple `Payment Data` fields. Sydney and Melbourne have the same offset on these dates. Source line IDs are 31036762580001–3, 31045559150001, 31065417420001 and 31065880770001. Unique line IDs do not prevent measures already multiplied inside the source report.

## Date-range checks

All values below are exact decimal database totals, before graph formatting.

| Inclusive period | Merchandise | Payments | Difference |
| --- | ---: | ---: | ---: |
| 11 November | $13,075.07 | $13,075.07 | $0 |
| 5–11 November, 7 days | $149,890.20 | $149,890.20 | $0 |
| 29 October–11 November, 14 days | $330,651.77 | $330,651.77 | $0 |
| 15 October–11 November, 28 days | $660,767.60 | $660,389.60 | −$378 |
| 14 October–11 November, screenshot | $674,088.80 | $673,710.80 | −$378 |
| 13 October–11 November, 30 days | $681,576.27 | $681,198.27 | −$378 |
| 26 October–1 November | $156,695.57 | $156,695.57 | $0 |
| October | $749,793.77 | $748,959.27 | −$834.50 |
| September | $696,567.36 | $669,766.86 | −$26,800.50 |
| 14 August–11 November, 90 days | $2,062,587.97 | $2,034,792.97 | −$27,795 |
| 1 January–11 November | $6,416,573.08 | $6,379,961.38 | −$36,611.70 |

Every observed day from 26 October through 11 November balances exactly in decimal arithmetic. October has 24 balanced days out of 31; September has 20 out of 30. Across 2026 there are 311 observed days, 243 balanced. Missing dates are not invented as zero days. Keeping one of the four bad receipts inside a selected interval preserves its contribution; this explains the stable $378 across several overlapping ranges.

The broad comparison covers 73 calendar months from November 2020 to November 2026, 1,510,482 current merchandise lines and 999,050 current payment aggregates. See [monthly totals](monthly.csv) and [2026 daily totals](daily-2026.csv). Neither matching day presence nor matching totals proves source completeness.

### 2026 gap attribution

For receipt/day groups with more than one reported payment, the exact identity `merchandise = payment amount × payment count` accounts for **$34,970.20, or 95.52%**, of the net $36,611.70 gap. These are diagnostic identities, not approved corrected sale facts. Timing differences elsewhere can cancel within a month.

| Month | Payments minus merchandise | Excess matching exact payment-count multiplication |
| --- | ---: | ---: |
| January | −$2,396.35 | $2,396.35 |
| February | −$178 | $178 |
| March | −$325 | $325 |
| April | −$114.25 | $114.25 |
| May | −$648.10 | $648.10 |
| June | −$2,789 | $2,659 |
| July | −$1,922 | $1,129 |
| August | −$604 | $604 |
| September | −$26,800.50 | $26,082 |
| October | −$834.50 | $834.50 |
| November through 11th | $0 | $0 |

The largest example is 24 September receipt SP-61 0924075912: $1,713 in 16 payments versus $27,408 of merchandise, a $25,695 excess. It alone explains most of September's gap.

The remaining **$1,641.50** localizes to four mixed-tender receipt/day groups: 1 June $130, 13 July $70, 25 July $723 and 20 September $718.50. Two involve gift-card tenders, one prepaid functions and one cash/card. Their source financial amounts remain suspect; the simple whole-sale multiplication identity does not hold, and this investigation does not certify a correction formula for them. They need clean line-level financial exports and verified payment identity. Do not label this residual rounding or assume gift-card liability movements explain it.

### Older coverage failures

2025 has a much larger and different failure: months have payments in the hundreds of thousands but merchandise totals of only a few thousand. January–June combined has only $16,041.30 of merchandise, with 12,851 uncategorised lines, 2,180 food lines and no normal bar-category population. Five isolated days were repaired separately; that does not establish complete-year coverage. Existing source documentation records the unsafe product join dropping most rows. The observed category/amount coverage is consistent with that problem, but no complete independent 2025 financial re-export was available in this investigation to quantify every missing line.

Older months also contain negative differences and some exact matches, including February 2024. This is not one constant adjustment or one percentage applied universally. Source access and coverage must be established for each historical population.

## How the application handles the requested cases

The [settlement loader](../../../frontend/components/sales-detail/settlement-flow-view.tsx) calls three resolved endpoints. [Graph aggregation](../../../frontend/components/sales-detail/settlement-graph.ts) sums category amounts and tender amounts; deleted orders are an annotation. The graph does **not** read headline daily sales or actual processor/bank settlement. Its “Sales recorded” means sale-item totals and its “Payments” means POS tender amounts. “Settled” is therefore a stronger label than the evidence supports.

[SaleItemProjector](../../../backend/src/main/java/com/goldys/platform/reconciliation/SaleItemProjector.java) sums current canonical quantities and amounts by closed date/category. [PaymentProjector](../../../backend/src/main/java/com/goldys/platform/reconciliation/PaymentProjector.java) sums current canonical payment amounts by payment-created date/tender type. Across all retained dates, both projections exactly match their respective canonical decimal sums: zero differing days. Current IDs are unique and no current amount is null in either domain. Rebuilding these projections alone would reproduce the bad historical values.

### Gift cards

Gift Cards is included as a tender; the graph does not discard it or add it again. The screenshot includes $340 of this tender. Gift-card redemptions can pay for merchandise and should not simply be removed. In the balanced 26 October–11 November period, gift-card tender is $150 and no merchandise item/category name contains “gift”. That name search does not prove no gift-card funding occurred.

Official O-Series documentation distinguishes gift-card funding, liability, redemption and Money In/Out. The graph has no liability/money-movement bridge. Public semantics therefore identify a possible additional reconciliation need; they do not establish gift-card funding as the screenshot's cause. Two older mixed-tender residuals involving gift cards remain unverified as described above.

### Refunds

Signed refund merchandise and negative payment amounts are retained and included in the graph totals; there is no blanket exclusion of Refund sale type or Me&U Refunds tender. In the screenshot period, merchandise includes 40 refund lines totaling −$609.78. Negative payment aggregates include Me&U −$431.98, Lightspeed −$122.80 and Cash −$70. Positive/negative components can net inside a payment aggregate, so summing negative aggregates alone is not a complete refund reconciliation. In particular, the Me&U Refunds node is not the total of every refund.

In the balanced 26 October–11 November period there are 22 refund lines totaling −$347.15, while the named Me&U refund tender is −$206.35. These different classifications do not themselves cause a gap. Refund corrections can also arrive later: January/February and May contain matching positive/negative receipt adjustments on different dates that cancel over a larger interval.

### Deleted orders

Deleted-order value is displayed separately and is not subtracted from merchandise or added to payment amounts. This matches its annotation-only edge in the code. The screenshot's $531.70 of deleted orders does not equal its $378 difference. The balanced 26 October–11 November interval still contains 17 deleted orders worth $237.20. Automatically subtracting those amounts would create an incorrect gap.

Official O-Series guidance distinguishes deleting open orders from refunding completed orders. A deleted order and a completed refund are not interchangeable accounting events.

### Manual payments and prepaid functions

Both are included normally. The screenshot contains $76 manual Lightspeed and $6,933 prepaid functions, neither of which accounts for the $378 gap. Official O-Series guidance supports manual tenders for standalone terminal transactions; their names alone establish neither duplicates nor errors.

There is a concrete timing difference: receipt SP-133 0912075300 has $59 merchandise on 12 September, $37 integrated payment that day and $22 manual Lightspeed payment on 26 September. A daily comparison differs by −$22 and later +$22; a window spanning both balances for this receipt-number group. Another $3,000 sale closes 4 September with $1,000 paid that day and a $2,000 prepaid-function entry on 5 September. Receipt-number matching is diagnostic, not a verified line-to-SaleID bridge; the [receipt-evidence contract](../../operations/lightspeed-separated-feeds.md) retains that limitation.

### Discounts and surcharges

The sale-item parser takes `Sales Data Total Inc Tax` as the final amount. It retains gross-before-adjustment and signed adjustment evidence separately; neither the projector nor the graph applies the discount again. Six screenshot anomaly lines explicitly have zero adjustment.

For 26 October–11 November, 432 Discount rows have $8,331.33 gross, −$6,124.84 adjustment and $2,206.49 final revenue. Another 7,800 Surcharge rows have $2,623.17 adjustment. Gross plus signed adjustment equals final amount to sub-cent precision (one aggregate representation residual of 0.000000000000003 dollars). Total merchandise and payments still balance exactly. Adding discounts back or deducting surcharges again would break this agreement.

Payment surcharge is a separate source field and the graph sums payment **amount**, not tendered, tip or surcharge. In this balanced interval payment-surcharge fields are zero despite merchandise surcharge adjustments. Those fields must not be assumed equivalent. The screenshot also has zero payment tips and payment-surcharge totals. Historical nonzero tips/surcharges need an explicit source contract; this investigation does not equate POS tender totals with processor settlement.

## Existing protections and remaining risks

The current [sale-item importer](../../../backend/src/main/java/com/goldys/platform/connectors/lightspeed/LightspeedSaleItemIngestService.java) rejects payment-joined financial headers with `UNSAFE_REPORT_GRAIN`, and rejects conflicting repeated financial line IDs. The retained October anomaly files contain exactly those unsafe headers. Protection of new imports does not retrospectively supersede already published financial facts. This investigation does not change the source Look or activate any report.

The [payment identity](../../../backend/src/main/java/com/goldys/platform/canonical/CanonicalPaymentService.java) is sale number, payment type, register and created date. Repeated unchanged observations are idempotent. Multiple payments already grouped under those dimensions are represented by amount and payment_count. Additional source dimensions such as payment mode or reconciliation state are not in that key; separate CSV rows differing only on omitted dimensions could overwrite one another. This is a code-level risk, not a demonstrated explanation of the four screenshot groups. A complete raw payment-key collision audit was not performed.

The graph also turns missing numeric values into zero during grouping and formats the gap without cents. Current stored amounts are non-null, so missing-to-zero behavior did not cause the measured screenshot gap. Future coverage/missingness and a sub-dollar real discrepancy should remain visible; UI qualification and monetary comparison need deliberate fixes.

## Verification and recommended follow-up

- [audit.sql](audit.sql) contains the read-only decimal comparisons, screenshot bridge, adjustment checks and residual checks. It uses a repeatable-read snapshot, statement timeout and rollback.
- [check-settlement.test.ts](check-settlement.test.ts) invokes the existing graph against a minimized ordered aggregate fixture from 11 November. Run `bun test docs/audits/sales-payment-2026-11-12/check-settlement.test.ts` from the repository root. It asserts the **observed defect**, not desired future acceptance. The wider in-memory probe covered all 311 observed 2026 days and found 146 false warnings among 243 balanced days.
- [External source research](../../connectors/lightspeed-payment-semantics-research-2026-11-12.md) cites official O-Series/Kounta documentation for each platform behavior and flags the unresolved nonzero-tip Sales Feed contract.

Next implementation should compare amounts at an agreed monetary precision and display cents, qualify the graph's actual source/date bases, and preserve a visible unexplained residual. Historical correction needs clean no-payment financial exports with stable receipt-line IDs and control totals for the approved dates, followed by reviewed bitemporal supersession. The four screenshot receipts are a small bounded starting scope. **Do not repair history by dividing every sale by payment_count**: mixed tenders, refunds, source grouping and missing lines require independent evidence.

Skipped/not checked: authenticated current Lightspeed UI/report changes, fresh historical source exports, processor/bank settlement and gift-card liability/money-movement ledgers, every raw payment identity collision, a full correction contract for the remaining $1,641.50, and complete historical coverage certification. Application fixes, production repair, deployment and publication were outside this investigation. Full backend/frontend suites were not run because application code is unchanged; the focused reproduction and audit/document checks were run locally. No PR or push is authorized by this investigation.
