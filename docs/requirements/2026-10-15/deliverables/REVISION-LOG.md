# Revision log — SRS v1.0 → v1.1

Date: 14 October 2026 (Australia/Melbourne).
Request: assess `SRS-REVIEW.md`, apply verified and justified documentation corrections, preserve stable requirement identifiers and traceability, record changes and disagreements, then stop.

## Evidence and decision boundary

Checked the review against PROJECT_CHARTER.md and the SRS baseline, domain model, role–KPI matrix, UX information architecture, requirements traceability and open-decision register. The charter explicitly requires atomic facts, minimum data contracts, verifiable requirements and business-led role/value chains, while prohibiting invented policies and implementation work. The review is diagnostic evidence, not stakeholder authority. No legal, tax, employment or valuation policy was adopted from the review or inferred from external standards; no new external applicability claim was made.

All twelve core findings are substantiated as documentation gaps or contradictions. Their rankings are the reviewer's risk assessment, not observed business losses or application defects. Corrections retain existing missingness, provenance, privacy, read-only and policy-validation safeguards. Seven independent assertion grains receive new IDs DR-049–DR-055; placing them inside generic DR-041 measurements would obscure the distinction between atomic evidence and results. All 210 pre-existing requirement IDs and titles, AC1 identities and upstream PRD/E/C/K/UJ/AT/OD mappings remain. The revised SRS has 217 requirements. New DR IDs have no invented PRD or E counterparts and are proposed or conditional as labelled.

Files updated: SRS.md (v1.1), DOMAIN-MODEL.md, ROLE-KPI-MATRIX.md, REQUIREMENTS-TRACEABILITY.md, UX-INFORMATION-ARCHITECTURE.md and OPEN-DECISIONS.md. Added SRS-VERIFICATION-FIXTURES.md and this log. The original review, charter, research, brief, PRD, underlying UX designs and historical implementation gap analysis remain unchanged.

## Findings, corrections and disagreements

### R01 — Accepted; recognition is distinct from purchase and cash

Verified BC-018/FR-038/MET-016 promise expense/profit explanation, but DR-018–DR-020/DR-036/DR-041 contain no atomic general recognition allocation. Added DR-049 (entry/line, entity/scope, classification version, recognised period/amount/basis, evidence and correction lineage) and explicit operational/accounting overlap reconciliation. Linked BC-018, DR-041, FR-038 and MET-016. AT-09.POS independently allocates a 1,200 prepaid cost to twelve periods (100 October expense), retains 10 non-cash expense and counts the 50 labour component/recognition only once.

**Qualification:** The baseline already prohibits substituting cash for profit and permits aggregate-only evidence; it did not positively mandate the category error. Retained aggregate-only fallback with grain disclosure. Recognition policy remains OD-08; fixture F-R1 is not its resolution.

### R02 — Accepted; valuation requires independent allocation facts

Verified DR-024/DR-027 define quantities and BR-003/MET-007 specify cost bases without detailed carrying-cost allocation. Added DR-050 for valued quantity/population, cost components/method/effective date, allocations and value-only corrections. Linked BC-008/BC-018, DR-024/DR-041, FR-028/FR-038 and MET-007/MET-016. AT-09.POS/REV reconcile sold/lost/remaining costs and a later acquisition credit without a new movement.

**Qualification:** Did not adopt FIFO, weighted average, loss classification or a legal inventory standard. Periodic weighted average and separate loss expense are explicit fixture-only choices under F-P1; OD-05/OD-08 remain open. Physical receipt, supplier credit, valuation and payment stay distinct.

### R03 — Accepted with a typed-source extension to the existing ID

Verified DR-012 explicitly required a money movement while DR-013's check link supplied no amount-conserving redemption settlement. Extended DR-012 to exactly one typed cash-movement or liability-release source, with target, amount/currency, effective time, conservation and reversal lineage. Linked DR-013 and FR-013/FR-014. Domain cardinalities and the conceptual ERD now show optional alternative source edges with an explicit exactly-one constraint. AT-05.REDEEM verifies one receipt, later zero-new-cash settlement and reversal restoring both liability and unpaid charge.

