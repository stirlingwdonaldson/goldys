# SRS verification fixtures — revision 1.1

Date: 14 October 2026 (Australia/Melbourne). Authority: [SRS v1.1](SRS.md), sections 8–9, 16–17. These subcases supplement existing AT identifiers and requirement AC1 contracts. They are documented expected-result oracles, not executed software tests or acceptance evidence. All values, classifications, schedules, rates and precedence rules below are fixture-only choices; production OD decisions remain open. Unspecified relevant evidence is not assumed complete.

Every case retains stable assertion identity, source/evidence, effective/occurrence versus recorded times, current and superseded revisions, scope/units and adopted fixture-policy version. Before the relevant production policy is adopted, authoritative attribution/scoring remains blocked or unresolved as the SRS requires.

## AT-09.POS — Complete valuation, recognition and profit

**Trace:** DR-003/DR-019/DR-024/DR-027/DR-036/DR-041/DR-049/DR-050; FR-028/FR-038; MET-007/MET-016; BR-003/BR-009; REL-002. UJ-01/UJ-06; review R01/R02/R11.

**Bound fixture policy F-P1:** one venue, one stock item/location, one October accounting period, AUD, evidenced tax-exclusive values; no tax conversion needed. All eligible revenue and cost populations are completely evidenced. Returns occur before pooling and remove their evidenced acquisition cost; subsequent net stock is valued with a periodic weighted-average pool. Loss is classified as a separate operating expense, excluded from goods-sold COGS and charged once. There are no transfers, production/conversion costs or other inventory changes; no other expenses in this explicitly bounded operating-result scope. This is not a complete statutory net-profit claim.

**Atomic material and acquisition facts (distinct document, movement and allocation identities):**

- Opening holding O1: 10 units, carrying value 80.
- Receipt A1: 10 units, acquisition value 100 (10 per unit).
- Receipt B1: 10 units, acquisition value 120 (12 per unit).
- Return T1 before pooling: 2 units from B1, physical return and linked supplier credit 24, reducing acquisition value once. The credit and its valuation allocation are not two deductions.
- Landed-cost assertion L1: freight 32, allocated to the eligible net inventory pool; quantity zero, without creating a receipt.
- Eligible sales consumption S1: 15 units sold; matched, evidenced net revenue 300. Loss D1: 3 units physically disposed with distinct loss evidence. Closing count C1: 10 units. All unit mappings, cutoff and populations agree.

**Independent quantity and cost derivation:**

- Net pool quantity = 10 + 10 + 10 − 2 = 28 units.
- Pool carrying value = 80 + 100 + 120 − 24 + 32 = 308; unit carrying cost = 308 / 28 = 11.
- Physical closing = 28 − 15 sold − 3 disposed = 10 units; observed closing 10, variance 0.
- COGS allocation = 15 × 11 = 165; loss allocation = 3 × 11 = 33; closing value = 10 × 11 = 110.
- Value conservation: 308 = 165 + 33 + 110. Period reconciliation: 80 + 220 + 32 − 24 − 33 − 110 = 165 COGS.
- Gross profit = 300 − 165 = 135; gross margin = 135 / 300 × 100 = 45%.

**Atomic non-inventory recognition facts:**

- Labour entry W1: recognised October expense 50, linked to a DR-036 operational labour component of 50. The component is explanatory evidence for W1 and excluded as an additional expense operand; recognised labour remains 50, not 100.
- Prepaid expense P1: invoice and payment of 1,200 in October, covering twelve fixture monthly periods October–September. Explicit equal-period allocation F-R1 yields October recognised expense 100 and eleven remaining allocations of 100 each (remaining prepaid balance 1,100). The invoice and money movement retain October dates and 1,200 amounts; neither is the October expense operand.
- Non-cash entry N1: evidenced October expense 10 under fixture recognition policy F-R1, with no new invoice or cash movement. Account/category, classification version, entry/line identity, period and origin evidence are supplied.

**Expected operating result:** 135 gross profit − 50 labour − 100 prepaid-period expense − 10 non-cash expense − 33 separate loss = **−58 AUD**. Total expenses beyond COGS = 193. Each recognised expense has its own atomic allocation identity and evidence; payments, invoices and operational labour operands are not summed again. This complete bounded result must be independently reproducible from these facts, rather than compared only with another system-generated result.

## AT-09.INC — Missing financial evidence

**Trace:** AT-09.POS requirements plus BR-004/BR-007 and REL-003; review R11.

Run independent variants from the complete POS fixture:

1. Remove opening carrying value O1 while retaining its known quantity. Under F-P1 the pool cost, COGS, loss value, closing value, gross profit and operating result become unavailable with the named missing valuation input. Physical closing 10, count variance 0 and evidenced revenue 300 remain available. No opening value of zero or purchases-as-COGS fallback is permitted.
2. Restore valuation, then remove the amount/evidence for required non-cash expense N1. Gross profit remains 135; complete operating result is unavailable. A labelled subtotal excluding the unknown expense may show −48, with missing scope and no final-profit claim. It cannot show the complete −58 without N1 evidence.

