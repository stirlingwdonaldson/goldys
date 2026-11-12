# Lightspeed O-Series payment and sales semantics

Research checked 12 November 2026 against official Restaurant **O-Series / Kounta** documentation. This note identifies possible causes and investigation checks; it does not establish which occurred at Goldy's. Retail X-Series/R-Series and Restaurant K-Series instructions are excluded. No source configuration, production data or application behavior was changed.

## What the reports measure

The Back Office documentation distinguishes sales from takings. Its Sales Summary excludes refunds/returns, whereas reconciliation's recorded amount includes refunds and money in/out. Reconciliation updates when staff finalise takings; skipping a close combines periods. Payment Breakdown includes payments, refunds, money in/out and tips; its Payments subreport includes gratuities. Counted versus recorded is the takings variance, which is a different comparison from sales versus payments. [Accessing Back Office Reports](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329355994523-Accessing-Back-Office-Reports).

**Investigation implication:** identify the actual report/explore, selected measures and filters before treating two totals as equivalent. “Net” in a sales report and “Net Amount” in a settlement export are not established synonyms by these sources.

## Documented discrepancy mechanisms

| Mechanism | Official evidence | Investigation implication / limit |
| --- | --- | --- |
| Gift-card funding versus redemption | Gift-card sales are negative liability values in reconciliation; redemptions are positive. The period's gift-card balance resets for the next reconciliation. Individual top-ups are Money In/Out against the funding payment method. [Setting up Lightspeed Gift Cards](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329466280475-Setting-up-Lightspeed-Gift-Cards). | A card payment funding a gift card need not represent merchandise sold that day. Examine funding tender, redemption tender and liability movements separately. The documentation also permits a variable-price Gift Card product mapped to a liability account; identify the venue's actual workflow before categorising every gift-card-labelled row. |
| Standalone/manual card payments | Standalone mode takes an amount on the terminal outside the POS integration. Staff can close the POS order with a custom non-integrated tender such as Manual EFTPOS; reporting uses that selected tender. [Lightspeed Payments FAQ](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329304601755-Lightspeed-Payments-FAQ). Manual MOTO payments are likewise standalone, unlinked to a POS order. [Setting up MOTO](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31330413915931-Setting-up-MOTO-with-Lightspeed-Payments). | A manual tender can be an intentional representation of a real terminal payment. The name “Manual Lightspeed Payments” alone proves neither duplication nor missing sales. Pair terminal evidence with the POS tender/order. |
| Completed refunds versus deleted orders | Completed/cashed-off sales cannot be deleted in the POS; corrections require full/partial refund, optionally followed by reprocessing. [Refunding a sale](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329394746907-Refunding-a-sale). | Deleted-order observations and completed-sale refunds describe different lifecycle events. Do not automatically subtract deleted-order value from completed-sales revenue. Validate refund references, replacement sales and payment signs individually. |
| Adjustments and discounts | Insights distinguishes templated discounts/surcharges, custom adjustments and promotions. Its comparison shows sale value after adjustment; the feed records amounts added/deducted and affected products. Multiple line adjustments may appear as Multiple Reasons. [Price adjustment reporting](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329460918299-FAQ-How-do-I-keep-track-of-price-adjustments-and-promos). | Adding discounts back to an already adjusted sale total changes the basis. Determine whether each source measure is before or after adjustments before calculating a bridge. |
| On-account timing | Unpaid account orders do not appear in Sales Feed until fully paid. Discounts must be applied before putting the sale on account. [Selling with Account sales](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329481152539-Selling-with-Account-sales). | Order opening, customer consumption, payment and reporting inclusion can differ. This is a documented possibility, not evidence Goldy's uses accounts in the affected period. |

## Processor/POS comparison: tips, surcharges, missing orders and rounding

Lightspeed's reconciliation guide recommends comparing transaction-level Payments Export and Sales Feed using order/sale IDs and dates, summing multiple payments per order. Terminal tips are excluded from Sales Feed Total but included in payment; combine Total and Tips when applicable. For surcharged refunds its example shows a POS refund of $11.90 versus $12.03 in Payments Export. It documents terminal payments absent from Sales Feed after standalone processing, deletion or wrong POS tender, and apparent POS Lightspeed tenders absent from processor settlement. Zero OrderID can require timestamp matching. Small residuals can be rounding. [Troubleshooting your Lightspeed Payments reconciliation](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329299298459-Troubleshooting-your-Lightspeed-Payments-reconciliation).

**Limits:** the article does not specify an O-Series universal rounding algorithm or acceptable materiality threshold. Retail X-Series documentation about five-decimal prices and one-cent variances must not be imported into O-Series. Preserve unexplained residuals instead of declaring them rounding. A timestamp-only candidate is weaker evidence than a verified order/payment linkage.

## Date boundaries and model limits

Calendar-day filtering can mix two trading days for a business open after midnight. The official guidance recommends consistent takings closures or explicitly configured Insights date ranges aligned to trading hours. [Trading past midnight](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329451314459-FAQ-How-do-I-generate-sales-reports-if-I-trade-past-midnight).

Money In/Out reports follow the takings period rather than necessarily matching sales-report periods. [Recording Money In/Out](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329389716251-Recording-Money-In-Out). Finalised takings and orders are locked; incorrect tenders are corrected by refund/reprocessing. Tips withdrawn during finalisation become Money Out. [Finalising your takings](https://o-series-support.lightspeedhq.com/hc/en-us/articles/31329369881755-Finalising-your-takings).

The processor reconciliation article uses prior-day/weekend settlement examples, but those are not a verified funding schedule for this venue. Confirm its real processing/payout timestamps and filters. Settlement date must not silently replace payment date or sale-closed date.

The Kounta API documents open and COMPLETE orders separately, a deleted-orders filter, order/payment timestamps, and `line_total_ex_tax` already including order/line price variations. It defines COMPLETE as fulfilled and paid. This supports checking lifecycle and adjustment grain, but does not define the semantics of every similarly named Looker field. [Orders API](https://apidoc.kounta.com/orders/).

## Checks for the Goldy's investigation

These are research-derived checks, not confirmed diagnoses:

1. Align venue/register scope, timezone, exact date dimension and interval boundaries; compare sale-closed, payment-created and takings periods separately.
2. Build transaction-level bridges for refunds, tips, processor surcharges, gift-card funding/redemption and money movements. Preserve unmatched amounts explicitly.
3. Match manual and integrated tender totals to terminal evidence without assuming every manual tender is additional money or an error.
4. Verify current CSV definitions with real non-zero examples. The repository's `source-access.md` legacy Sales Feed note says Total includes Tip, while the official processor guide treats them separately. Zero-tip samples cannot settle this conflict; do not add tips blindly.
5. Keep report-specific qualification: official Back Office Sales Summary behavior is not proof that the venue's custom Insights headline export uses the same refund/adjustment rules.

## Evidence and verification limits

All external sources above are first-party O-Series help pages or Kounta API docs, inspected through web search/open. The adjustment-report article was available with full content in the search result, but its direct open returned a fetch error; this is a link-verification limitation. No authenticated venue report, payment processor export or non-zero-tip transaction was inspected by this research task. Public API documentation does not establish account access: the repository records this venue's API as plan-gated and uses scheduled reports/back-office exports. No parser change or numeric accounting identity is proposed without that real evidence. The research skill was read from `.claude/skills/research/SKILL.md`; no new skill or dependency was added. Tests were skipped because this is a research-only note.