**Disagreement with an overly broad interpretation:** Retained the existing “Payment allocation” title/anchor and ID rather than reclassifying DR-011 or introducing a synthetic cash receipt. Non-cash extension is expressly documented; arbitrary non-cash instruments are not assumed. Reallocation of an earlier receipt cannot also settle a charge already settled through its funded liability.

### R04 — Accepted; availability has its own temporal identity

Verified availability and absence are required by BC-013/BC-014/FR-031/FR-033, but neither engagement, roster nor competency records specify dated declarations and conflicts. Added DR-051 with interval/recurrence/exception, status, scope, provenance, supersession and adopted precedence or unresolved conflict. Existing staffing journey and role information chains now consume it. AT-08.AVAIL uses a fixture recurrence/absence/partial-offer rule and preserves the published roster and private-reason boundary.

**Qualification:** A later partial offer does not automatically override an absence in production. F-A1 binds that precedence only for the example. Acceptance with unresolved precedence remains blocked/conflicted, and cover still needs external evidence; no new staffing authority or time interface is assigned.

### R05 — Accepted conditionally; distinguish three supply quantities

Verified FR-017's usable-stock rule and conditional receipt/lot facts do not define later quantity-specific holds or commitments. Added conditional DR-052, with holding population/quantity/unit, effective state, release history, demand link and overlap evidence; linked ordering and MET-014. AT-07.HOLD yields physical 20, usable 15 and uncommitted usable 7, plus release and overlap variants.

**Disagreement with a universal-reservation interpretation:** The example's seven portions represent uncommitted usable supply; usable stock alone is fifteen. Reservations and lot tracking are required only when the adopted rule depends on them, not for every item. Unknown overlap prevents an exact result; holds/releases do not imply physical loss.

### R06 — Accepted; split promises and scoring history need grain

Verified MET-014 needs promised/accepted quantity and time, but document/line dates cannot represent split tranches or scoring revisions. Added DR-053 with quantity/window promise identity, issuer/issue time, accepted revision evidence and receipt allocations; linked purchase-line, supply-horizon and supplier-result contracts. AT-06.PROMISE independently obtains nine accepted/one remaining case, and 30% original versus 70% accepted-revision quantity timeliness.

**Qualification:** Those percentages are an explicitly quantity-based fixture score, not a universal binary OTIF definition or supplier policy. The horizon and scoring baseline can differ but must each be declared; original and revised promises survive. OD-06/OD-09 remain open.

### R07 — Accepted; stage events are repeatable quantity observations

Verified sale-line quantity/time minima and FR-010/MET-003 do not identify repeated ready/served observations, corrections and remakes. Added DR-054 and linked DR-008/BC-005/FR-010/MET-003. AT-04.STAGE supplies per-unit ready/served populations, missing served coverage, timestamp supersession and separately classified replacement remake.

**Qualification:** Events across different stages of the same units are not additive fulfilment. Per-unit weighting and remake exclusion are fixture F-E1, not production defaults. Original-order served quantity remains two after a replacement remake; an evidenced additional order would have a different population.

### R08 — Accepted as an operationalisation of an existing safeguard

Verified BR-003's blanket-tax prohibition and tax-basis labels alone cannot reconstruct exclusive amounts. Extended BR-003 and DR-008/DR-010/DR-019 conditional tax evidence: observed exclusive amount or sufficient component/base/treatment, rate/observed tax, allocation, effective basis, rounding and credit lineage. Linked MET-001 and tax-inclusive/exclusive input qualifications. AT-05.TAX reconciles exclusive 140 + tax 9 = inclusive 149 after a partial credit.

**Qualification:** A known inclusive observation can remain complete at its own grain; only the unsupported exclusive derivation is blocked. Artificial 10% and zero-tax fixture treatments establish no Goldy's tax classification or universal rate.

### R09 — Accepted conditionally; expected controls precede observations

Verified DR-043 models observed evidence while MET-012 needs overdue/missing-work populations. Added conditional DR-055 for adopted obligation/version, trigger/schedule, independently identified expected occurrence, due window/applicability and fulfilment links; linked DR-043/BC-021/FR-047/MET-012. AT-11.DUE distinguishes missing, evidenced not-applicable and late passing observations.