Aggregate-only accountant results may remain labelled aggregate-only; they do not fabricate these missing allocations or satisfy detailed-input verification.

## AT-09.REV — Late value-only supplier credit

**Trace:** AT-09.POS requirements plus DR-048, BR-005 and REL-005; review R02/R11.

Starting from POS, a separately identified late credit K1 for 28 AUD is received in November. Its evidence explicitly attributes the credit to the retained acquisition cost of the 28-unit October net pool, excluding the earlier returned units. Fixture F-P1 permits retrospective October cost correction; preserve October effective recognition and November recorded/received dates. Allocate the credit across the original sold, lost and retained populations, without changing quantities, revenue or the earlier 24 return credit.

- Revised pool = 308 − 28 = 280; revised unit cost = 280 / 28 = 10.
- Revised COGS 150, loss 30, closing value 100; 280 = 150 + 30 + 100.
- Credit allocations: −15 sold, −3 lost, −10 closing; sum −28. Net eligible acquisition additions now 220 + 32 − 24 − 28 = 200; opening 80 + additions 200 − loss 30 − closing 100 = 150 COGS.
- Revised gross profit 150; margin 50%; operating result = 150 − 50 − 100 − 10 − 30 = **−40 AUD**.
- Original issued report/result R1 remains identifiable with COGS 165, closing 110, gross profit 135 and operating result −58. Restatement R2 links K1, new allocations, policy F-P1 and original R1 with reason and time. Physical closing remains 10; credit creates zero physical movement and no inferred cash refund. Operating result improves by 18, reflecting 15 lower COGS and 3 lower loss; 10 reduces closing inventory value.

## AT-05.REDEEM — Non-cash settlement and reversal

**Trace:** DR-007/DR-011–DR-013; FR-013/FR-014; BR-010; review R03. UJ-06.

Fixture F-S1 recognises voucher issuance as customer liability, not meal revenue. One original cash receipt M1 of 100 funds liability issuance V1 of 100 through allocation A1. Later, a separately evidenced meal charge C1 of 100 is recognised under F-S1. Liability release V2 of 100 settles C1 through non-cash allocation A2, sourced from V2 rather than M1. Expected cumulative cash receipts 100, new cash at redemption 0, outstanding voucher 0, C1 unpaid balance 0 and meal revenue 100 counted once. Reversing A2/V2 restores voucher balance 100 and unpaid charge 100, retains charge/revenue 100, creates no cash refund and no additional receipt. Do not also apply M1 to C1. Missing liability detail cannot prove individual redemption capacity.

## AT-05.TAX — Mixed treatments, shared charge and partial credit

**Trace:** BR-003; DR-008/DR-010/DR-019; MET-001/MET-007; review R08.

Fixture F-T1 supplies a 10% treatment for line X and a zero-tax treatment for line Y; these are artificial test treatments, not Goldy's GST classifications. Line X inclusive 110 = exclusive 100 + observed tax 10. Line Y inclusive/exclusive 50, observed tax 0. A shared header charge of 11 has evidenced allocation wholly to X: exclusive 10 + tax 1. Original document reconciles inclusive 171 = exclusive 160 + tax 11. A partial credit to X is inclusive −22 = exclusive −20 + tax −2, linked to its original line and tax basis. Revised totals: inclusive 149, exclusive 140, tax 9. F-T1 rounds allocated AUD components to cents; these numbers incur no residual. Header and allocated amounts count once. Remove X's exclusive amount and tax reconstruction evidence: inclusive totals stay known, while the unsupported X/document exclusive derivation is unavailable; Y remains known. An inclusive label or generic rate alone cannot fill missing treatment/allocation evidence.

## AT-08.AVAIL — Recurrence, absence and partial cover

**Trace:** DR-051; FR-031/FR-033; DR-029/DR-031–DR-033; ACC-003; review R04. UJ-03.

Use an explicit fixture timezone and Friday service 17:00–23:00. Person P has recurring Friday availability 17:00–23:00 (A1); confirmed dated unavailability covers that Friday (A2); a later confirmed offer A3 covers only 18:00–21:00. Fixture precedence F-A1: dated exception overrides recurrence; later confirmed explicit partial override replaces the earlier state only within its stated interval. Expected P availability is unavailable 17:00–18:00 and 21:00–23:00, available 18:00–21:00. A published P shift 17:00–23:00 remains the original plan: three hours of absence conflict, with three hours potentially covered by a local option. No published roster change is inferred until external evidence supports it. If F-A1 is unadopted, show the overlapping declarations and unresolved precedence. Ordinary coverage users see no private absence reason.

## AT-07.HOLD — Physical, usable and uncommitted holdings

**Trace:** DR-021/DR-024/DR-027/DR-052; FR-017/FR-018; MET-014; review R05. UJ-04.