**Qualification:** One still-unfulfilled overdue occurrence differs from two deadline misses in the fixture. A late pass remains late; missing evidence alone proves neither compliance nor a breach. No regulatory duty or pre-service schedule is adopted; OD-10 remains open.

### R10 — Accepted by extending workload evidence

Verified DR-044 generic effort and DR-048 report revisions do not expressly link contributor person-time/stage/shared allocations to report revisions. Extended those existing IDs and MET-015; updated the GM/report journey and OD-13 questions. AT-01.EFFORT independently yields 120 person-minutes (two person-hours) from two contributors for thirty minutes plus one hour correction/reissue.

**Qualification:** This is ninety minutes of sequential elapsed working time, not two elapsed hours. Checking is explicitly observed zero in this fixture, not omitted as unknown. Manual or privacy-safe count observations can supply evidence; no time-tracking interface is mandated. Estimated effort cannot silently become an observed savings baseline.

### R11 — Accepted; add a complete positive financial oracle

Verified AT-09 has useful cash/settlement and incomplete-profit cases but no numeric complete valuation-to-profit case. Added separate AT-09.POS/INC/REV subcases in SRS-VERIFICATION-FIXTURES.md and referenced them from relevant AC1 records and bidirectional input/KPI/journey traces. POS gives COGS 165, gross profit 135, gross margin 45% and bounded operating result −58. REV gives COGS 150, gross profit 150, margin 50% and result −40. INC separately removes valuation or expense evidence while retaining known unaffected values.

**Disagreement with an overstatement:** The original SRS does not allow an always-unavailable implementation to pass: every applicable acceptance condition is conjunctive. The verified gap is the absent independently executable numeric oracle. The new result is a complete explicitly bounded operating result, not an unqualified statutory/net-profit claim; all fixture scope exclusions are declared. Existing AT-09 cash/settlement conditions remain.

### R12 — Accepted; supporting wording contradicted the open policy

Verified DOMAIN-MODEL.md unconditionally said “Intervals use inclusive start/exclusive end” while BR-002 calls it a candidate and OD-03 is open. Corrected the domain statement to candidate status, preserved source period semantics and required adopted policy/version or unresolved attribution. Extended BR-002 verification with AT-08.BOUNDARY.

**Rejected alternative:** No stakeholder evidence authorises adopting the convention. The fixture demonstrates exactly-once assignment after binding F-B1; it does not close OD-03. Actual instants remain distinct from canonical attribution.

## Traceability and verification

Updated existing explicit capability/function/KPI dependencies, exact AC1 text and gates in REQUIREMENTS-TRACEABILITY.md. Added independent individual records for DR-049–DR-055, capability reverse links and review → input → function/result → fixture → journey chains. Historical implementation classifications are retained only for their original assessed records; new contracts have no direct implementation assessment. Updated domain minima/relationships, role inputs, existing journeys and open-decision subquestions/reverse lookup. No new screen, grant, KPI target or implementation sequence was introduced.

Checks performed on documentation: all 210 original requirement IDs/titles preserved; seven unused sequential DR IDs added; 217 unique SRS records match 217 individual trace records; exact acceptance and dependency text checked between the two; all twelve named fixture subcases resolve; local linked documents exist; introduced SRS anchors checked; quantity, cost, tax, holding, effort and service arithmetic independently calculated; whitespace/diff checks passed. The arithmetic verification is of the authored oracle, not application execution. Stakeholder validation, source fitness and operational acceptance remain pending. Work stops at this documentation revision.

## Previous wording and change history

The entries below retain the exact v1.0 clauses replaced in existing identified records; unquoted clauses survive unchanged in v1.1. New clauses are additions, not evidence of a prior policy. DR-049–DR-055 and the twelve worked AT subcases had no v1.0 predecessor; their current definitions are linked from the change register above. This log records documentation authority from the user's request, not stakeholder policy approval.

### BC-005 — retained v1.0 wording

> **Trace:** S01; S04 C05; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-009–FR-011.

### BC-006 — retained v1.0 wording

> **Trace:** S01; S04 C06; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-017–FR-019.

### BC-007 — retained v1.0 wording

> **Trace:** S01; S04 C07; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-020–FR-021.

### BC-008 — retained v1.0 wording

> **Trace:** S01; S04 C08; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-021–FR-022.

### BC-009 — retained v1.0 wording

> **Trace:** S01; S04 C09; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-023–FR-024.

### BC-013 — retained v1.0 wording

> **Trace:** S01; S04 C13; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-031; DR-029–DR-031.

### BC-014 — retained v1.0 wording

> **Trace:** S01; S04 C14; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-032–FR-034.

### BC-018 — retained v1.0 wording

> **Trace:** S01; S04 C18; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-001–FR-006; FR-038–FR-040.

### BC-021 — retained v1.0 wording

> **Trace:** S01; S04 C21; S06 capability coverage; BG-02/BG-03. **Dependencies:** OD-01; GOV-001; FR-047; DR-043.

### DR-008 — retained v1.0 wording

No prior clause removed; conditional evidence/relationship wording was added within this record. Existing v1.0 clauses are retained.

### DR-010 — retained v1.0 wording

No prior clause removed; conditional evidence/relationship wording was added within this record. Existing v1.0 clauses are retained.

### DR-012 — retained v1.0 wording

> **Grain/minimum:** One applied amount: payment, exactly one target identity/type, amount/currency, application direction/status. Cross-currency application requires conversion evidence.

> **Relationships/invariants:** Target is one check, supplier charge/credit, customer liability, pay obligation or settlement obligation. Many allocations are possible; total cannot silently exceed available payment.

> **Business rationale:** The amount of a money movement applied to a particular obligation or entitlement; supports split tenders, part payments and consolidated supplier payments.

> **Trace:** S01; S03 E12; S06 GOL-DR-012; PRD trace: S01; S04 E12; C03,C08,C16,C17. **Dependencies:** OD-08.

> **Verification — DR-012.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Part payments reconcile independently; missing conversion or unknown target produces unresolved/unapplied balance, never invented settlement.

### DR-013 — retained v1.0 wording

No prior clause removed; conditional evidence/relationship wording was added within this record. Existing v1.0 clauses are retained.

### DR-019 — retained v1.0 wording

No prior clause removed; conditional evidence/relationship wording was added within this record. Existing v1.0 clauses are retained.

### DR-024 — retained v1.0 wording

No prior clause removed; conditional evidence/relationship wording was added within this record. Existing v1.0 clauses are retained.

### DR-041 — retained v1.0 wording

> **Trace:** S01; S03 E41; S06 GOL-DR-041; PRD trace: S01; S04 E41; C18,C20,C22. **Dependencies:** OD-09.

### DR-043 — retained v1.0 wording

No prior clause removed; conditional evidence/relationship wording was added within this record. Existing v1.0 clauses are retained.

### DR-044 — retained v1.0 wording

> **Trace:** S01; S03 (explicit extension); S06 GOL-DR-044; PRD trace: S01; S02,S06; derived extension for workload/context/local records/reporting; C05,C14,C19. **Dependencies:** OD-04,OD-09.

> **Verification — DR-044.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Cocktail effort recorded as an estimate stays estimated; no evidence permits neither invented duration nor headcount-derived measured workload.

### DR-048 — retained v1.0 wording

No prior clause removed; conditional evidence/relationship wording was added within this record. Existing v1.0 clauses are retained.

### BR-002 — retained v1.0 wording

> **Verification — BR-002.AC1:** T/A/I: An unknown occurrence time is not replaced by receipt time. Overnight/DST elapsed work uses actual instants; a trading-day rule does not change payroll/calendar dates. Unresolved period assignment stays visible (OD-03).

### BR-003 — retained v1.0 wording

> **Verification — BR-003.AC1:** T/A/I: Cases, litres and serves are not added without conversions. Mixed-tax values are not assigned a blanket tax rule. Compatible totals reconcile within an agreed rounding tolerance; incompatible bases remain separate (OD-05,OD-08,OD-09).

### FR-010 — retained v1.0 wording

> **Trace:** S06 GOL-FR-010; PRD trace: S01,S04,S05; BG-02; C05; UJ-05. **Dependencies:** OD-01; OD-03,OD-09; DR-008–DR-009; DR-043.

> **Verification — FR-010.AC1:** T/A/D: Partial fulfilment remains distinguishable from complete fulfilment. Service duration uses observed order/fulfilment evidence; closure time does not fill a missing fulfilment time.