Twenty portions have a known physical holding at one location. Fixture F-H1 excludes held portions from usable stock and separately committed usable portions from supply available to other demand. Five identified portions are held and eight other portions committed to function F1: physical 20, usable 15, uncommitted usable 7. Release the five-portion hold: physical remains 20, usable 20, uncommitted usable 12. In a separate overlap variant, three of the eight committed portions are also among the five held: union excluded from uncommitted supply = 5 + 8 − 3 = 10, so uncommitted usable 10 and usable 15. Do not subtract 13. Without overlap evidence, do not claim an exact uncommitted quantity. Holds and releases create no disposal or transfer; lot tracking is required only if the adopted rule uses it.

## AT-06.PROMISE — Split schedule and selected baseline

**Trace:** DR-018–DR-020/DR-053; FR-017/FR-019; MET-014; review R06. UJ-04.

Ten cases on one order line are promised as tranche A: five Monday by 12:00; tranche B: five Friday by 12:00. Original promises have stable identities and issue times. Before Friday, supplier revises B to Saturday by 12:00 with recorded acceptance; retain both revisions. Accepted receipts allocated without overlap: three cases Monday 11:00 and two Tuesday 11:00 to A; four Saturday 11:00 to B. At Saturday 11:30, total accepted 9, remaining A 0/B 1, total remaining 1.

Fixture scoring F-D1 counts accepted cases received by each selected deadline divided by ten promised cases, with cutoff after both selected windows. Original baseline: three cases on time, 30% quantity-based timeliness. Accepted revised baseline: seven cases on time, 70%. In-full accepted quantity at cutoff is 9/10 = 90% under this explicitly quantity-based measure; this is not a binary order OTIF score. Horizon analysis cites the current accepted B revision; historical scoring declares its chosen baseline. A missing adopted scoring rule leaves the authoritative score unresolved, not retrospectively improved by overwriting B.

## AT-04.STAGE — Partial stages, correction and remake

**Trace:** DR-008/DR-054; FR-010; MET-003; review R07. UJ-05.

Four meals on one line are ordered at 19:00. Partition P1 of two meals is ready at 19:10 and served 19:15; P2 of two is ready 19:20, with served time not captured. Each stage has its own event identity and quantity-partition link. Fixture F-E1 pairs order to ready and order to served, weights per unit and excludes remakes from the original-order distribution while reporting them separately. Ready population four: durations 10,10,20,20 minutes, mean 15. Served population two: 15,15 minutes, mean 15; served timing coverage 2/4, with two missing observations. Ready events do not fill the missing served timestamps.

Correct P1 served event to 19:16 via supersession: current served mean 16, population still two and uniquely served quantity still two. Record a one-unit replacement remake for P1, ready 19:25 and served 19:27. Keep its original fulfilment link; no additional order or charged quantity is evidenced. Unique original fulfilment remains two served units; remake population is separately one replacement event/unit. Under F-E1 it does not increase the original served sample to three. Changing weighting or remake policy requires a different explicit definition/version.

## AT-11.DUE — Expected controls and observed outcomes

**Trace:** DR-043/DR-055; FR-047; MET-012; CON-004; review R09. UJ-07.

Fixture F-C1 adopts a control for three identified services, each due before 18:00; no regulatory obligation is asserted. At 19:00: O1 was applicable/due and has no observation; O2 has evidenced not-applicable status; O3 was applicable/due and has a passing observation at 18:30. Expected applicable population two, on-time fulfilled zero, still-unfulfilled overdue one, late-fulfilled one, observed pass one and evidenced not-applicable one. A metric counting deadline misses (two) must be named separately from currently unfulfilled overdue occurrences (one). Passing evidence does not erase O3's lateness, and O1's missing evidence alone does not prove a breach. A separate unknown-applicability occurrence stays unresolved outside the confirmed denominator.

## AT-01.EFFORT — Report-linked person-time

**Trace:** DR-044/DR-048; MET-015; BG-01; review R10. UJ-01.

Two contributors jointly assemble report instance R revision 1 for thirty minutes: observation E1, contributor count 2, person-time 60 minutes. One contributor later corrects/reissues revision 2 for sixty minutes: E2, person-time 60 minutes, rework stage. In a declared comparison window covering both revisions, total effort is 120 person-minutes = two person-hours. If tasks are sequential with no intervening delay, elapsed working time is ninety minutes, not two hours. Checking stage is explicitly observed zero in this fixture, not missing evidence. A shared observation allocated across reports must conserve total contributor effort rather than charge the whole observation to each. Estimated effort stays estimated. Before/after savings require equivalent definition/scope/coverage, window and observed baseline; revision 2 correction cannot be omitted merely because revision 1 was ready earlier.

## AT-08.BOUNDARY — Candidate interval convention

**Trace:** BR-002; DR-003; review R12.

Preserve a fact's occurrence at an exact fixture boundary 18:00 and its source period semantics. Prior to OD-03 adoption, canonical attribution is unresolved. Fixture policy F-B1 explicitly selects inclusive-start/exclusive-end for adjacent partitions [17:00,18:00) and [18:00,19:00). After binding F-B1, the fact belongs exactly once to the second partition with policy/version retained. Recorded-at time cannot replace occurrence, and the source period label is not overwritten. F-B1 demonstrates the candidate only; it does not resolve Goldy's OD-03.