### FR-013 — retained v1.0 wording

> **Trace:** S06 GOL-FR-013; PRD trace: S01,S04,S05; BG-03; C03; UJ-06. **Dependencies:** OD-01; OD-02,OD-08; DR-007; DR-011–DR-012.

> **Verification — FR-013.AC1:** T/A/D: A 100-unit-currency check with a 60-unit allocation has a 40-unit unpaid balance on the compatible basis. An unapplied receipt remains unapplied; a closed check does not prove payment.

### FR-014 — retained v1.0 wording

> **Trace:** S06 GOL-FR-014; PRD trace: S03,S04,S05; BG-03; C03,C04; UJ-06. **Dependencies:** OD-01; OD-08,OD-10; DR-013; DR-037.

> **Verification — FR-014.AC1:** T/A/D: Issuance and redemption are not both counted as product sales. Unknown expiry does not become expiry; an over-redemption remains an exception pending an agreed treatment.

### FR-017 — retained v1.0 wording

> **Trace:** S06 GOL-FR-017; PRD trace: S02,S04,S05; BG-02; C06; UJ-04. **Dependencies:** OD-01; OD-05,OD-06; DR-017–DR-021; DR-024; DR-027; DR-042.

> **Verification — FR-017.AC1:** T/A/D: The analysis identifies current count/balance cutoff, usable-stock rule, outstanding receipts, lead-time assumptions and horizon. Unknown delivery time or unusable-stock status produces visible uncertainty.

### FR-018 — retained v1.0 wording

> **Trace:** S06 GOL-FR-018; PRD trace: S02,S04,S05; BG-02; C06,C11,C12,C19; UJ-04. **Dependencies:** OD-01; OD-05,OD-06,OD-09; DR-017; DR-021–DR-023; DR-028; DR-042.

### FR-019 — retained v1.0 wording

> **Trace:** S06 GOL-FR-019; PRD trace: S01,S04,S05; BG-02; C06; UJ-04. **Dependencies:** OD-01; OD-05,OD-06,OD-08; DR-016–DR-020.

> **Verification — FR-019.AC1:** T/A/D: A partially received order shows only remaining quantities as outstanding on matched units. A changed pack/quote retains effective dates; cost comparisons exclude incompatible tax/currency bases.

### FR-028 — retained v1.0 wording

> **Trace:** S06 GOL-FR-028; PRD trace: S01,S04,S05; BG-02; C11,C12; UJ-04. **Dependencies:** OD-01; OD-05,OD-08,OD-09; DR-017; DR-021–DR-023; DR-028.

### FR-031 — retained v1.0 wording

> **Trace:** S06 GOL-FR-031; PRD trace: S01,S02,S04; BG-02,BG-05; C13; UJ-03. **Dependencies:** OD-01; OD-02,OD-04,OD-07,OD-10; DR-029–DR-031; DR-043.

> **Verification — FR-031.AC1:** T/A/D: An employee title alone proves neither competency nor classification. Required-but-expired evidence and unknown availability are distinguished from valid evidence and confirmed unavailability.

### FR-033 — retained v1.0 wording

> **Trace:** S06 GOL-FR-033; PRD trace: S01,S02,S04; BG-02; C14; UJ-03. **Dependencies:** OD-01; OD-02,OD-04,OD-07; DR-029; DR-032–DR-033.

> **Verification — FR-033.AC1:** T/A/D: The originally published plan remains available when amended. A sickness-related unfilled slot remains unresolved until external evidence supports cover; sub-head-chef rostering authority is not inferred.

### FR-038 — retained v1.0 wording

> **Trace:** S06 GOL-FR-038; PRD trace: S01,S04,S05; BG-01,BG-03; C18; UJ-01,UJ-06. **Dependencies:** OD-01; OD-03,OD-08,OD-09; DR-003; DR-007–DR-028; DR-036; DR-040–DR-041.

> **Verification — FR-038.AC1:** T/A/D: Missing cost coverage produces a partial/estimated or unavailable profit measure. Recipe cost, purchasing, cash paid and final COGS remain distinguishable; valued waste is not subtracted twice.

### FR-047 — retained v1.0 wording

> **Trace:** S06 GOL-FR-047; PRD trace: S01,S03,S04; BG-05; C13,C21; UJ-08. **Dependencies:** OD-01; OD-02,OD-07,OD-10; DR-043.

> **Verification — FR-047.AC1:** T/A/D: A pass/fail assertion has an applicable criterion/version, subject and observation. Unknown applicability or missing observation remains a gap; neither becomes a compliance pass or established breach.

### INT-002 — retained v1.0 wording

> **Trace:** S01; S05–S07; BG-03/BG-05. **Dependencies:** DR-001–DR-048; BR-004; REL-003.

### MET-001 — retained v1.0 wording

> **Trace:** S04 K01. **Dependencies:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-001; DR-040–DR-042.

### MET-003 — retained v1.0 wording

> **Trace:** S04 K03. **Dependencies:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-003; DR-040–DR-042.

> **Verification:** A/T: independently recompute from the eligible fixture inputs. Missing service timestamp is excluded with visible coverage; check closure is not a proxy. Invalid time order is an exception, not negative duration silently averaged away.

### MET-007 — retained v1.0 wording

> **Trace:** S04 K07. **Dependencies:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-007; DR-040–DR-042.

> **Verification:** A/T: independently recompute from the eligible fixture inputs. Purchases/payments alone cannot supply COGS. Missing valuation/cost coverage makes profit partial/estimated or unavailable; waste is not double-subtracted. Markup uses cost denominator and is separately named.

### MET-012 — retained v1.0 wording

> **Trace:** S04 K12. **Dependencies:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-012; DR-040–DR-042.

> **Verification:** A/T: independently recompute from the eligible fixture inputs. Unknown applicability is reported separately from pass/fail or overdue. Locally marked complete but unverified work is not counted as verified resolution.

### MET-014 — retained v1.0 wording

> **Trace:** S06 analytical extension. **Dependencies:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-014; DR-040–DR-042.

> **Verification:** A/T: independently recompute from the eligible fixture inputs. Zero sales alone is not a stockout; a rejected delivery is not full usable supply. Unknown promised time prevents an on-time score; unknown excess policy prevents authoritative excess classification.

### MET-015 — retained v1.0 wording

> **Trace:** S06 analytical extension. **Dependencies:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-015; DR-040–DR-042.

> **Verification:** A/T: independently recompute from the eligible fixture inputs. Less preparation time with more hidden gaps or correction work is not unqualified improvement. No baseline or agreed deadline means the corresponding improvement/timeliness conclusion is unavailable.

### MET-016 — retained v1.0 wording

> **Trace:** S06 analytical extension. **Dependencies:** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-016; DR-040–DR-042.

> **Verification:** A/T: independently recompute from the eligible fixture inputs. Missing rent/other included expenses prevents a complete net-profit claim. Open purchase commitments remain commitments rather than paid expenses; cash received cannot replace revenue recognition evidence.

### Supporting documents — retained v1.0 scope statements

> Authority: SRS v1.0. No new product requirements are introduced. Candidate details inherited from planning remain proposed under their existing SRS IDs; any future addition must be explicitly recorded in OPEN-DECISIONS.md before adoption.

The supporting authority statements now identify v1.1, the new proposed DR assertion contracts and their revision/open-decision records. This is a documentation revision, not a stakeholder capability adoption.

Exact replaced DOMAIN-MODEL temporal clause:

> Intervals use inclusive start/exclusive end; unknown occurrence time is not recorded-at time.

Exact replaced REQUIREMENTS-TRACEABILITY change-boundary clause:

> No requirements were added, retired or renumbered.

The v1.1 register explicitly records seven additions, retained identifiers and unchanged historical crosswalks. Supporting grain, acceptance and dependency wording now mirrors the corresponding SRS record; the exact prior replaced normative clauses are retained above under their authoritative SRS IDs. Source registers and historical evidence meanings remain unchanged.

Additional exact v1.0 DOMAIN-MODEL E12 identity wording replaced to align the typed-source extension:

> **Business key / uniqueness:** Payment + allocation identity; several allocations to the same target may be legitimate, but duplicated evidence is not.

The E12 canonical definition mirrored the old DR-012 rationale quoted above and now includes liability releases.
