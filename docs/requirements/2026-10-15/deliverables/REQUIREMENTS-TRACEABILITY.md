# Requirements traceability — Goldy's Unified Data Platform

Date: 14 October 2026 (Australia/Melbourne). Status: supporting documentation; stakeholder validation and operational acceptance pending.

Authority: [SRS v1.1](SRS.md), especially its source precedence and obligation/maturity rules. Existing SRS identifiers are retained. These documents explain the baseline and its proposals; they do not approve a capability, assign a grant, choose architecture or establish implementation acceptance. Revision 1.1 extends proposed information contracts under DR-049–DR-055 and existing IDs, as recorded in [REVISION-LOG.md](REVISION-LOG.md). These supporting documents mirror that SRS revision; they do not independently adopt policies or approve capabilities. Validation questions remain in [OPEN-DECISIONS.md](OPEN-DECISIONS.md).

## Source register

- [Project charter](../PROJECT_CHARTER.md): domain coverage, role-to-value chain and documentation-only boundary.
- [Domain research](research/domain-australian-pub-operational-information-m-2026-10-14/research.md): derivation and applicability/evidence limitations.
- [Business-domain model](../research/business-domain-model.md) and [capability map](../research/business-capability-map.md): proposed E01–E43, C01–C22 and K01–K12.
- [BMAD brief](briefs/brief-goldys-requirements-2026-10-14/brief.md) and [brief addendum](briefs/brief-goldys-requirements-2026-10-14/addendum.md): reported duties, discovery examples and boundaries.
- [BMAD PRD](prds/prd-goldys-requirements-2026-10-14/prd.md) and [PRD addendum](prds/prd-goldys-requirements-2026-10-14/addendum.md): reporting intent, candidate journeys and open questions.
- [BMAD EXPERIENCE](ux-designs/ux-goldys-requirements-2026-10-14/EXPERIENCE.md) and [DESIGN](ux-designs/ux-goldys-requirements-2026-10-14/DESIGN.md): agreed contexts and responsive web, proposed compositions and interaction contracts.
- [Implementation gap analysis](IMPLEMENTATION-GAP-ANALYSIS.md): informative static evidence at commit `9d625c7f23c88112302acc0a89d7826a3443f462`; no runtime acceptance.

SRS source names S01–S08 retain the SRS namespace. A `PRD trace` uses the PRD's separate source namespace. E01 in the domain model is Venue; E01 in the gap analysis is its raw-ledger evidence bundle. Those namespaces must not be combined.

## Coverage and reading rules

This register covers **217 requirements**: BC 22, DR 55, BR 10, FR 54, NFR 12, ACC 8, UX 18, INT 7, REL 6, MET 16, GOV 3 and CON 6. Each retains its exact SRS ID, title, obligation/maturity, dependencies and one acceptance contract `<ID>.AC1`. Revision 1.1 extends relevant AC1 conditions and adds worked AT subcases; original child identities remain stable.

Trace order is business goal → capability → requirement → acceptance contract → available implementation evidence. Source-explicit links are retained; supplementary goal/capability applicability is labelled explanatory rather than a new normative dependency. Cross-cutting safeguards apply across the capabilities where relevant; they are not implementation sequence or self-dependencies. Requirement-level source traces retain the PRD source namespace when expressly labelled `PRD trace`.

MET-001–MET-016 use the SRS section 1.2/17 convention `<ID>.AC1`; their section 16 paragraphs are headed `Verification` without the explicit child label found on other records. The criteria reproduced here are those existing paragraphs. This naming clarification adds no requirement or acceptance content and does not edit the SRS.

Evidence status is separate from maturity and acceptance. The existing gap analysis individually assesses 154 PRD requirements plus 18 UX contracts at the reference commit. BC/INT/REL/GOV are later consolidation records with **no direct individual implementation assessment**. Related findings are indirect evidence only. No new application inspection or test execution was performed for this package; every operational acceptance result remains **not established**. Unknown applicability, missing authority or unbound QP criteria block the corresponding assertion; verified non-applicability needs an explicit rationale.

## Business-goal entry points

- **BG-01:** reduce report assembly, checking and rework while preserving correctness (FR-001–FR-006, FR-051–FR-053, MET-015).
- **BG-02:** support decisions with relevant qualified evidence and forecasts (FR-005, FR-007–FR-045, FR-048, FR-054, UX-001).
- **BG-03:** retain canonical atomic facts and comparable, explainable measurements (DR-001–DR-055, BR-001–BR-010, REL-001–REL-006).
- **BG-04:** inform opening decisions through explicit transfer assumptions (FR-043, FR-045, CON-005).
- **BG-05:** preserve authorised control, privacy and read-only sources (CON-001, CON-003–CON-004, ACC-001–ACC-008).

BG-01 reaches reporting/review/governance capabilities C18/C20/C22 and relevant C19 outlook/report comparisons. BG-02 and BG-03 reach the full operating capability set through BC-001–BC-022 and their downstream contracts. BG-04 reaches FR-043/FR-045, DR-042 and CON-005: opening planning uses C19/C20 evidence support plus relevant departmental capabilities, without creating a C23. BG-05 is a safeguard across authorised capability use, including ACC, GOV, CON and contextual/source controls. These are explanatory entry paths; exact source traces below retain their original goal references.

## Capability → requirement coverage

C01–C22 preserve the capability map. Capability verbs describe business work; Goldy's supports evidence/review and local records rather than executing external transactions. Every listed requirement is looked up in the individual register below, which supplies acceptance criteria and implementation evidence. Cross-cutting requirements appear across applicable rows by design.

### C01 / BC-001 — Manage the offer, availability and pricing

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-001, CON-002, DR-004, DR-005, DR-006, DR-008, DR-009, DR-022, FR-007, FR-011, FR-027, FR-028, FR-042, FR-054, MET-001, MET-007, MET-013, MET-014.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C02 / BC-002 — Manage customer sales and commercial exceptions

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-002, BR-010, CON-002, DR-004, DR-006, DR-007, DR-008, DR-009, DR-010, FR-012, MET-001, MET-011.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C03 / BC-003 — Manage tender, customer balances and stored value

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-003, BR-010, CON-002, DR-007, DR-010, DR-011, DR-012, DR-013, FR-013, FR-014, MET-011.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C04 / BC-004 — Manage reservations, attendance and capacity

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-004, CON-002, DR-002, DR-013, DR-037, DR-038, DR-039, FR-008, FR-009, FR-014, MET-002, MET-004.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C05 / BC-005 — Coordinate service and fulfilment

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-005, CON-002, DR-005, DR-008, DR-009, DR-038, DR-044, FR-009, FR-010, FR-011, FR-032, MET-003.

**Revision 1.1 derived assertion contracts:** DR-054.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C06 / BC-006 — Plan and control purchasing commitments

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-006, CON-002, DR-016, DR-017, DR-018, DR-019, FR-011, FR-017, FR-018, FR-019, FR-039, FR-042, FR-054, MET-014.

**Revision 1.1 derived assertion contracts:** DR-052, DR-053.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C07 / BC-007 — Receive and verify goods

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-007, CON-002, DR-016, DR-017, DR-018, DR-019, DR-020, DR-024, FR-020, FR-021, MET-011, MET-014.

**Revision 1.1 derived assertion contracts:** DR-053.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C08 / BC-008 — Validate supplier charges and manage amounts payable

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-008, BR-010, CON-002, DR-011, DR-012, DR-016, DR-018, DR-019, DR-020, FR-021, FR-022, MET-011, MET-016.

**Revision 1.1 derived assertion contracts:** DR-050.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C09 / BC-009 — Maintain stock custody, transfers and production records

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-009, BR-010, CON-002, DR-002, DR-021, DR-024, FR-023, FR-024, MET-005.

**Revision 1.1 derived assertion contracts:** DR-052.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C10 / BC-010 — Observe stock and resolve count discrepancies

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-010, CON-002, DR-021, DR-026, DR-027, FR-025, FR-026, MET-005, MET-011.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C11 / BC-011 — Manage recipes, yields and preparation economics

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-011, CON-002, DR-009, DR-017, DR-021, DR-022, DR-023, DR-028, FR-018, FR-024, FR-027, FR-028, MET-006.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C12 / BC-012 — Measure consumption, known waste and unexplained usage

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-012, BR-010, CON-002, DR-008, DR-021, DR-022, DR-023, DR-024, DR-025, DR-028, FR-018, FR-028, FR-029, FR-030, MET-006, MET-014.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C13 / BC-013 — Establish employment duties, availability and competency

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** ACC-003, BC-013, CON-002, CON-004, DR-029, DR-030, DR-031, DR-043, FR-031, FR-047, MET-012.

**Revision 1.1 derived assertion contracts:** DR-051.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C14 / BC-014 — Plan staffing and publish rosters

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-014, CON-002, DR-029, DR-030, DR-032, DR-033, DR-037, DR-044, FR-009, FR-032, FR-033, FR-034, FR-042, FR-054, MET-008.

**Revision 1.1 derived assertion contracts:** DR-051.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C15 / BC-015 — Record, review and approve actual work

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-015, BR-010, CON-002, DR-029, DR-034, DR-035, FR-034, FR-035, MET-008, MET-011.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C16 / BC-016 — Determine labour cost and settle pay obligations

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** ACC-003, ACC-006, BC-016, CON-002, CON-004, DR-011, DR-012, DR-031, DR-034, DR-035, DR-036, FR-034, FR-036, FR-037, MET-008, MET-016.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C17 / BC-017 — Reconcile cash, tenders and processor settlements

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-017, CON-002, DR-011, DR-012, DR-014, DR-015, FR-015, FR-016, MET-011.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C18 / BC-018 — Establish comparable financial performance

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** ACC-006, BC-018, CON-002, DR-003, DR-036, DR-039, DR-040, DR-041, DR-048, FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-036, FR-038, FR-039, FR-040, FR-051, FR-052, FR-054, MET-001, MET-002, MET-007, MET-008, MET-009, MET-016, NFR-004, NFR-008, NFR-012.

**Revision 1.1 derived assertion contracts:** DR-049, DR-050.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C19 / BC-019 — Set budgets, forecasts and performance targets

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-019, BR-006, CON-002, CON-005, DR-003, DR-032, DR-040, DR-042, DR-044, DR-045, FR-018, FR-032, FR-039, FR-040, FR-041, FR-042, FR-043, FR-044, FR-045, FR-046, FR-054, MET-009, MET-010, MET-013, MET-016, NFR-011.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C20 / BC-020 — Review operations and act on exceptions

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** ACC-004, BC-020, BR-007, BR-008, CON-002, CON-003, DR-041, DR-045, DR-046, DR-047, DR-048, FR-001, FR-003, FR-005, FR-044, FR-045, FR-046, FR-048, FR-049, FR-050, FR-051, FR-052, FR-054, MET-003, MET-010, MET-012, MET-015, NFR-001, NFR-002, NFR-006, NFR-007.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C21 / BC-021 — Manage applicable safety, licence and incident controls

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** BC-021, CON-002, CON-004, DR-043, FR-047, MET-012, NFR-009.

**Revision 1.1 derived assertion contracts:** DR-055.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

### C22 / BC-022 — Govern business definitions, evidence and responsible use

**Goals (source trace):** BG-02, BG-03. **Requirements from explicit C traces or BC dependencies:** ACC-001, ACC-002, ACC-003, ACC-004, ACC-005, ACC-006, ACC-007, ACC-008, BC-022, BR-001, BR-002, BR-003, BR-004, BR-005, BR-006, BR-007, BR-008, BR-009, BR-010, CON-001, CON-002, CON-003, CON-004, DR-001, DR-002, DR-003, DR-030, DR-040, DR-041, DR-043, DR-045, DR-046, DR-047, DR-048, FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-026, FR-035, FR-040, FR-045, FR-046, FR-048, FR-049, FR-050, FR-051, FR-052, FR-053, FR-054, MET-011, MET-012, MET-015, NFR-001, NFR-002, NFR-003, NFR-004, NFR-005, NFR-006, NFR-007, NFR-008, NFR-009, NFR-010, NFR-011, NFR-012.

**Inherited applicability:** CON-001–CON-005; BR-001–BR-010; applicable ACC-001–ACC-008; derived results DR-040–DR-042/REL-001–REL-006; external evidence INT-001–INT-006; exports INT-007; local records FR-049/FR-050 and OD-14; relevant NFR/UX/GOV. Applicability is conditional on the behaviour; not every contract is a sequential dependency.

## Individual requirement → acceptance → evidence register

Each entry's evidence is the prior static assessment, not a newly executed test or a declaration that `.AC1` passed. Gap links lead to business impact, source-versus-software distinction and inspected file/line evidence. Their E01–E30 labels belong to the implementation evidence namespace.

### GOV-001 — Validated responsibility records

**Authority / maturity:** [SRS GOV-001](SRS.md#gov-001--validated-responsibility-records); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S04–S07; BG-05.

**Dependencies / gates (source):** OD-02; OD-14.

**Acceptance — GOV-001.AC1:** I: inspect each adopted workflow for all named functions or an explicitly unresolved field. Unresolved authority blocks authority-dependent acceptance.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. The available assessment does not establish this complete contract. Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### GOV-002 — Decision value and outcome contract

**Authority / maturity:** [SRS GOV-002](SRS.md#gov-002--decision-value-and-outcome-contract); Mandatory — established.

**Goals (source-explicit):** BG-02. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S04; S07 GUX-01/09; BG-02.

**Dependencies / gates (source):** GOV-001; OD-01; OD-09; OD-12.

**Acceptance — GOV-002.AC1:** I/D: trace each adopted feature and primary destination through the complete chain with role holders; unresolved value prevents confirmed primary placement.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. The available assessment does not establish this complete contract. Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-001 — Manage the offer, availability and pricing

**Authority / maturity:** [SRS BC-001](SRS.md#bc-001--manage-the-offer-availability-and-pricing); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C01.

**Upstream trace:** S01; S04 C01; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-007; FR-027–FR-028; MET-001.

**Acceptance — BC-001.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-002 — Manage customer sales and commercial exceptions

**Authority / maturity:** [SRS BC-002](SRS.md#bc-002--manage-customer-sales-and-commercial-exceptions); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C02.

**Upstream trace:** S01; S04 C02; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-012; DR-007–DR-010.

**Acceptance — BC-002.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-003 — Manage tender, customer balances and stored value

**Authority / maturity:** [SRS BC-003](SRS.md#bc-003--manage-tender-customer-balances-and-stored-value); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C03.

**Upstream trace:** S01; S04 C03; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-013–FR-014; DR-011–DR-013.

**Acceptance — BC-003.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-004 — Manage reservations, attendance and capacity

**Authority / maturity:** [SRS BC-004](SRS.md#bc-004--manage-reservations-attendance-and-capacity); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C04.

**Upstream trace:** S01; S04 C04; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-008–FR-009; DR-037–DR-039.

**Acceptance — BC-004.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-005 — Coordinate service and fulfilment

**Authority / maturity:** [SRS BC-005](SRS.md#bc-005--coordinate-service-and-fulfilment); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C05.

**Upstream trace:** S01; S04 C05; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-009–FR-011; DR-054.

**Acceptance — BC-005.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-006 — Plan and control purchasing commitments

**Authority / maturity:** [SRS BC-006](SRS.md#bc-006--plan-and-control-purchasing-commitments); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C06.

**Upstream trace:** S01; S04 C06; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-017–FR-019; DR-052; DR-053.

**Acceptance — BC-006.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G16](IMPLEMENTATION-GAP-ANALYSIS.md#g16). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-007 — Receive and verify goods

**Authority / maturity:** [SRS BC-007](SRS.md#bc-007--receive-and-verify-goods); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C07.

**Upstream trace:** S01; S04 C07; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-020–FR-021; DR-053.

**Acceptance — BC-007.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-008 — Validate supplier charges and manage amounts payable

**Authority / maturity:** [SRS BC-008](SRS.md#bc-008--validate-supplier-charges-and-manage-amounts-payable); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C08.

**Upstream trace:** S01; S04 C08; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-021–FR-022; DR-050.

**Acceptance — BC-008.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-009 — Maintain stock custody, transfers and production records

**Authority / maturity:** [SRS BC-009](SRS.md#bc-009--maintain-stock-custody-transfers-and-production-records); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C09.

**Upstream trace:** S01; S04 C09; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-023–FR-024; DR-052.

**Acceptance — BC-009.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-010 — Observe stock and resolve count discrepancies

**Authority / maturity:** [SRS BC-010](SRS.md#bc-010--observe-stock-and-resolve-count-discrepancies); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C10.

**Upstream trace:** S01; S04 C10; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-025–FR-026.

**Acceptance — BC-010.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-011 — Manage recipes, yields and preparation economics

**Authority / maturity:** [SRS BC-011](SRS.md#bc-011--manage-recipes-yields-and-preparation-economics); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C11.

**Upstream trace:** S01; S04 C11; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-027–FR-028.

**Acceptance — BC-011.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-012 — Measure consumption, known waste and unexplained usage

**Authority / maturity:** [SRS BC-012](SRS.md#bc-012--measure-consumption-known-waste-and-unexplained-usage); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C12.

**Upstream trace:** S01; S04 C12; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-028–FR-030.

**Acceptance — BC-012.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-013 — Establish employment duties, availability and competency

**Authority / maturity:** [SRS BC-013](SRS.md#bc-013--establish-employment-duties-availability-and-competency); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C13.

**Upstream trace:** S01; S04 C13; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-031; DR-029–DR-031; DR-051.

**Acceptance — BC-013.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-014 — Plan staffing and publish rosters

**Authority / maturity:** [SRS BC-014](SRS.md#bc-014--plan-staffing-and-publish-rosters); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C14.

**Upstream trace:** S01; S04 C14; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-032–FR-034; DR-051.

**Acceptance — BC-014.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-015 — Record, review and approve actual work

**Authority / maturity:** [SRS BC-015](SRS.md#bc-015--record-review-and-approve-actual-work); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C15.

**Upstream trace:** S01; S04 C15; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-034–FR-035.

**Acceptance — BC-015.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-016 — Determine labour cost and settle pay obligations

**Authority / maturity:** [SRS BC-016](SRS.md#bc-016--determine-labour-cost-and-settle-pay-obligations); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C16.

**Upstream trace:** S01; S04 C16; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-036–FR-037.

**Acceptance — BC-016.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-017 — Reconcile cash, tenders and processor settlements

**Authority / maturity:** [SRS BC-017](SRS.md#bc-017--reconcile-cash-tenders-and-processor-settlements); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C17.

**Upstream trace:** S01; S04 C17; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-015–FR-016.

**Acceptance — BC-017.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-018 — Establish comparable financial performance

**Authority / maturity:** [SRS BC-018](SRS.md#bc-018--establish-comparable-financial-performance); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C18.

**Upstream trace:** S01; S04 C18; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-001–FR-006; FR-038–FR-040; DR-049; DR-050.

**Acceptance — BC-018.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02), [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G22](IMPLEMENTATION-GAP-ANALYSIS.md#g22), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-019 — Set budgets, forecasts and performance targets

**Authority / maturity:** [SRS BC-019](SRS.md#bc-019--set-budgets-forecasts-and-performance-targets); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C19.

**Upstream trace:** S01; S04 C19; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-039; FR-041–FR-045.

**Acceptance — BC-019.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G16](IMPLEMENTATION-GAP-ANALYSIS.md#g16), [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-020 — Review operations and act on exceptions

**Authority / maturity:** [SRS BC-020](SRS.md#bc-020--review-operations-and-act-on-exceptions); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C20.

**Upstream trace:** S01; S04 C20; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-005; FR-048–FR-050.

**Acceptance — BC-020.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-021 — Manage applicable safety, licence and incident controls

**Authority / maturity:** [SRS BC-021](SRS.md#bc-021--manage-applicable-safety-licence-and-incident-controls); Conditional mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C21.

**Upstream trace:** S01; S04 C21; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-047; DR-043; DR-055.

**Acceptance — BC-021.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BC-022 — Govern business definitions, evidence and responsible use

**Authority / maturity:** [SRS BC-022](SRS.md#bc-022--govern-business-definitions-evidence-and-responsible-use); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S04 C22; S06 capability coverage; BG-02/BG-03.

**Dependencies / gates (source):** OD-01; GOV-001; FR-006; FR-048–FR-050; BR-001–BR-010.

**Acceptance — BC-022.AC1:** I/D: inspect a capability-to-information-to-decision chain and demonstrate its linked functional acceptance cases; unavailable evidence must remain visible.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03), [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-001 — Venue

**Authority / maturity:** [SRS DR-001](SRS.md#dr-001--venue); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S03 E01; S06 GOL-DR-001; PRD trace: S01; S04 E01; C22.

**Dependencies / gates (source):** OD-03,OD-10.

**Acceptance — DR-001.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown operator/jurisdiction leaves obligations unresolved, without erasing known venue activity.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Single venue timezone configuration exists; enduring venue identity/operator/jurisdiction/history contracts are incomplete. Impact/evidence/direction: [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-002 — Operational location

**Authority / maturity:** [SRS DR-002](SRS.md#dr-002--operational-location); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C04, C09, C22.

**Upstream trace:** S01; S03 E02; S06 GOL-DR-002; PRD trace: S01; S04 E02; C04,C09,C22.

**Dependencies / gates (source):** OD-02,OD-03,OD-05.

**Acceptance — DR-002.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. An unknown area remains unassigned; parent/child aggregation counts activity once.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Area/table/department labels exist; canonical location hierarchy, capacities and stockholding identity do not. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-003 — Reporting period

**Authority / maturity:** [SRS DR-003](SRS.md#dr-003--reporting-period); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C18, C19, C22.

**Upstream trace:** S01; S03 E03; S06 GOL-DR-003; PRD trace: S01; S04 E03; C18,C19,C22.

**Dependencies / gates (source):** OD-03,OD-09.

**Acceptance — DR-003.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. A midnight-crossing example is assigned by an explicit agreed rule while its original timestamp remains intact; unresolved assignment is shown.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Date ranges and lunch/dinner classification exist; separate policy/versioned operational/pay/accounting periods do not. Impact/evidence/direction: [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-004 — Product

**Authority / maturity:** [SRS DR-004](SRS.md#dr-004--product); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C01, C02.

**Upstream trace:** S01; S03 E04; S06 GOL-DR-004; PRD trace: S01; S04 E04; C01,C02.

**Dependencies / gates (source):** OD-03,OD-05.

**Acceptance — DR-004.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. An unidentified product leaves an unmapped sale and incomplete product mix, not deleted revenue; identical names do not force a merge.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Canonical product IDs exist; product-day totals still key names, and sale unit/variant/effective category links are incomplete. Impact/evidence/direction: [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-005 — Menu offering version

**Authority / maturity:** [SRS DR-005](SRS.md#dr-005--menu-offering-version); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C01, C05.

**Upstream trace:** S01; S03 E05; S06 GOL-DR-005; PRD trace: S01; S04 E05; C01,C05.

**Dependencies / gates (source):** OD-03,OD-05,OD-10.

**Acceptance — DR-005.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. A sale can be known without menu evidence; unknown availability/allergen evidence is not treated as available/allergen-free.

**Implementation evidence — prior individual assessment:** **4. Present in raw data but not canonicalised**; severity **High**. OpenTable menu intake is raw-only; product catalogue/derived menu state is not a canonical effective offering context. Impact/evidence/direction: [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-006 — Price term

**Authority / maturity:** [SRS DR-006](SRS.md#dr-006--price-term); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C01, C02.

**Upstream trace:** S01; S03 E06; S06 GOL-DR-006; PRD trace: S01; S04 E06; C01,C02.

**Dependencies / gates (source):** OD-03,OD-08.

**Acceptance — DR-006.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Zero price is valid; today’s price never recalculates past sales. Unknown intended price makes list-price reduction unavailable.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Product sell price and charged line price exist; independently eligible dated/contextual price terms are absent. Impact/evidence/direction: [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-007 — Customer check

**Authority / maturity:** [SRS DR-007](SRS.md#dr-007--customer-check); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C02, C03.

**Upstream trace:** S01; S03 E07; S06 GOL-DR-007; PRD trace: S01; S04 E07; C02,C03.

**Dependencies / gates (source):** OD-03,OD-08.

**Acceptance — DR-007.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Closed does not prove paid/fulfilled. Aggregate-only check evidence cannot establish missing product quantities or covers.

**Implementation evidence — prior individual assessment:** **4. Present in raw data but not canonicalised**; severity **High**. All-sales raw records preserve check identifiers/totals but collapse to daily canonical sales; no check lifecycle/split/merge entity exists. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-008 — Sale line

**Authority / maturity:** [SRS DR-008](SRS.md#dr-008--sale-line); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C01, C02, C05, C12.

**Upstream trace:** S01; S03 E08; S06 GOL-DR-008; PRD trace: S01; S04 E08; C01,C02,C05,C12.

**Dependencies / gates (source):** OD-03,OD-05,OD-08.

**Acceptance — DR-008.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Missing fulfilment means unknown, not unfulfilled. Financial reversal never automatically restocks; zero-price fulfilled lines can consume stock.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Canonical receipt-line identity and amounts/quantities exist; parent check, status partitions, fulfilment, units and unknown handling are incomplete. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Revised input/semantic contract:** [SRS DR-008](SRS.md#dr-008--sale-line); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-009 — Modifier selection

**Authority / maturity:** [SRS DR-009](SRS.md#dr-009--modifier-selection); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C01, C02, C05, C11.

**Upstream trace:** S01; S03 E09; S06 GOL-DR-009; PRD trace: S01; S04 E09; C01,C02,C05,C11.

**Dependencies / gates (source):** OD-03,OD-05.

**Acceptance — DR-009.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Price effect counts once; unknown substitution mapping leaves theoretical ingredient usage incomplete even when charged amount is known.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. is_modifier on a catalogue product does not identify a selected modifier occurrence/target/effect on a sale line. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-010 — Commercial adjustment

**Authority / maturity:** [SRS DR-010](SRS.md#dr-010--commercial-adjustment); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C02, C03.

**Upstream trace:** S01; S03 E10; S06 GOL-DR-010; PRD trace: S01; S04 E10; C02,C03.

**Dependencies / gates (source):** OD-02,OD-03,OD-08.

**Acceptance — DR-010.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Amount-only credit creates neither stock return nor cash refund; unknown reason stays unknown and unresolved excess remains visible.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Deleted-order and source tax/discount fragments do not provide signed check/line adjustment allocations with independent reversal/approval basis. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Revised input/semantic contract:** [SRS DR-010](SRS.md#dr-010--commercial-adjustment); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-011 — Money movement

**Authority / maturity:** [SRS DR-011](SRS.md#dr-011--money-movement); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C03, C08, C16, C17.

**Upstream trace:** S01; S03 E11; S06 GOL-DR-011; PRD trace: S01; S04 E11; C03,C08,C16,C17.

**Dependencies / gates (source):** OD-08,OD-10.

**Acceptance — DR-011.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Internal transfers are not revenue; pending authorisation is not settled cash. Unknown method does not invalidate an evidenced receipt; unknown target leaves it unapplied.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Canonical tender facts exist; generic transfer/refund/supplier/pay/settlement direction/status and endpoint contracts do not. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-012 — Payment allocation

**Authority / maturity:** [SRS DR-012](SRS.md#dr-012--payment-allocation); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C03, C08, C16, C17.

**Upstream trace:** S01; S03 E12; S06 GOL-DR-012; PRD trace: S01; S04 E12; C03,C08,C16,C17.

**Dependencies / gates (source):** OD-08; OD-03.

**Acceptance — DR-012.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Part payments reconcile independently; missing conversion or unknown target produces unresolved/unapplied balance, never invented settlement. AT-05.REDEEM verifies original receipt, non-cash redemption and its reversal with independent cash, liability and check balances.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. saleNumber on payment rows is not an independent partial/many-target payment allocation with balance invariants. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Revised input/semantic contract:** [SRS DR-012](SRS.md#dr-012--payment-allocation); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-013 — Customer liability entry

**Authority / maturity:** [SRS DR-013](SRS.md#dr-013--customer-liability-entry); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C03, C04.

**Upstream trace:** S01; S03 E13; S06 GOL-DR-013; PRD trace: S01; S04 E13; C03,C04.

**Dependencies / gates (source):** OD-08,OD-10.

**Acceptance — DR-013.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Pooled evidence cannot prove individual redemption balances; unknown expiry never means expired. Redemption/issuance are not duplicate sales.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Reservation prepayment amounts/status exist; deposit/voucher/credit entry and linked redemption balance contracts do not. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Revised input/semantic contract:** [SRS DR-013](SRS.md#dr-013--customer-liability-entry); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-014 — Cash reconciliation observation

**Authority / maturity:** [SRS DR-014](SRS.md#dr-014--cash-reconciliation-observation); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C17.

**Upstream trace:** S01; S03 E14; S06 GOL-DR-014; PRD trace: S01; S04 E14; C17.

**Dependencies / gates (source):** OD-02,OD-08.

**Acceptance — DR-014.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. No count is unknown rather than zero; missing expected holding prevents variance, not observed cash reporting.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No independent cash holding count/session/cutoff with expected-movement basis exists. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-015 — Settlement component

**Authority / maturity:** [SRS DR-015](SRS.md#dr-015--settlement-component); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C17.

**Upstream trace:** S01; S03 E15; S06 GOL-DR-015; PRD trace: S01; S04 E15; C17.

**Dependencies / gates (source):** OD-08.

**Acceptance — DR-015.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. A bank deposit without statement detail remains unmatched, not reconstructed gross sales; components explain net settlement with declared signs.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No provider settlement-group/component and bank-allocation model exists. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-016 — Supplier

**Authority / maturity:** [SRS DR-016](SRS.md#dr-016--supplier); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C06, C07, C08.

**Upstream trace:** S01; S03 E16; S06 GOL-DR-016; PRD trace: S01; S04 E16; C06,C07,C08.

**Dependencies / gates (source):** OD-06,OD-10.

**Acceptance — DR-016.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Name similarity does not merge suppliers; unknown contact/tax identity does not erase a known receipt or claim verified compliance.

**Implementation evidence — prior individual assessment:** **4. Present in raw data but not canonicalised**; severity **High**. Supplier master is fetched raw; invoice supplierName does not supply enduring qualified counterparty identity/history. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-017 — Supplier item and pack term

**Authority / maturity:** [SRS DR-017](SRS.md#dr-017--supplier-item-and-pack-term); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C06, C07, C11.

**Upstream trace:** S01; S03 E17; S06 GOL-DR-017; PRD trace: S01; S04 E17; C06,C07,C11.

**Dependencies / gates (source):** OD-05,OD-06,OD-08.

**Acceptance — DR-017.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown conversion preserves pack evidence but prevents base-stock/per-litre claims; a replacement pack is not treated as the old size.

**Implementation evidence — prior individual assessment:** **4. Present in raw data but not canonicalised**; severity **High**. Reference measurement/conversion and stock/supplier datasets are raw-only; PDF pack fragments are not effective supplier-item conversion terms. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-018 — Purchase document

**Authority / maturity:** [SRS DR-018](SRS.md#dr-018--purchase-document); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C06, C07, C08.

**Upstream trace:** S01; S03 E18; S06 GOL-DR-018; PRD trace: S01; S04 E18; C06,C07,C08.

**Dependencies / gates (source):** OD-06,OD-08.

**Acceptance — DR-018.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. A receipt without order/invoice remains a physical fact. Missing detail supports only a labelled document total, not invented quantities; service invoices need not create stock.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Invoice header exists; separately typed order/receipt/credit, issuer-qualified identity and status do not. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-019 — Purchase document line

**Authority / maturity:** [SRS DR-019](SRS.md#dr-019--purchase-document-line); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C06, C07, C08.

**Upstream trace:** S01; S03 E19; S06 GOL-DR-019; PRD trace: S01; S04 E19; C06,C07,C08.

**Dependencies / gates (source):** OD-05,OD-06,OD-08.

**Acceptance — DR-019.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown receipt price does not invalidate measured acceptance; unknown received quantity cannot be derived from invoiced money or post trustworthy stock.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Invoice line and enrichment exist; typed order/receipt disposition and unknown price/quantity semantics are incomplete. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Revised input/semantic contract:** [SRS DR-019](SRS.md#dr-019--purchase-document-line); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-020 — Purchase match allocation

**Authority / maturity:** [SRS DR-020](SRS.md#dr-020--purchase-match-allocation); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C07, C08.

**Upstream trace:** S01; S03 E20; S06 GOL-DR-020; PRD trace: S01; S04 E20; C07,C08.

**Dependencies / gates (source):** OD-06,OD-08.

**Acceptance — DR-020.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Document match cannot prove line agreement; over-allocation is explicit. Missing matches leave independently known receipts/charges intact.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. CSV/PDF enrichment match is not a many-to-many typed purchase-document/line allocation. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-021 — Stock item

**Authority / maturity:** [SRS DR-021](SRS.md#dr-021--stock-item); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C09, C10, C11, C12.

**Upstream trace:** S01; S03 E21; S06 GOL-DR-021; PRD trace: S01; S04 E21; C09,C10,C11,C12.

**Dependencies / gates (source):** OD-05.

**Acceptance — DR-021.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown category does not erase known quantity; unknown base conversion prevents incompatible-pack aggregation.

**Implementation evidence — prior individual assessment:** **4. Present in raw data but not canonicalised**; severity **High**. Raw stock master exists; stock IDs on other entities do not provide an enduring base-unit/interchangeability stock-item contract. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-022 — Recipe version

**Authority / maturity:** [SRS DR-022](SRS.md#dr-022--recipe-version); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C01, C11, C12.

**Upstream trace:** S01; S03 E22; S06 GOL-DR-022; PRD trace: S01; S04 E22; C01,C11,C12.

**Dependencies / gates (source):** OD-05.

**Acceptance — DR-022.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown yield/components prevents complete theoretical usage/cost; recipe cost never becomes evidence of actual COGS.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Recipe-cost row and portion fields exist; positive output/effective version, yield completeness and attribution are incomplete. Impact/evidence/direction: [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-023 — Recipe component

**Authority / maturity:** [SRS DR-023](SRS.md#dr-023--recipe-component); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C11, C12.

**Upstream trace:** S01; S03 E23; S06 GOL-DR-023; PRD trace: S01; S04 E23; C11,C12.

**Dependencies / gates (source):** OD-05.

**Acceptance — DR-023.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unmapped ingredient remains descriptive evidence but cannot prove stock-specific usage. Negative quantities are invalid; zero requires meaningful omission.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Ingredients/sub-recipe structure exists; validated gross/net conversions, effective stock mapping and dependency/missing-link qualifications are incomplete. Impact/evidence/direction: [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-024 — Stock movement

**Authority / maturity:** [SRS DR-024](SRS.md#dr-024--stock-movement); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C07, C09, C12.

**Upstream trace:** S01; S03 E24; S06 GOL-DR-024; PRD trace: S01; S04 E24; C07,C09,C12.

**Dependencies / gates (source):** OD-02,OD-05.

**Acceptance — DR-024.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Incomplete item/location/quantity leaves balance incomplete; transfer nets item quantity venue-wide, and negative balances stay visible. Source changes are observed read-only.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No originating-fact item/location movement legs, counterpart groups or adjustment basis exists. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06).

**Revised input/semantic contract:** [SRS DR-024](SRS.md#dr-024--stock-movement); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-025 — Wastage observation

**Authority / maturity:** [SRS DR-025](SRS.md#dr-025--wastage-observation); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C12.

**Upstream trace:** S01; S03 E25; S06 GOL-DR-025; PRD trace: S01; S04 E25; C12.

**Dependencies / gates (source):** OD-05,OD-08.

**Acceptance — DR-025.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown quantity permits an incident, not a zero/complete measured loss; disposal/waste count once. A discrepancy alone never proves waste.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Waste row exists; scope/time precision, measured/estimated method, optional unknown quantity and disposal linkage are incomplete. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-026 — Stock count session

**Authority / maturity:** [SRS DR-026](SRS.md#dr-026--stock-count-session); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C10.

**Upstream trace:** S01; S03 E26; S06 GOL-DR-026; PRD trace: S01; S04 E26; C10.

**Dependencies / gates (source):** OD-02,OD-05.

**Acceptance — DR-026.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. A completed session with missing observations remains incomplete for full stock measurement; unknown cutoff limits reconciled balance claims.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Stocktake header has partial/locked flags; intended population, authoritative cutoff and full completion contract are absent. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-027 — Stock count observation

**Authority / maturity:** [SRS DR-027](SRS.md#dr-027--stock-count-observation); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C10.

**Upstream trace:** S01; S03 E27; S06 GOL-DR-027; PRD trace: S01; S04 E27; C10.

**Dependencies / gates (source):** OD-05.

**Acceptance — DR-027.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Zero is affirmative; no observation is uncounted. Unknown book quantity prevents variance, not physical reporting; duplicates/subcounts do not add twice.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Count lines retain source IDs/area/UOM, but missing/malformed qtyCount becomes zero and true cutoff/recount basis is incomplete. Impact/evidence/direction: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-028 — Consumption assessment

**Authority / maturity:** [SRS DR-028](SRS.md#dr-028--consumption-assessment); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C11, C12.

**Upstream trace:** S01; S03 E28; S06 GOL-DR-028; PRD trace: S01; S04 E28; C11,C12.

**Dependencies / gates (source):** OD-05,OD-09.

**Acceptance — DR-028.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Missing endpoints/recipes/movements yield incomplete/unavailable usage; residual minus theoretical is unexplained, not automatically known waste.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No versioned interval/method consumption assessment with eligible inputs/exclusions exists. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-029 — Employee

**Authority / maturity:** [SRS DR-029](SRS.md#dr-029--employee); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C13, C14, C15.

**Upstream trace:** S01; S03 E29; S06 GOL-DR-029; PRD trace: S01; S04 E29; C13,C14,C15.

**Dependencies / gates (source):** OD-02,OD-07,OD-10.

**Acceptance — DR-029.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown worker interval remains unresolved attendance; missing unnecessary private fields does not invalidate identified work.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. staffRef/name and login accounts do not provide a canonical enduring employee with employment/work relationships. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-030 — Position and work role

**Authority / maturity:** [SRS DR-030](SRS.md#dr-030--position-and-work-role); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C13, C14, C22.

**Upstream trace:** S01; S03 E30; S06 GOL-DR-030; PRD trace: S01; S04 E30; C13,C14,C22.

**Dependencies / gates (source):** OD-02,OD-07.

**Acceptance — DR-030.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Title-only evidence leaves duties/authority unresolved rather than granting approval/access or proving qualifications.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Software department/seniority codes are not duties/work-role definitions or delegation/competency evidence. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-031 — Employment assignment

**Authority / maturity:** [SRS DR-031](SRS.md#dr-031--employment-assignment); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C13, C16.

**Upstream trace:** S01; S03 E31; S06 GOL-DR-031; PRD trace: S01; S04 E31; C13,C16.

**Dependencies / gates (source):** OD-07,OD-10.

**Acceptance — DR-031.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Known attendance with unknown rate/classification remains usable work but unverified/incomplete pay; no award is inferred from workplace setting.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No employer engagement/terms/classification version distinguishes concurrent or re-engaged employment. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-032 — Roster version

**Authority / maturity:** [SRS DR-032](SRS.md#dr-032--roster-version); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C14, C19.

**Upstream trace:** S01; S03 E32; S06 GOL-DR-032; PRD trace: S01; S04 E32; C14,C19.

**Dependencies / gates (source):** OD-02,OD-04,OD-07.

**Acceptance — DR-032.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. No roster is not an empty confirmed plan. Actual attendance never overwrites published planned hours.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Daily scheduled hours and shift scaffold do not identify published/amended roster versions. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-033 — Planned shift

**Authority / maturity:** [SRS DR-033](SRS.md#dr-033--planned-shift); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C14.

**Upstream trace:** S01; S03 E33; S06 GOL-DR-033; PRD trace: S01; S04 E33; C14.

**Dependencies / gates (source):** OD-04,OD-07.

**Acceptance — DR-033.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unfilled slot is valid; missing planned break is not no break. Overlap is an explicit planning conflict, not automatic duplicate actual work.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Scaffold shiftStart/end/staffRef exists; roster/role/slot/break/allocation contract and production ingestion do not. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-034 — Timesheet

**Authority / maturity:** [SRS DR-034](SRS.md#dr-034--timesheet); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C15, C16.

**Upstream trace:** S01; S03 E34; S06 GOL-DR-034; PRD trace: S01; S04 E34; C15,C16.

**Dependencies / gates (source):** OD-02,OD-07.

**Acceptance — DR-034.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Missing approval means unapproved, not unworked; empty confirmed no-work differs from missing intervals. Approved is not paid.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No worker statement/version/status with observed intervals, approval and deduplication exists. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-035 — Actual work and break interval

**Authority / maturity:** [SRS DR-035](SRS.md#dr-035--actual-work-and-break-interval); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C15, C16.

**Upstream trace:** S01; S03 E35; S06 GOL-DR-035; PRD trace: S01; S04 E35; C15,C16.

**Dependencies / gates (source):** OD-03,OD-07.

**Acceptance — DR-035.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown clock-out means unknown duration, not rostered duration. Work/break overlaps remain unresolved and never double count; no break evidence means unknown break status.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Labour rows allow shift endpoints; independent actual work/break intervals, unknown ends and nonduplicating allocations are incomplete. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-036 — Labour cost and entitlement component

**Authority / maturity:** [SRS DR-036](SRS.md#dr-036--labour-cost-and-entitlement-component); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C16, C18.

**Upstream trace:** S01; S03 E36; S06 GOL-DR-036; PRD trace: S01; S04 E36; C16,C18.

**Dependencies / gates (source):** OD-07,OD-08.

**Acceptance — DR-036.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown on-cost scope prevents complete loaded labour cost; known hours remain available. Unallocated payment cannot prove entitlement settled.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Daily scheduled/actual cost values exist; engagement/earning-period component revisions and entitlement/payment lineage do not. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-037 — Reservation

**Authority / maturity:** [SRS DR-037](SRS.md#dr-037--reservation); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C04, C14.

**Upstream trace:** S01; S03 E37; S06 GOL-DR-037; PRD trace: S01; S04 E37; C04,C14.

**Dependencies / gates (source):** OD-03,OD-10.

**Acceptance — DR-037.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unknown party size makes booked covers incomplete; missing visit link alone never establishes no-show; no invented customer profile is required.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Rich canonical booking with source status/history exists; unknown party-size support and validated temporal/visit/eligibility semantics are incomplete. Impact/evidence/direction: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-038 — Visit and actual covers

**Authority / maturity:** [SRS DR-038](SRS.md#dr-038--visit-and-actual-covers); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C04, C05.

**Upstream trace:** S01; S03 E38; S06 GOL-DR-038; PRD trace: S01; S04 E38; C04,C05.

**Dependencies / gates (source):** OD-03,OD-09.

**Acceptance — DR-038.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Booking size never substitutes for actual count. Recount/table move is not new people; unknown size permits visit count, not complete covers.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Attended reservations are summarised as visits/covers without independent observed count or table/episode identity. Impact/evidence/direction: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-039 — Service allocation

**Authority / maturity:** [SRS DR-039](SRS.md#dr-039--service-allocation); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C04, C18.

**Upstream trace:** S01; S03 E39; S06 GOL-DR-039; PRD trace: S01; S04 E39; C04,C18.

**Dependencies / gates (source):** OD-03,OD-09.

**Acceptance — DR-039.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Unquantified link is not precise spend attribution; unlinked populations remain visible; covers count once per visit, not per check.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. OpenTable pos_check_ids and POS amounts exist as attributes; validated many-to-many visit/check/line allocation/coverage does not. Impact/evidence/direction: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-040 — Metric definition

**Authority / maturity:** [SRS DR-040](SRS.md#dr-040--metric-definition); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C18, C19, C22.

**Upstream trace:** S01; S03 E40; S06 GOL-DR-040; PRD trace: S01; S04 E40; C18,C19,C22.

**Dependencies / gates (source):** OD-03,OD-08,OD-09.

**Acceptance — DR-040.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Same label with different denominator/cost scope is not equivalent; undefined meaning cannot support an authoritative KPI.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Compiled metric definitions/version 1 exist; effective validated population/time/cost/missing-rule history is incomplete. Impact/evidence/direction: [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-041 — Measurement

**Authority / maturity:** [SRS DR-041](SRS.md#dr-041--measurement); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C18, C20, C22.

**Upstream trace:** S01; S03 E41; S06 GOL-DR-041; PRD trace: S01; S04 E41; C18,C20,C22.

**Dependencies / gates (source):** OD-09; DR-049; DR-050 for detailed financial operands.

**Acceptance — DR-041.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Aggregate-only detail is disclosed; incomplete costs cannot prove final profit; incompatible currencies/tax/cost bases do not silently combine.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Metric result/provenance exists; retained input/result revisions and complete unavailability/coverage/uncertainty contracts do not. Impact/evidence/direction: [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Revised input/semantic contract:** [SRS DR-041](SRS.md#dr-041--measurement); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-042 — Plan and target value

**Authority / maturity:** [SRS DR-042](SRS.md#dr-042--plan-and-target-value); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C19.

**Upstream trace:** S01; S03 E42; S06 GOL-DR-042; PRD trace: S01; S04 E42; C19.

**Dependencies / gates (source):** OD-04,OD-08,OD-09,OD-11.

**Acceptance — DR-042.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Revised forecast does not overwrite budget; absent approved comparator makes budget variance unavailable, not actual minus zero.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **Medium**. Persisted forecast runs exist; approved budget/target, scenario authority, scoped assumptions and comparable definition versions do not. Impact/evidence/direction: [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-043 — Operational control evidence

**Authority / maturity:** [SRS DR-043](SRS.md#dr-043--operational-control-evidence); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C13, C21, C22.

**Upstream trace:** S01; S03 E43; S06 GOL-DR-043; PRD trace: S01; S04 E43; C13,C21,C22.

**Dependencies / gates (source):** OD-02,OD-07,OD-10.

**Acceptance — DR-043.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. No pass/fail without applicability/criterion; missing evidence is not compliance/breach. Claimed corrective completion requires verification evidence.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **Medium**. No applicable control/incident/qualification subject and criterion/version evidence contract exists. Impact/evidence/direction: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Revised input/semantic contract:** [SRS DR-043](SRS.md#dr-043--operational-control-evidence); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-044 — Workload observation or assumption

**Authority / maturity:** [SRS DR-044](SRS.md#dr-044--workload-observation-or-assumption); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C05, C14, C19.

**Upstream trace:** S01; S03 (explicit extension); S06 GOL-DR-044; PRD trace: S01; S02,S06; derived extension for workload/context/local records/reporting; C05,C14,C19.

**Dependencies / gates (source):** OD-04,OD-09; OD-13 for report-effort claims; DR-048.

**Acceptance — DR-044.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Cocktail effort recorded as an estimate stays estimated; no evidence permits neither invented duration nor headcount-derived measured workload. AT-01.EFFORT verifies report-linked person-time, elapsed time and correction/reissue scope.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No observed/estimated product/task/preparation effort and competing-duty workload assertion exists. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Revised input/semantic contract:** [SRS DR-044](SRS.md#dr-044--workload-observation-or-assumption); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-045 — Contextual evidence item

**Authority / maturity:** [SRS DR-045](SRS.md#dr-045--contextual-evidence-item); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C19, C20, C22.

**Upstream trace:** S01; S03 (explicit extension); S06 GOL-DR-045; PRD trace: S01; S02,S06; derived extension for workload/context/local records/reporting; C19,C20,C22.

**Dependencies / gates (source):** OD-01,OD-10.

**Acceptance — DR-045.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Duplicated articles count once as evidence, not additional attendance; missing publication date is unknown, not receipt date. Source task completion does not alone verify operational outcome.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **Medium**. Reviews/reservation context exist; relevance/purpose, broader contextual types and verified external-work linkage are incomplete. Impact/evidence/direction: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-046 — Local annotation and correction proposal

**Authority / maturity:** [SRS DR-046](SRS.md#dr-046--local-annotation-and-correction-proposal); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S01; S03 (explicit extension); S06 GOL-DR-046; PRD trace: S01; S02,S06; derived extension for workload/context/local records/reporting; C20,C22.

**Dependencies / gates (source):** OD-02,OD-10,OD-14.

**Acceptance — DR-046.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Original remains retrievable; proposal is not a corrected source fact. Sensitive annotations follow the same access purpose as their contents.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Extracted invoice notes and operative overrides do not meet authored proposal/review states and independent hypothetical calculation semantics. Impact/evidence/direction: [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-047 — Local follow-up and decision record

**Authority / maturity:** [SRS DR-047](SRS.md#dr-047--local-follow-up-and-decision-record); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S01; S03 (explicit extension); S06 GOL-DR-047; PRD trace: S01; S02,S06; derived extension for workload/context/local records/reporting; C20,C22.

**Dependencies / gates (source):** OD-02,OD-14.

**Acceptance — DR-047.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. No owner is unassigned, not defaulted to a manager. Complete-without-evidence is unverified; local closure does not assert connected-system task execution.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No persisted local decision/follow-up ownership and evidence-backed lifecycle exists. Impact/evidence/direction: [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-048 — Report specification and report instance

**Authority / maturity:** [SRS DR-048](SRS.md#dr-048--report-specification-and-report-instance); Mandatory — proposed.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (source-explicit):** C18, C20, C22.

**Upstream trace:** S01; S03 (explicit extension); S06 GOL-DR-048; PRD trace: S01; S02,S06; derived extension for workload/context/local records/reporting; C18,C20,C22.

**Dependencies / gates (source):** OD-03,OD-09,OD-10,OD-13.

**Acceptance — DR-048.AC1:** I/T/A: inspect representative complete, incomplete and conflicting records and validate cardinality/conditional-field predicates. Two revisions of the same week are distinguishable. Missing contributing platform makes relevant results partial/unavailable and the report visibly qualified, including exports.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Dashboard query documents are not report specifications plus prepared instance/result revisions with readiness/coverage/sign-off. Impact/evidence/direction: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01).

**Revised input/semantic contract:** [SRS DR-048](SRS.md#dr-048--report-specification-and-report-instance); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### DR-049 — Accounting recognition assertion

**Authority / maturity:** [SRS DR-049](SRS.md#dr-049--accounting-recognition-assertion); Mandatory — proposed.

**Goals:** BG-02, BG-03. **Capabilities (revision derivation):** C18.

**Upstream trace:** S01 atomic facts and financial measurements; BC-018; FR-038; MET-016; review R01. No new PRD counterpart.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; DR-003; DR-018–DR-020; DR-036; DR-041; BR-003; REL-002/005.

**Acceptance — DR-049.AC1:** A/T/I: recompute spanning prepaid and separately evidenced non-cash expense using AT-09.POS; remove expense evidence using AT-09.INC. Duplicate labour/accounting coverage is reconciled once, not summed. Invoice/payment dates remain independent.

**Implementation evidence:** No direct individual assessment; these revision 1.1 contracts were not in the historical 154-requirement PRD assessment. Related gap findings do not establish implementation or acceptance.

**Acceptance result:** Not established; documentation only, resolve applicable OD gates and execute independent verification.

### DR-050 — Inventory valuation and cost allocation

**Authority / maturity:** [SRS DR-050](SRS.md#dr-050--inventory-valuation-and-cost-allocation); Mandatory — proposed.

**Goals:** BG-02, BG-03. **Capabilities (revision derivation):** C08, C18.

**Upstream trace:** S01 inventory, purchasing costs and atomic financial facts; BC-008/BC-018; FR-028/FR-038; MET-007; review R02. No new PRD counterpart.

**Dependencies / gates (source):** OD-03,OD-05,OD-08,OD-09; DR-019; DR-021; DR-024; DR-027; DR-041; BR-003; REL-002/005.

**Acceptance — DR-050.AC1:** A/T/I: independently reconcile quantities and opening/addition/return/loss/COGS/closing values in AT-09.POS, incomplete inputs in AT-09.INC and value-only credit restatement in AT-09.REV. Missing allocations cannot be replaced by invoice purchases.

**Implementation evidence:** No direct individual assessment; these revision 1.1 contracts were not in the historical 154-requirement PRD assessment. Related gap findings do not establish implementation or acceptance.

**Acceptance result:** Not established; documentation only, resolve applicable OD gates and execute independent verification.

### DR-051 — Availability and absence assertion

**Authority / maturity:** [SRS DR-051](SRS.md#dr-051--availability-and-absence-assertion); Mandatory — proposed.

**Goals:** BG-02, BG-03, BG-05. **Capabilities (revision derivation):** C13, C14.

**Upstream trace:** S01 workforce facts; reported staffing/absence need in S05–S06; BC-013/BC-014; FR-031/FR-033; review R04. No new PRD counterpart.

**Dependencies / gates (source):** OD-02,OD-03,OD-04,OD-07,OD-10; DR-029; DR-031–DR-033; ACC-003.

**Acceptance — DR-051.AC1:** T/I: apply the explicit fixture precedence to recurrence, dated absence and partial cover in AT-08.AVAIL. Without an adopted rule, disputed overlaps remain unresolved; neither superseded declaration nor private reason silently controls coverage.

**Implementation evidence:** No direct individual assessment; these revision 1.1 contracts were not in the historical 154-requirement PRD assessment. Related gap findings do not establish implementation or acceptance.

**Acceptance result:** Not established; documentation only, resolve applicable OD gates and execute independent verification.

### DR-052 — Holding condition and stock commitment assertion

**Authority / maturity:** [SRS DR-052](SRS.md#dr-052--holding-condition-and-stock-commitment-assertion); Conditional mandatory — proposed.

**Goals:** BG-02, BG-03. **Capabilities (revision derivation):** C06, C09.

**Upstream trace:** S01 inventory semantics; BC-006/BC-009; FR-017/FR-018; MET-014; review R05. No new PRD counterpart.

**Dependencies / gates (source):** OD-03,OD-05,OD-06; DR-021; DR-024; DR-027; DR-042; BR-003.

**Acceptance — DR-052.AC1:** A/T: AT-07.HOLD distinguishes twenty physical, fifteen usable and seven uncommitted usable portions; release changes availability only. Overlapping holds/commitments count the affected quantity once; unknown overlap stays qualified.

**Implementation evidence:** No direct individual assessment; these revision 1.1 contracts were not in the historical 154-requirement PRD assessment. Related gap findings do not establish implementation or acceptance.

**Acceptance result:** Not established; documentation only, resolve applicable OD gates and execute independent verification.

### DR-053 — Delivery schedule and promise assertion

**Authority / maturity:** [SRS DR-053](SRS.md#dr-053--delivery-schedule-and-promise-assertion); Mandatory — proposed.

**Goals:** BG-02, BG-03. **Capabilities (revision derivation):** C06, C07.

**Upstream trace:** S01 supplier/purchasing facts; BC-006/BC-007; FR-017/FR-019; MET-014; review R06. No new PRD counterpart.

**Dependencies / gates (source):** OD-03,OD-05,OD-06,OD-09; DR-018–DR-020; BR-003; REL-005.

**Acceptance — DR-053.AC1:** A/T: AT-06.PROMISE allocates partial receipts across two tranches, retains original/revised promise times and recomputes remaining quantities and selected-baseline timeliness; unresolved baseline prevents an authoritative supplier score.

**Implementation evidence:** No direct individual assessment; these revision 1.1 contracts were not in the historical 154-requirement PRD assessment. Related gap findings do not establish implementation or acceptance.

**Acceptance result:** Not established; documentation only, resolve applicable OD gates and execute independent verification.

### DR-054 — Service stage event

**Authority / maturity:** [SRS DR-054](SRS.md#dr-054--service-stage-event); Mandatory — proposed.

**Goals:** BG-02, BG-03. **Capabilities (revision derivation):** C05.

**Upstream trace:** S01 sales/service atomic facts; BC-005; FR-010; MET-003; review R07. No new PRD counterpart.

**Dependencies / gates (source):** OD-03,OD-09; DR-008–DR-009; DR-040; BR-002.

**Acceptance — DR-054.AC1:** A/T: recompute ready and served durations, correction and remake population in AT-04.STAGE. Missing served time affects served coverage only; correction creates neither an extra observation in the current population nor extra served units.

**Implementation evidence:** No direct individual assessment; these revision 1.1 contracts were not in the historical 154-requirement PRD assessment. Related gap findings do not establish implementation or acceptance.

**Acceptance result:** Not established; documentation only, resolve applicable OD gates and execute independent verification.

### DR-055 — Control obligation and expected occurrence

**Authority / maturity:** [SRS DR-055](SRS.md#dr-055--control-obligation-and-expected-occurrence); Conditional mandatory — proposed.

**Goals:** BG-02, BG-03, BG-05. **Capabilities (revision derivation):** C21.

**Upstream trace:** S01 operational assurance and explicit missingness; BC-021; FR-047; MET-012; review R09. No new PRD counterpart.

**Dependencies / gates (source):** OD-02,OD-03,OD-09,OD-10; DR-043; DR-040; CON-004.

**Acceptance — DR-055.AC1:** A/T/I: AT-11.DUE distinguishes due with no evidence, evidenced non-applicability and late passing evidence; expected and observed populations remain independently identifiable.

**Implementation evidence:** No direct individual assessment; these revision 1.1 contracts were not in the historical 154-requirement PRD assessment. Related gap findings do not establish implementation or acceptance.

**Acceptance result:** Not established; documentation only, resolve applicable OD gates and execute independent verification.

### BR-001 — Identity and duplicate evidence

**Authority / maturity:** [SRS BR-001](SRS.md#br-001--identity-and-duplicate-evidence); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-001; PRD trace: S01,S04; BG-03; C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-001.AC1:** T/A/I: Two identical legitimate sale lines remain two occurrences. Repeating the same source observation leaves business totals unchanged. Ambiguous identity/mapping is unresolved rather than guessed.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Some source-fact idempotency exists, but invoice-number and normalised-name identities can merge distinct business facts. Impact/evidence/direction: [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-002 — Temporal meaning and attribution

**Authority / maturity:** [SRS BR-002](SRS.md#br-002--temporal-meaning-and-attribution); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-002; PRD trace: S01,S04; BG-03; C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-002.AC1:** T/A/I: An unknown occurrence time is not replaced by receipt time. Overnight/DST elapsed work uses actual instants; a trading-day rule does not change payroll/calendar dates. Unresolved period assignment stays visible (OD-03). AT-08.BOUNDARY tests unresolved then singly assigned boundary attribution under an identified policy/version.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Revision timestamps exist; several valid-time values are receipt time, and trading/pay/forecast calendars are not consistently policy-bound. Impact/evidence/direction: [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Revised input/semantic contract:** [SRS BR-002](SRS.md#br-002--temporal-meaning-and-attribution); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-003 — Quantity, currency, tax and cost bases

**Authority / maturity:** [SRS BR-003](SRS.md#br-003--quantity-currency-tax-and-cost-bases); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-003; PRD trace: S01,S04; BG-03; C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-003.AC1:** T/A/I: Cases, litres and serves are not added without conversions. Mixed-tax values are not assigned a blanket tax rule. Compatible totals reconcile within an agreed rounding tolerance; incompatible bases remain separate (OD-05,OD-08,OD-09). AT-05.TAX independently reconciles mixed treatments, shared charges and partial credit; unsupported exclusive values stay unavailable.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Currency/tax/UOM fragments exist; guessed quantity/zero cost, cross-item blended units and purchases-as-COGS violate the required bases. Impact/evidence/direction: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03).

**Revised input/semantic contract:** [SRS BR-003](SRS.md#br-003--quantity-currency-tax-and-cost-bases); see revision 1.1 evidence chains below.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-004 — Known zero and absence states

**Authority / maturity:** [SRS BR-004](SRS.md#br-004--known-zero-and-absence-states); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-004; PRD trace: S01,S02,S04; BG-03; C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-004.AC1:** T/A/I: A confirmed zero stock count remains zero; no count remains uncounted. A disputed rate preserves conflicting evidence. Reports/exports do not coerce absence to zero, a false identity or artificial date.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Metric null/zero guards exist, but parser fallbacks fabricate zero/one and lose material absence states. Impact/evidence/direction: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-005 — History, correction and authority

**Authority / maturity:** [SRS BR-005](SRS.md#br-005--history-correction-and-authority); Mandatory — established.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-005; PRD trace: S01,S02,S04; BG-03,BG-05; C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-005.AC1:** T/A/I: A late invoice changes a distinguishable result revision, not the original published report unnoticed. A local proposed correction never proves that source history has changed; absent approval evidence cannot claim approval (OD-02,OD-14).

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Canonical supersession and some override/rule audits retain history; original published results, proposal authority and reason/lineage are incomplete. Impact/evidence/direction: [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-006 — Derived measures and uncertainty

**Authority / maturity:** [SRS BR-006](SRS.md#br-006--derived-measures-and-uncertainty); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C19, C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-006; PRD trace: S01,S02,S04; BG-03; C19,C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-006.AC1:** T/A/I: A known aggregate without atomic detail is labelled aggregate-only. A missing denominator makes a ratio unavailable with reason; a forecast is never rendered as an observed outcome. Relevant assumptions accompany comparisons (OD-09).

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Catalogue definitions, notices and quantiles exist; snapshots, eligible populations, scenario/final states and uncertainty lineage are incomplete. Impact/evidence/direction: [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-007 — Completeness by decision population

**Authority / maturity:** [SRS BR-007](SRS.md#br-007--completeness-by-decision-population); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-007; PRD trace: S01,S02; BG-03; C20,C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-007.AC1:** T/A/I: A report covering four of five known service periods states the omitted period and coverage basis. If total expected visits are unknown, it says denominator unknown; 100% ingestion does not prove complete business activity.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Missing dates/shared-day notices exist; expected business population and downstream summary qualification remain incomplete. Impact/evidence/direction: [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-008 — Freshness and decision eligibility

**Authority / maturity:** [SRS BR-008](SRS.md#br-008--freshness-and-decision-eligibility); Mandatory — established.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-008; PRD trace: S01,S02; BG-02,BG-03; C20,C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-008.AC1:** T/A/I: An old count newly received is not called fresh stock. If a decision rule is unset, eligibility is unvalidated; if violated, the result is qualified/unavailable under the agreed rule (OD-04,OD-06,OD-09,OD-13).

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Trust/ingestion timestamps and configured thresholds exist; evidenced coverage and validated decision-specific eligibility are incomplete. Impact/evidence/direction: [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-009 — Canonical meaning before source mapping

**Authority / maturity:** [SRS BR-009](SRS.md#br-009--canonical-meaning-before-source-mapping); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-009; PRD trace: S01,S04; BG-03; C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-009.AC1:** T/A/I: Two differently defined “sales” totals retain tax/time/adjustment differences. An unmapped receipt pack is retained as pack evidence but cannot silently enter a stock-unit balance.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Source concepts are mapped, but purchases/COGS, party size/covers and positive-price/menu availability are incorrectly equated. Impact/evidence/direction: [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03), [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### BR-010 — Single recognition and reconciled relationships

**Authority / maturity:** [SRS BR-010](SRS.md#br-010--single-recognition-and-reconciled-relationships); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C02, C03, C08, C09, C12, C15, C22.

**Upstream trace:** S01; S03 shared contracts; S06 GOL-SEM-010; PRD trace: S01,S04; BG-03; C02,C03,C08,C09,C12,C15,C22.

**Dependencies / gates (source):** none beyond the shared contracts.

**Acceptance — BR-010.AC1:** T/A/I: A discount parent and its allocations count once; transfer legs net venue-wide without new purchasing. A payroll estimate plus its final replacement is not double actual cost; unexplained residuals stay explicit.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Resolved-source selection and idempotency prevent some duplication; allocations, transfers, losses and estimate/final replacement relationships are missing. Impact/evidence/direction: [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-001 — Repeatable weekly business report

**Authority / maturity:** [SRS FR-001](SRS.md#fr-001--repeatable-weekly-business-report); Mandatory — proposed.

**Goals (source-explicit):** BG-01. **Capabilities (source-explicit):** C18, C20, C22.

**Upstream trace:** S06 GOL-FR-001; PRD trace: S06; BG-01; C18,C20,C22; UJ-01.

**Dependencies / gates (source):** OD-01; OD-03,OD-09,OD-13; DR-048; FR-002; FR-004.

**Acceptance — FR-001.AC1:** T/A/D: For an agreed example week, each selected measure has a defined scope/basis and matches its eligible evidence. Repeating preparation with unchanged evidence and definition yields the same business totals; preparation effort is measured against the agreed baseline.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Resolved consolidated measures and reusable dashboards exist; recurring weekly report acceptance, usable handoff and measured effort do not. Impact/evidence/direction: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-002 — Weekly report contract and version

**Authority / maturity:** [SRS FR-002](SRS.md#fr-002--weekly-report-contract-and-version); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-03. **Capabilities (source-explicit):** C18, C22.

**Upstream trace:** S06 GOL-FR-002; PRD trace: S06; BG-01,BG-03; C18,C22; UJ-01.

**Dependencies / gates (source):** OD-01; OD-03,OD-09,OD-13; DR-048; ACC-001.

**Acceptance — FR-002.AC1:** T/A/D: A report identifies its definition/version, week, venue/department scope, preparation/as-of time and provisional/final status. A changed calculation or period creates a distinguishable report definition/result revision.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Dashboard definitions/revisions are retained; report specification, prepared result revision and validated sign-off are missing. Impact/evidence/direction: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-003 — Automated report timing and readiness

**Authority / maturity:** [SRS FR-003](SRS.md#fr-003--automated-report-timing-and-readiness); Mandatory — proposed.

**Goals (source-explicit):** BG-01. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-FR-003; PRD trace: S06; BG-01; C20,C22; UJ-01.

**Dependencies / gates (source):** OD-01; OD-10,OD-13; FR-002; DR-048.

**Acceptance — FR-003.AC1:** T/A/D: A due report is either available with coverage/status or visibly failed/incomplete with reason and follow-up need. A late source has its own status; report timing, availability deadline and notification behaviour are validated before acceptance.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Scheduled ingestion does not prepare a due report or expose report-level deadline/readiness/failure. Impact/evidence/direction: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-004 — Reconciliation of contributing numbers

**Authority / maturity:** [SRS FR-004](SRS.md#fr-004--reconciliation-of-contributing-numbers); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-03. **Capabilities (source-explicit):** C18, C22.

**Upstream trace:** S06 GOL-FR-004; PRD trace: S01,S06; BG-01,BG-03; C18,C22; UJ-01,UJ-06.

**Dependencies / gates (source):** OD-01; OD-03,OD-08,OD-09,OD-13; BR-009; REL-002.

**Acceptance — FR-004.AC1:** T/A/D: Two sources covering the same activity do not double its total. A source total with a different time/tax scope is presented as a qualified comparison, with explainable differences rather than forced equality.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Sales resolution and invoice reconciliation exist; source-basis comparability, full contributor relationships and summary coverage remain incomplete. Impact/evidence/direction: [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-005 — Actionable business summaries

**Authority / maturity:** [SRS FR-005](SRS.md#fr-005--actionable-business-summaries); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-FR-005; PRD trace: S01,S06; BG-02; C20,C22; UJ-01,UJ-02.

**Dependencies / gates (source):** OD-01; OD-01,OD-02,OD-09,OD-12; GOV-002; DR-040–DR-041.

**Acceptance — FR-005.AC1:** T/A/D: Each summary identifies affected scope/period, measure or observation, comparator where used, business significance or decision question, and evidence qualification. Correlation is not described as a proven cause; no proposed action is automatically executed.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Home, attention queue and Ask Goldy's surface findings; responsibility, significance, qualification and next-decision/outcome chain are incomplete. Impact/evidence/direction: [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-006 — Trace from summary to evidence

**Authority / maturity:** [SRS FR-006](SRS.md#fr-006--trace-from-summary-to-evidence); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-FR-006; PRD trace: S01,S06; BG-03; C22; UJ-01,UJ-08.

**Dependencies / gates (source):** OD-01; OD-02,OD-09,OD-10; REL-001; DR-040–DR-041.

**Acceptance — FR-006.AC1:** T/A/D: A sample total can be reproduced from its retained definition and eligible inputs to the agreed rounding tolerance. Where only an aggregate is available, detail is explicitly unavailable rather than reconstructed; restricted detail remains protected.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Daily sales drill-down and invoice PDF links exist; other measure components, retained historical inputs and reproducible aggregate-only boundaries are incomplete. Impact/evidence/direction: [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-007 — Historical product, menu and price interpretation

**Authority / maturity:** [SRS FR-007](SRS.md#fr-007--historical-product-menu-and-price-interpretation); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C01.

**Upstream trace:** S06 GOL-FR-007; PRD trace: S01,S04,S05; BG-02,BG-03; C01; UJ-02,UJ-05.

**Dependencies / gates (source):** OD-01; OD-03,OD-05; DR-004–DR-006.

**Acceptance — FR-007.AC1:** T/A/D: A later price/menu/category change does not alter historical charged consideration. An unmapped product remains in known venue sales with product-level coverage disclosed.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Products and charged prices exist; effective offering/price/category/recipe attribution and availability are incomplete. Impact/evidence/direction: [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-008 — Bookings versus attendance and covers

**Authority / maturity:** [SRS FR-008](SRS.md#fr-008--bookings-versus-attendance-and-covers); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C04.

**Upstream trace:** S06 GOL-FR-008; PRD trace: S01,S02,S04; BG-02; C04; UJ-02,UJ-05.

**Dependencies / gates (source):** OD-01; OD-03,OD-09; DR-037–DR-039.

**Acceptance — FR-008.AC1:** T/A/D: A booking for six with four observed covers and two checks reports six booked, four actual and two checks. Missing arrival evidence is unresolved, not automatically a no-show.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Attended party counts exist, but covers use partySize rather than an independent actual-cover observation; visits/check allocations are absent. Impact/evidence/direction: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-009 — Demand and capacity comparison

**Authority / maturity:** [SRS FR-009](SRS.md#fr-009--demand-and-capacity-comparison); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C04, C05, C14.

**Upstream trace:** S06 GOL-FR-009; PRD trace: S04,S05; BG-02; C04,C05,C14; UJ-03,UJ-05.

**Dependencies / gates (source):** OD-01; OD-03,OD-04,OD-09; DR-002–DR-003; DR-033; DR-037–DR-039; DR-044.

**Acceptance — FR-009.AC1:** T/A/D: Seat/area utilisation is unavailable without capacity and relevant occupancy evidence. Split arrivals, table moves and split bills do not duplicate covers; demand and workload remain separate quantities.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Reservation service buckets and labour summaries exist; capacity, occupancy, workload/skills and nonduplicating attendance comparison do not. Impact/evidence/direction: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-010 — Fulfilment and service outcomes

**Authority / maturity:** [SRS FR-010](SRS.md#fr-010--fulfilment-and-service-outcomes); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C05.

**Upstream trace:** S06 GOL-FR-010; PRD trace: S01,S04,S05; BG-02; C05; UJ-05.

**Dependencies / gates (source):** OD-01; OD-03,OD-09; DR-008–DR-009; DR-043; DR-054.

**Acceptance — FR-010.AC1:** T/A/D: Partial fulfilment remains distinguishable from complete fulfilment. Service duration uses observed order/fulfilment evidence; closure time does not fill a missing fulfilment time. AT-04.STAGE verifies stage-specific partial durations and current-revision population.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Sale lines and seated/finished reservation fields are not order/amendment/partial-fulfilment stage evidence or measured item service outcomes. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-011 — Availability and unmet-demand evidence

**Authority / maturity:** [SRS FR-011](SRS.md#fr-011--availability-and-unmet-demand-evidence); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C01, C05, C06.

**Upstream trace:** S06 GOL-FR-011; PRD trace: S02,S05; BG-02; C01,C05,C06; UJ-04,UJ-05.

**Dependencies / gates (source):** OD-01; OD-03,OD-05,OD-10; DR-005; DR-021; DR-045.

**Acceptance — FR-011.AC1:** T/A/D: Zero parma sales alone creates no stockout or lost-sales claim. An availability incident records scope/time, affected offering/material and evidence; lost-demand estimates are explicitly qualified.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **Medium**. No availability-incident or evidenced unmet-demand capability supports a stockout/lost-sales assertion. Impact/evidence/direction: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-012 — Atomic sales and commercial exceptions

**Authority / maturity:** [SRS FR-012](SRS.md#fr-012--atomic-sales-and-commercial-exceptions); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C02.

**Upstream trace:** S06 GOL-FR-012; PRD trace: S01,S04,S05; BG-03; C02; UJ-01,UJ-06.

**Dependencies / gates (source):** OD-01; OD-03,OD-08; DR-007–DR-010.

**Acceptance — FR-012.AC1:** T/A/D: A prepared zero-price item can contribute consumption without revenue. An amount-only refund does not imply quantity returned, cash paid or stock replenished; reasons/approval are not invented.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Receipt lines, deleted orders and tenders exist; checks, modifier/adjustment allocations, fulfilment and missing-value semantics remain incomplete. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-013 — Tender allocation and unpaid balances

**Authority / maturity:** [SRS FR-013](SRS.md#fr-013--tender-allocation-and-unpaid-balances); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C03.

**Upstream trace:** S06 GOL-FR-013; PRD trace: S01,S04,S05; BG-03; C03; UJ-06.

**Dependencies / gates (source):** OD-01; OD-02,OD-08; DR-007; DR-011–DR-012; DR-013.

**Acceptance — FR-013.AC1:** T/A/D: A 100-unit-currency check with a 60-unit allocation has a 40-unit unpaid balance on the compatible basis. An unapplied receipt remains unapplied; a closed check does not prove payment. AT-05.REDEEM settles a charge without new cash and restores it on redemption reversal.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Tender facts and sale numbers exist; independent applied/unapplied allocations and reconciled unpaid balances do not. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-014 — Deposits, vouchers and credits

**Authority / maturity:** [SRS FR-014](SRS.md#fr-014--deposits-vouchers-and-credits); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C03, C04.

**Upstream trace:** S06 GOL-FR-014; PRD trace: S03,S04,S05; BG-03; C03,C04; UJ-06.

**Dependencies / gates (source):** OD-01; OD-08,OD-10; DR-013; DR-037; DR-012.

**Acceptance — FR-014.AC1:** T/A/D: Issuance and redemption are not both counted as product sales. Unknown expiry does not become expiry; an over-redemption remains an exception pending an agreed treatment. Redemption amounts and reversal lineage reconcile through DR-012 in AT-05.REDEEM.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. OpenTable prepayment fields are canonicalised, but no liability-entry/redemption/refund balance model separates these from sales. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-015 — Cash holding reconciliation

**Authority / maturity:** [SRS FR-015](SRS.md#fr-015--cash-holding-reconciliation); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C17.

**Upstream trace:** S06 GOL-FR-015; PRD trace: S04,S05; BG-03; C17; UJ-06.

**Dependencies / gates (source):** OD-01; OD-02,OD-08; DR-011; DR-014.

**Acceptance — FR-015.AC1:** T/A/D: A count of 98 against expected 100 reports observed-minus-expected variance of -2, without adjusting sales. Unknown expected holdings make variance unavailable.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No holding/session cash observation and expected-movement reconciliation exists. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-016 — Processor-to-bank settlement explanation

**Authority / maturity:** [SRS FR-016](SRS.md#fr-016--processor-to-bank-settlement-explanation); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C17.

**Upstream trace:** S06 GOL-FR-016; PRD trace: S01,S04,S05; BG-03; C17; UJ-06.

**Dependencies / gates (source):** OD-01; OD-08,OD-10; DR-011–DR-012; DR-015.

**Acceptance — FR-016.AC1:** T/A/D: A 100 gross settlement less 3 fee matched to a 97 bank receipt is explained without lowering product sales by 3. An unmatched bank deposit does not establish gross sales or fees.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Tender clearing fields do not supply processor statement components, bank receipts or matched settlement explanations. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-017 — Replenishment horizon and usable stock

**Authority / maturity:** [SRS FR-017](SRS.md#fr-017--replenishment-horizon-and-usable-stock); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C06.

**Upstream trace:** S06 GOL-FR-017; PRD trace: S02,S04,S05; BG-02; C06; UJ-04.

**Dependencies / gates (source):** OD-01; OD-05,OD-06; DR-017–DR-021; DR-024; DR-027; DR-042; DR-052; DR-053.

**Acceptance — FR-017.AC1:** T/A/D: The analysis identifies current count/balance cutoff, usable-stock rule, outstanding receipts, lead-time assumptions and horizon. Unknown delivery time or unusable-stock status produces visible uncertainty. AT-07.HOLD separates physical, usable and uncommitted supply; AT-06.PROMISE identifies outstanding scheduled quantities and horizon revisions.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No usable item/location stock, outstanding accepted supply and next-delivery horizon workflow exists. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-018 — Candidate purchase quantity explanation

**Authority / maturity:** [SRS FR-018](SRS.md#fr-018--candidate-purchase-quantity-explanation); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C06, C11, C12, C19.

**Upstream trace:** S06 GOL-FR-018; PRD trace: S02,S04,S05; BG-02; C06,C11,C12,C19; UJ-04.

**Dependencies / gates (source):** OD-01; OD-05,OD-06,OD-09; DR-017; DR-021–DR-023; DR-028; DR-042; DR-052; DR-053.

**Acceptance — FR-018.AC1:** T/A/D: Each proposal states its units, conversion/pack rounding, expected need, current usable supply and assumptions. Missing recipe/conversion evidence prevents an unqualified quantity recommendation; no safety-stock policy is invented.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Forecast and recipe fragments do not yield explained item/pack quantity alternatives under delivery, conversion and yield assumptions. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G16](IMPLEMENTATION-GAP-ANALYSIS.md#g16).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-019 — Supplier terms and outstanding commitments

**Authority / maturity:** [SRS FR-019](SRS.md#fr-019--supplier-terms-and-outstanding-commitments); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C06.

**Upstream trace:** S06 GOL-FR-019; PRD trace: S01,S04,S05; BG-02; C06; UJ-04.

**Dependencies / gates (source):** OD-01; OD-05,OD-06,OD-08; DR-016–DR-020; DR-053.

**Acceptance — FR-019.AC1:** T/A/D: A partially received order shows only remaining quantities as outstanding on matched units. A changed pack/quote retains effective dates; cost comparisons exclude incompatible tax/currency bases. AT-06.PROMISE retains two delivery tranches and both original and accepted revised promises.

**Implementation evidence — prior individual assessment:** **4. Present in raw data but not canonicalised**; severity **High**. Supplier, stock-order and reference datasets are fetched raw; dated terms and remaining/amended/cancelled commitments are not canonical business facts. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-020 — Accepted and rejected receipt evidence

**Authority / maturity:** [SRS FR-020](SRS.md#fr-020--accepted-and-rejected-receipt-evidence); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C07.

**Upstream trace:** S06 GOL-FR-020; PRD trace: S01,S04,S05; BG-03; C07; UJ-04.

**Dependencies / gates (source):** OD-01; OD-02,OD-05,OD-06; DR-018–DR-019; DR-024.

**Acceptance — FR-020.AC1:** T/A/D: For ten delivered packs with eight accepted and two rejected, only eight accepted packs support accepted-stock additions after conversion. Missing invoice/order evidence does not invalidate a known receipt.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Invoices/PDF notes do not establish separately accepted/rejected physical receipt lines and stock additions. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-021 — Order, receipt, invoice and credit matching

**Authority / maturity:** [SRS FR-021](SRS.md#fr-021--order-receipt-invoice-and-credit-matching); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C07, C08.

**Upstream trace:** S06 GOL-FR-021; PRD trace: S01,S04,S05; BG-03; C07,C08; UJ-04,UJ-06.

**Dependencies / gates (source):** OD-01; OD-02,OD-06,OD-08; DR-018–DR-020.

**Acceptance — FR-021.AC1:** T/A/D: One order can match several receipts and one invoice several receipts without duplicated purchase activity. A document-level match cannot claim line-level quantity agreement; over-allocation remains an exception. Three-way approval policy is not assumed.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. CSV/PDF invoice matching exists; typed order/receipt/invoice/credit many-to-many quantity/value allocations do not. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-022 — Payables and supplier payment status

**Authority / maturity:** [SRS FR-022](SRS.md#fr-022--payables-and-supplier-payment-status); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C08.

**Upstream trace:** S06 GOL-FR-022; PRD trace: S01,S04,S05; BG-03; C08; UJ-06.

**Dependencies / gates (source):** OD-01; OD-02,OD-08; DR-011–DR-012; DR-018–DR-020.

**Acceptance — FR-022.AC1:** T/A/D: An invoice without a payment remains outstanding, not paid because it was received. Non-stock service charges do not create stock movements; missing due-date evidence prevents an overdue conclusion.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Invoice amounts/due dates exist; supplier payment/credit allocations, disputes and remaining payable status do not. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-023 — Stock by item, location and cutoff

**Authority / maturity:** [SRS FR-023](SRS.md#fr-023--stock-by-item-location-and-cutoff); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C09.

**Upstream trace:** S06 GOL-FR-023; PRD trace: S01,S04,S05; BG-03; C09; UJ-04.

**Dependencies / gates (source):** OD-01; OD-05; DR-021; DR-024; DR-026–DR-027.

**Acceptance — FR-023.AC1:** T/A/D: Opening plus classified movements explains closing book quantity, with each fact counted once. A missing location or conversion leaves the affected balance incomplete; a negative balance is retained as an exception.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Canonical observations exist; no movement-explained item/location book balance or negative/incomplete-balance review exists. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-024 — Internal transfers and preparation production

**Authority / maturity:** [SRS FR-024](SRS.md#fr-024--internal-transfers-and-preparation-production); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C09, C11.

**Upstream trace:** S06 GOL-FR-024; PRD trace: S01,S04,S05; BG-03; C09,C11; UJ-04.

**Dependencies / gates (source):** OD-01; OD-05; DR-022–DR-024.

**Acceptance — FR-024.AC1:** T/A/D: A venue-internal transfer of two bottles has equal outbound/inbound quantity and zero venue-wide net change. Production inputs/outputs use their own units and yield evidence; a missing counterpart is visible.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No paired transfer or preparation-production legs support venue-net-zero/internal activity explanations. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-025 — Count scope, observations and recounts

**Authority / maturity:** [SRS FR-025](SRS.md#fr-025--count-scope-observations-and-recounts); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C10.

**Upstream trace:** S06 GOL-FR-025; PRD trace: S01,S04,S05; BG-03; C10; UJ-04,UJ-08.

**Dependencies / gates (source):** OD-01; OD-02,OD-05; DR-024; DR-026–DR-027.

**Acceptance — FR-025.AC1:** T/A/D: A missing count line is uncounted, not zero. A recount supersedes rather than adds to the prior observation; movements during the count are reconciled to the comparison cutoff.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Count header/line replay exists; expected count population, true cutoff/recount semantics and movement reconciliation are incomplete. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-026 — Count variance and proposed adjustment

**Authority / maturity:** [SRS FR-026](SRS.md#fr-026--count-variance-and-proposed-adjustment); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C10, C22.

**Upstream trace:** S06 GOL-FR-026; PRD trace: S01,S04,S05; BG-03; C10,C22; UJ-08.

**Dependencies / gates (source):** OD-01; OD-02,OD-05,OD-14; DR-024; DR-026–DR-027; DR-046.

**Acceptance — FR-026.AC1:** T/A/D: Eight observed against ten book units yields -2 variance without automatically posting -2 stock movement or labelling waste/theft. A proposal records basis/reason; an external adjustment requires independently obtained evidence.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No matched physical-minus-book variance and independent local adjustment proposal exists; purchase overrides address a different fact. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-027 — Effective recipes, modifiers and yields

**Authority / maturity:** [SRS FR-027](SRS.md#fr-027--effective-recipes-modifiers-and-yields); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C01, C11.

**Upstream trace:** S06 GOL-FR-027; PRD trace: S01,S02,S04; BG-02,BG-03; C01,C11; UJ-02,UJ-04.

**Dependencies / gates (source):** OD-01; OD-03,OD-05; DR-004; DR-009; DR-021–DR-023.

**Acceptance — FR-027.AC1:** T/A/D: A historical dish uses the appropriate recipe version under an explicit attribution rule. An omission removes its defined input rather than adding consumption; unknown substitution mapping marks ingredient coverage incomplete.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Recipe costs, links and components exist; effective sale/modifier/yield/component application is not complete. Impact/evidence/direction: [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-028 — Theoretical usage and ingredient economics

**Authority / maturity:** [SRS FR-028](SRS.md#fr-028--theoretical-usage-and-ingredient-economics); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C11, C12.

**Upstream trace:** S06 GOL-FR-028; PRD trace: S01,S04,S05; BG-02; C11,C12; UJ-04.

**Dependencies / gates (source):** OD-01; OD-05,OD-08,OD-09; DR-017; DR-021–DR-023; DR-028; DR-050.

**Acceptance — FR-028.AC1:** T/A/D: Output multiplied by effective component quantities gives labelled theoretical usage. Cost per usable portion states cost date and yield; purchase pack cost alone does not establish portion cost or actual COGS.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Source portion/component cost is retained; reproducible effective theoretical consumption and validated conversion/usable-yield economics are absent. Impact/evidence/direction: [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-029 — Known waste and disposal

**Authority / maturity:** [SRS FR-029](SRS.md#fr-029--known-waste-and-disposal); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C12.

**Upstream trace:** S06 GOL-FR-029; PRD trace: S01,S04,S05; BG-03; C12; UJ-04,UJ-08.

**Dependencies / gates (source):** OD-01; OD-05,OD-08; DR-024–DR-025.

**Acceptance — FR-029.AC1:** T/A/D: A linked waste observation and disposal movement enter stock balance once. Unknown cost permits physical waste reporting; unknown quantity permits incident reporting with incomplete measured waste, not zero loss.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Canonical waste quantity/reason/cost exists; unknown-quantity incidents, disposal linkage, measured/estimated method and business projections are incomplete. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-030 — Residual depletion and usage variance

**Authority / maturity:** [SRS FR-030](SRS.md#fr-030--residual-depletion-and-usage-variance); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C12.

**Upstream trace:** S06 GOL-FR-030; PRD trace: S01,S04,S05; BG-03; C12; UJ-04,UJ-08.

**Dependencies / gates (source):** OD-01; OD-05,OD-09; DR-024–DR-028.

**Acceptance — FR-030.AC1:** T/A/D: The residual shows opening/closing counts, included movements and excluded known losses. Residual minus theoretical usage is labelled unexplained usage variance, never automatically waste, theft or employee fault.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No compatible residual/theoretical assessment with classified movements and known-loss exclusions exists. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-031 — Availability, duties and competency evidence

**Authority / maturity:** [SRS FR-031](SRS.md#fr-031--availability-duties-and-competency-evidence); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (source-explicit):** C13.

**Upstream trace:** S06 GOL-FR-031; PRD trace: S01,S02,S04; BG-02,BG-05; C13; UJ-03.

**Dependencies / gates (source):** OD-01; OD-02,OD-04,OD-07,OD-10; DR-029–DR-031; DR-043; DR-051.

**Acceptance — FR-031.AC1:** T/A/D: An employee title alone proves neither competency nor classification. Required-but-expired evidence and unknown availability are distinguished from valid evidence and confirmed unavailability. AT-08.AVAIL resolves or exposes interval-specific declaration conflicts without disclosing private reasons.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No enduring worker availability/duties/competency applicability/expiry evidence model supports rostering review. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-032 — Workload and skill coverage planning

**Authority / maturity:** [SRS FR-032](SRS.md#fr-032--workload-and-skill-coverage-planning); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C05, C14, C19.

**Upstream trace:** S06 GOL-FR-032; PRD trace: S02,S04,S05; BG-02; C05,C14,C19; UJ-03.

**Dependencies / gates (source):** OD-01; OD-02,OD-04,OD-07; DR-032–DR-033; DR-037–DR-038; DR-044.

**Acceptance — FR-032.AC1:** T/A/D: A cocktail-heavy demand scenario can have a separately stated effort assumption; revenue/headcount alone is not declared measured workload. Required coverage and under/overstaffing criteria are validated, and missing task evidence is visible.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Daily hours/covers/revenue are not product effort, competing-duty or required-skill coverage planning. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-033 — Roster versions and absence risks

**Authority / maturity:** [SRS FR-033](SRS.md#fr-033--roster-versions-and-absence-risks); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C14.

**Upstream trace:** S06 GOL-FR-033; PRD trace: S01,S02,S04; BG-02; C14; UJ-03.

**Dependencies / gates (source):** OD-01; OD-02,OD-04,OD-07; DR-029; DR-032–DR-033; DR-051.

**Acceptance — FR-033.AC1:** T/A/D: The originally published plan remains available when amended. A sickness-related unfilled slot remains unresolved until external evidence supports cover; sub-head-chef rostering authority is not inferred. AT-08.AVAIL retains the original roster while showing the remaining absence gap and a partial local cover option.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No published/amended roster version, unfilled slot or absence-risk/scenario workflow exists. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-034 — Planned, actual and payable time

**Authority / maturity:** [SRS FR-034](SRS.md#fr-034--planned-actual-and-payable-time); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C14, C15, C16.

**Upstream trace:** S06 GOL-FR-034; PRD trace: S01,S04,S05; BG-03; C14,C15,C16; UJ-03,UJ-06.

**Dependencies / gates (source):** OD-01; OD-03,OD-07; DR-032–DR-036.

**Acceptance — FR-034.AC1:** T/A/D: Unrostered work remains actual work; a rostered absence does not become worked hours. A missing clock-out has unknown duration rather than a roster-derived end; department allocations do not duplicate worked time.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Daily scheduled/actual hour fields exist; independent roster, observed work/break and payable-time contracts are absent. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-035 — Timesheet evidence and correction follow-up

**Authority / maturity:** [SRS FR-035](SRS.md#fr-035--timesheet-evidence-and-correction-follow-up); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C15, C22.

**Upstream trace:** S06 GOL-FR-035; PRD trace: S01,S04,S05; BG-03; C15,C22; UJ-08.

**Dependencies / gates (source):** OD-01; OD-02,OD-07,OD-14; DR-034–DR-035; DR-046.

**Acceptance — FR-035.AC1:** T/A/D: Approval status does not prove payment. A proposed correction retains original evidence and reason; self-approval and verifier separation apply only when the validated policy requires them.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No timesheet status, overlapping/missing interval and authored local correction lineage workflow exists. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-036 — Labour cost interpretation and productivity

**Authority / maturity:** [SRS FR-036](SRS.md#fr-036--labour-cost-interpretation-and-productivity); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C16, C18.

**Upstream trace:** S06 GOL-FR-036; PRD trace: S01,S04,S05; BG-02,BG-03; C16,C18; UJ-03,UJ-06.

**Dependencies / gates (source):** OD-01; OD-07,OD-08,OD-09; DR-031; DR-035–DR-036; DR-040–DR-041.

**Acceptance — FR-036.AC1:** T/A/D: Unknown penalties/on-costs prevent a complete loaded-cost claim. Estimate and superseding final components are not both added to actual cost; sales-per-worked-hour and sales-per-paid-hour remain separately named.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Labour totals/ratios are labelled cost/productivity without component/payable scope, and percentages use gross rather than matching net sales. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-037 — Pay obligations and settlement evidence

**Authority / maturity:** [SRS FR-037](SRS.md#fr-037--pay-obligations-and-settlement-evidence); Mandatory — proposed.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C16.

**Upstream trace:** S06 GOL-FR-037; PRD trace: S04,S05; BG-05; C16; UJ-06.

**Dependencies / gates (source):** OD-01; OD-02,OD-07,OD-10; DR-011–DR-012; DR-031; DR-036; DR-043.

**Acceptance — FR-037.AC1:** T/A/D: Paid status requires allocation/evidence appropriate to the obligation. Unknown applicability or due-date basis prevents an overdue/compliance assertion; no universal rate or deadline is introduced.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No earning/contribution obligation and allocated-payment evidence capability exists; applicability remains conditional. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-038 — Comparable financial results

**Authority / maturity:** [SRS FR-038](SRS.md#fr-038--comparable-financial-results); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-03. **Capabilities (source-explicit):** C18.

**Upstream trace:** S06 GOL-FR-038; PRD trace: S01,S04,S05; BG-01,BG-03; C18; UJ-01,UJ-06.

**Dependencies / gates (source):** OD-01; OD-03,OD-08,OD-09; DR-003; DR-007–DR-028; DR-036; DR-040–DR-041; DR-049; DR-050.

**Acceptance — FR-038.AC1:** T/A/D: Missing cost coverage produces a partial/estimated or unavailable profit measure. Recipe cost, purchasing, cash paid and final COGS remain distinguishable; valued waste is not subtracted twice. AT-09.POS, AT-09.INC and AT-09.REV verify complete financial results, affected missing inputs and linked value restatement respectively.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Purchases are labelled COGS/food cost; valuation, other expenses and matched financial recognition do not support complete profit. Impact/evidence/direction: [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-039 — Budgets, targets and commitments

**Authority / maturity:** [SRS FR-039](SRS.md#fr-039--budgets-targets-and-commitments); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C06, C18, C19.

**Upstream trace:** S06 GOL-FR-039; PRD trace: S04,S05; BG-02; C06,C18,C19; UJ-06,UJ-07.

**Dependencies / gates (source):** OD-01; OD-08,OD-09,OD-11; DR-040–DR-042.

**Acceptance — FR-039.AC1:** T/A/D: Without an approved budget, budget variance is unavailable. A revised forecast does not overwrite the approved budget; favourable direction follows the agreed measure definition.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **Medium**. No approved budget/target, commitment exposure and separate forecast-plan comparison model exists. Impact/evidence/direction: [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-040 — Comparable reporting populations

**Authority / maturity:** [SRS FR-040](SRS.md#fr-040--comparable-reporting-populations); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-03. **Capabilities (source-explicit):** C18, C19, C22.

**Upstream trace:** S06 GOL-FR-040; PRD trace: S01,S04,S05; BG-01,BG-03; C18,C19,C22; UJ-01,UJ-06.

**Dependencies / gates (source):** OD-01; OD-03,OD-08,OD-09; DR-003–DR-006; DR-040–DR-042.

**Acceptance — FR-040.AC1:** T/A/D: A comparison states duration, service scope, category version, tax/cost basis and coverage differences. Incompatible comparisons are qualified or unavailable; an unequal trading-week duration is not silently compared as equivalent.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Period comparisons and shared-day series exist; agreed trading duration, effective categories, financial bases and summary coverage are incomplete. Impact/evidence/direction: [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G22](IMPLEMENTATION-GAP-ANALYSIS.md#g22).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-041 — Revenue forecast

**Authority / maturity:** [SRS FR-041](SRS.md#fr-041--revenue-forecast); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C19.

**Upstream trace:** S06 GOL-FR-041; PRD trace: S06,S02; BG-02; C19; UJ-02.

**Dependencies / gates (source):** OD-01; OD-03,OD-04,OD-08,OD-09,OD-13; DR-040–DR-042; DR-048.

**Acceptance — FR-041.AC1:** T/A/D: The result states what revenue includes, evidence cutoff/coverage and forecast horizon. Inadequate history yields a labelled scenario or unavailable result; hindsight revisions cannot masquerade as the originally issued forecast.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Persisted gross-sales forecast, issue/horizon and quantiles exist; original input coverage, basis and decision eligibility remain incomplete. Impact/evidence/direction: [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-042 — Specific-product sales forecast

**Authority / maturity:** [SRS FR-042](SRS.md#fr-042--specific-product-sales-forecast); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C01, C06, C14, C19.

**Upstream trace:** S06 GOL-FR-042; PRD trace: S06,S02; BG-02; C01,C06,C14,C19; UJ-02,UJ-04.

**Dependencies / gates (source):** OD-01; OD-03,OD-04,OD-05,OD-09,OD-13; DR-004; DR-005; DR-008–DR-009; DR-040–DR-042.

**Acceptance — FR-042.AC1:** T/A/D: A product forecast states sale units, product/version mapping, horizon, price assumptions for money and known availability constraints. Recorded zero sales do not imply zero demand during an evidenced stockout; unknown history is not invented.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No selected-product/variant predicate or forecast UX exists despite available product metrics. Impact/evidence/direction: [G16](IMPLEMENTATION-GAP-ANALYSIS.md#g16).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-043 — Forecast and scenario assumptions

**Authority / maturity:** [SRS FR-043](SRS.md#fr-043--forecast-and-scenario-assumptions); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-04. **Capabilities (source-explicit):** C19.

**Upstream trace:** S06 GOL-FR-043; PRD trace: S02,S06; BG-02,BG-04; C19; UJ-02,UJ-07.

**Dependencies / gates (source):** OD-01; OD-04,OD-05,OD-09,OD-11; DR-040–DR-042; DR-046–DR-047.

**Acceptance — FR-043.AC1:** T/A/D: Each scenario identifies author, version, scope, assumptions, changed inputs and qualified outputs. Weather, bookings and public context remain proposed drivers unless supported; a scenario is not labelled a confirmed causal forecast.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **Medium**. Forecast request metadata exists; authored assumptions, scenario versions/alternatives and explicit local changes do not. Impact/evidence/direction: [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-044 — Forecast outcome evaluation

**Authority / maturity:** [SRS FR-044](SRS.md#fr-044--forecast-outcome-evaluation); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C19, C20.

**Upstream trace:** S06 GOL-FR-044; PRD trace: S02,S05,S06; BG-02; C19,C20; UJ-02.

**Dependencies / gates (source):** OD-01; OD-03,OD-04,OD-09,OD-13; DR-040–DR-042.

**Acceptance — FR-044.AC1:** T/A/D: Evaluation references the forecast version issued before the outcome and matched actual population. Missing actuals or actual zero with a percentage-error denominator are handled under the agreed missing/denominator rule, not forced to zero error.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **Medium**. Stored issued quantiles are compared with later actuals; error denominator guards exist, but retained definitions/input coverage and eligible evaluation disclosure are incomplete. Impact/evidence/direction: [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-045 — Second-pub decision evidence

**Authority / maturity:** [SRS FR-045](SRS.md#fr-045--second-pub-decision-evidence); Mandatory — proposed.

**Goals (source-explicit):** BG-04. **Capabilities (source-explicit):** C19, C20, C22.

**Upstream trace:** S06 GOL-FR-045; PRD trace: S02; BG-04; C19,C20,C22; UJ-07.

**Dependencies / gates (source):** OD-01; OD-08,OD-11; DR-040–DR-042; DR-047.

**Acceptance — FR-045.AC1:** T/A/D: A scenario states transfer assumptions, alternative options and sensitivity for the selected decision area. Current-pub performance is not treated as guaranteed new-pub demand or profitability; financial outputs disclose included costs.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **Medium**. No proposed-site evidence/scenario/transfer-assumption/sensitivity workflow exists. Impact/evidence/direction: [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-046 — Purpose-bound external and internal context

**Authority / maturity:** [SRS FR-046](SRS.md#fr-046--purpose-bound-external-and-internal-context); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C19, C20, C22.

**Upstream trace:** S06 GOL-FR-046; PRD trace: S02; BG-02; C19,C20,C22; UJ-02,UJ-05.

**Dependencies / gates (source):** OD-01; OD-01,OD-10; DR-045.

**Acceptance — FR-046.AC1:** T/A/D: Each item has source, subject, occurrence/publication/receipt times and relevance/limitations. A review is not counted as a visit; read task status is distinct from independently verified completion. Weather requires a validated use; no unrestricted archive is created.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **Medium**. Canonical reviews and rich booking context exist; purpose-bound relevance, article/Asana/weather coverage and verified-outcome distinctions are incomplete. Impact/evidence/direction: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-047 — Applicable operational control evidence

**Authority / maturity:** [SRS FR-047](SRS.md#fr-047--applicable-operational-control-evidence); Mandatory — proposed.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C13, C21.

**Upstream trace:** S06 GOL-FR-047; PRD trace: S01,S03,S04; BG-05; C13,C21; UJ-08.

**Dependencies / gates (source):** OD-01; OD-02,OD-07,OD-10; DR-043; DR-055.

**Acceptance — FR-047.AC1:** T/A/D: A pass/fail assertion has an applicable criterion/version, subject and observation. Unknown applicability or missing observation remains a gap; neither becomes a compliance pass or established breach. AT-11.DUE counts expected occurrences independently of observations and distinguishes late fulfilment from pass/fail.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **Medium**. No applicable control/licence/competency/incident and corrective-evidence register exists; applicability itself is unverified. Impact/evidence/direction: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-048 — Prioritised exception review

**Authority / maturity:** [SRS FR-048](SRS.md#fr-048--prioritised-exception-review); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-03. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-FR-048; PRD trace: S01,S02,S05; BG-02,BG-03; C20,C22; UJ-01,UJ-08.

**Dependencies / gates (source):** OD-01; OD-01,OD-02,OD-09,OD-14; DR-041; DR-047.

**Acceptance — FR-048.AC1:** T/A/D: An issue identifies type, affected facts/decision, first-observed/as-of times, unresolved amount/scope if known and assigned-or-unassigned owner. Unagreed thresholds remain pending; diagnostics do not displace the user’s business question.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. A source/reconciliation/invoice queue exists; owners, decision impact, agreed prioritisation and unresolved scope/age are incomplete. Impact/evidence/direction: [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-049 — Local annotation and correction proposal

**Authority / maturity:** [SRS FR-049](SRS.md#fr-049--local-annotation-and-correction-proposal); Mandatory — proposed.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-FR-049; PRD trace: S01,S02; BG-03,BG-05; C22; UJ-08.

**Dependencies / gates (source):** OD-01; OD-02,OD-10,OD-14; DR-046.

**Acceptance — FR-049.AC1:** T/A/D: Original fact, author, time, reason, proposed meaning/value and review status remain identifiable. Calculations do not silently substitute a proposal for a source fact; an explicitly hypothetical adjusted view is labelled and independently reproducible.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Operative manual overrides can replace resolved business values immediately; source facts persist, but proposal/review/hypothetical states are not the required correction workflow. Impact/evidence/direction: [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-050 — Follow-up responsibility and lifecycle

**Authority / maturity:** [SRS FR-050](SRS.md#fr-050--follow-up-responsibility-and-lifecycle); Mandatory — proposed.

**Goals (source-explicit):** BG-02. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-FR-050; PRD trace: S02,S05; BG-02; C20,C22; UJ-01,UJ-08.

**Dependencies / gates (source):** OD-01; OD-02,OD-14; DR-047.

**Acceptance — FR-050.AC1:** T/A/D: Unassigned work stays unassigned until ownership is established. Locally marked completion without required evidence remains unverified; cancellation, supersession, partial resolution and reopening retain history without changing connected tasks.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No persisted local ownership, due/escalation, partial/reopened/cancelled and verified-closure lifecycle exists. Impact/evidence/direction: [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-051 — CSV export of permitted business information

**Authority / maturity:** [SRS FR-051](SRS.md#fr-051--csv-export-of-permitted-business-information); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-05. **Capabilities (source-explicit):** C18, C20, C22.

**Upstream trace:** S06 GOL-FR-051; PRD trace: S06; BG-01,BG-02,BG-05; C18,C20,C22; UJ-01.

**Dependencies / gates (source):** OD-01; OD-02,OD-09,OD-10,OD-13; DR-040–DR-041; DR-048; NFR-012.

**Acceptance — FR-051.AC1:** T/A/D: Exported rows preserve grain/identity or result scope, meaningful headers, units, dates, currency/tax basis and missing states. Row count/totals match the selected authorised population; delimiters, quotes and line breaks do not corrupt records.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. CSV handling is inbound; authorised selected-fact/result CSV output is absent. Impact/evidence/direction: [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-052 — Readable formatted Excel reports

**Authority / maturity:** [SRS FR-052](SRS.md#fr-052--readable-formatted-excel-reports); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02. **Capabilities (source-explicit):** C18, C20, C22.

**Upstream trace:** S06 GOL-FR-052; PRD trace: S06; BG-01,BG-02; C18,C20,C22; UJ-01.

**Dependencies / gates (source):** OD-01; OD-09,OD-10,OD-12,OD-13; DR-040–DR-041; DR-048; NFR-012.

**Acceptance — FR-052.AC1:** T/A/D: Agreed report fields are readable without hidden/truncated critical labels or statuses; money, quantity, percentage and date formats preserve meaning. Summary and detail reconcile, with title, scope/period, as-of/status, units and caveats retained; numbers remain usable numbers.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No formatted Excel reporting/export workflow exists. Impact/evidence/direction: [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-053 — Export completeness, provenance and safe use

**Authority / maturity:** [SRS FR-053](SRS.md#fr-053--export-completeness-provenance-and-safe-use); Mandatory — proposed.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-FR-053; PRD trace: S01,S06; BG-03,BG-05; C22; UJ-01,UJ-08.

**Dependencies / gates (source):** OD-01; OD-02,OD-09,OD-10,OD-13; DR-040–DR-041; DR-048; NFR-012.

**Acceptance — FR-053.AC1:** T/A/D: A partial report stays marked partial in the file; an export cannot expose restricted staff/customer detail. Known zero and missing values remain distinguishable; untrusted text is not interpreted as executable spreadsheet content.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No business export artefact preserves scope, missingness, provenance, omissions, grants and safe spreadsheet text. Impact/evidence/direction: [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### FR-054 — Business questions, filters and comparisons

**Authority / maturity:** [SRS FR-054](SRS.md#fr-054--business-questions-filters-and-comparisons); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02. **Capabilities (source-explicit):** C01, C06, C14, C18, C19, C20, C22.

**Upstream trace:** S06 GOL-FR-054; PRD trace: S01,S06; BG-01,BG-02; C01,C06,C14,C18–C20,C22; UJ-01,UJ-02,UJ-06.

**Dependencies / gates (source):** OD-01,OD-02,OD-03,OD-09,OD-10,OD-12,OD-13; DR-040–DR-041; MET-001–MET-016.

**Acceptance — FR-054.AC1:** T/A/D: The selected scope, filters, sort/grouping and comparator are visible and retained in the result/export. Changing to a validated product or service-period scope yields corresponding eligible facts without changing canonical meaning; ratios recalculate from eligible components. Unsupported dimensions and restricted detail are explicitly unavailable rather than guessed or exposed.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Compiled metric queries/rankings and some source/evidence filters exist; validated arbitrary business scopes, value predicates and complete authorised comparisons do not. Impact/evidence/direction: [G22](IMPLEMENTATION-GAP-ANALYSIS.md#g22), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-001 — Decision-speed and report deadlines

**Authority / maturity:** [SRS NFR-001](SRS.md#nfr-001--decision-speed-and-report-deadlines); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-NFR-001; S07; PRD trace: S06; C20,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-12,OD-13.

**Acceptance — NFR-001.AC1:** T/A/D/I: For the agreed workload/volume, record elapsed request-to-usable-result time against each approved bound. Until bounds and volumes exist, performance acceptance is pending; an old cached result cannot pass freshness by returning quickly.

**Implementation evidence — prior individual assessment:** **6. Cannot verify from repository evidence**; severity **High**. No stakeholder-approved request/report deadlines and business volumes or measured acceptance results are available. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24), [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-002 — Business-time availability and degraded use

**Authority / maturity:** [SRS NFR-002](SRS.md#nfr-002--business-time-availability-and-degraded-use); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-NFR-002; S07; PRD trace: S02,S06; C20,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-04,OD-06,OD-13.

**Acceptance — NFR-002.AC1:** T/A/D/I: Within the agreed test window, interruption and qualified last-known behaviour are measured against approved limits; unavailable evidence is shown as unavailable. No continuous or offline operation is assumed.

**Implementation evidence — prior individual assessment:** **6. Cannot verify from repository evidence**; severity **Medium**. Error/partial states exist, but approved operating window/disruption/support and qualified degraded-use acceptance cannot be verified. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-003 — Recoverable local evidence and report history

**Authority / maturity:** [SRS NFR-003](SRS.md#nfr-003--recoverable-local-evidence-and-report-history); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-NFR-003; S07; PRD trace: S01,S02; C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-10,OD-13,OD-14.

**Acceptance — NFR-003.AC1:** T/A/D/I: A recovery exercise verifies the agreed latest retained records, relationships, status and access under the approved loss/time bounds. No value is declared until the stakeholder contract is set.

**Implementation evidence — prior individual assessment:** **6. Cannot verify from repository evidence**; severity **High**. Recovery bounds/exercise evidence is unavailable; required report/local-record classes are also absent. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24), [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-004 — Reproducibility and numerical reconciliation

**Authority / maturity:** [SRS NFR-004](SRS.md#nfr-004--reproducibility-and-numerical-reconciliation); Mandatory — established.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C18, C22.

**Upstream trace:** S06 GOL-NFR-004; S07; PRD trace: S01; C18,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-08,OD-09.

**Acceptance — NFR-004.AC1:** T/A/D/I: Identical definitions/inputs produce the same business results within agreed tolerance; a restatement is attributable. A sample rounding difference is exposed under its rule, not hidden as extra waste or sales.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Resolved recomputation and rounding exist; complete input/result versions, declared tolerances and cross-surface reproducibility are incomplete. Impact/evidence/direction: [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-005 — Confidentiality and authorised use

**Authority / maturity:** [SRS NFR-005](SRS.md#nfr-005--confidentiality-and-authorised-use); Mandatory — established.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-NFR-005; S07; PRD trace: S01,S02; C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-02,OD-10.

**Acceptance — NFR-005.AC1:** T/A/D/I: Attempted unauthorised detail, small-scope inference, export and annotation access reveals no protected information. Authorised operation remains within its validated purpose/scope; tests use approved handling rules.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Explicit server checks protect some resources; full purpose/scope/inference/export/local-record confidentiality is not satisfied. Impact/evidence/direction: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G19](IMPLEMENTATION-GAP-ANALYSIS.md#g19).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-006 — Usability for operational work

**Authority / maturity:** [SRS NFR-006](SRS.md#nfr-006--usability-for-operational-work); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-NFR-006; S07; PRD trace: S01,S06; C20,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-01,OD-12,OD-13.

**Acceptance — NFR-006.AC1:** T/A/D/I: Representative users complete the agreed tasks and can explain period, unit, missing state and proposed action. Record completion rate, time, errors and assistance against agreed bounds; diagnostics do not replace the business task.

**Implementation evidence — prior individual assessment:** **6. Cannot verify from repository evidence**; severity **High**. No representative user task success/time/assistance evidence or approved bounds exist; required workflows also have structural gaps. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24), [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-007 — Accessible interpretation and use

**Authority / maturity:** [SRS NFR-007](SRS.md#nfr-007--accessible-interpretation-and-use); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-NFR-007; S07; PRD trace: S01,S06; C20,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-12.

**Acceptance — NFR-007.AC1:** T/A/D/I: A user under each validated access condition can identify status/unit/scope and complete the agreed task. If formal accessibility conformance is required, name/version and test scope are agreed before claiming compliance.

**Implementation evidence — prior individual assessment:** **6. Cannot verify from repository evidence**; severity **Medium**. Accessible component groundwork exists; approved conditions and end-to-end interpretation/conformance evidence are unavailable. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-008 — Business volume and growth envelope

**Authority / maturity:** [SRS NFR-008](SRS.md#nfr-008--business-volume-and-growth-envelope); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C18, C22.

**Upstream trace:** S06 GOL-NFR-008; S07; PRD trace: S01,S02,S06; C18,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-11,OD-13.

**Acceptance — NFR-008.AC1:** T/A/D/I: Complete-report/export counts and reconciliation remain correct across the approved envelope, including large periods and simultaneous permitted requests. Limits and any partial result are visible; timing is measured under NFR-001.

**Implementation evidence — prior individual assessment:** **6. Cannot verify from repository evidence**; severity **Medium**. Pagination exists; complete output/reconciliation under an approved history/concurrency/export envelope is unverified. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24), [G22](IMPLEMENTATION-GAP-ANALYSIS.md#g22).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-009 — Record-class retention and lawful disposition

**Authority / maturity:** [SRS NFR-009](SRS.md#nfr-009--record-class-retention-and-lawful-disposition); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C21, C22.

**Upstream trace:** S06 GOL-NFR-009; S07; PRD trace: S01,S03; C21,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-10.

**Acceptance — NFR-009.AC1:** T/A/D/I: For each adopted policy, required evidence is retrievable in its agreed readable form during the verified interval; due disposition follows the agreed authority and hold rule. No one-period-for-all retention rule or exemption is inferred.

**Implementation evidence — prior individual assessment:** **6. Cannot verify from repository evidence**; severity **Medium**. No verified record-class applicability, retention/hold/disposition policy and acceptance evidence is available. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-010 — Business-readable dates, units and formats

**Authority / maturity:** [SRS NFR-010](SRS.md#nfr-010--business-readable-dates-units-and-formats); Mandatory — established.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-NFR-010; S07; PRD trace: S01,S06; C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-03,OD-05,OD-08,OD-09.

**Acceptance — NFR-010.AC1:** T/A/D/I: An overnight/DST case and a mixed-tax/pack case are interpretable without assuming calendar date equals trading date or case equals serve. Spreadsheet display/precision does not change values.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Shared formatting/venue timezone exists; unknown states, unit/tax bases and overnight reporting/forecast date consistency remain incomplete. Impact/evidence/direction: [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-011 — Interpretability of forecast uncertainty

**Authority / maturity:** [SRS NFR-011](SRS.md#nfr-011--interpretability-of-forecast-uncertainty); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C19, C22.

**Upstream trace:** S06 GOL-NFR-011; S07; PRD trace: S02,S06; C19,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-04,OD-09,OD-12.

**Acceptance — NFR-011.AC1:** T/A/D/I: A representative user distinguishes a qualified scenario from an evidence-supported forecast and identifies key assumptions. Uncertainty ranges are not called guaranteed bounds; an opaque confidence score does not substitute for evidence coverage.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **Medium**. Quantile chart and forecast/run labels exist; source coverage, assumption meaning and user reliance interpretation are incomplete and unvalidated. Impact/evidence/direction: [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### NFR-012 — Complete and usable report/export artefacts

**Authority / maturity:** [SRS NFR-012](SRS.md#nfr-012--complete-and-usable-reportexport-artefacts); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03, BG-05. **Capabilities (source-explicit):** C18, C22.

**Upstream trace:** S06 GOL-NFR-012; S07; PRD trace: S06; C18,C22; BG-01/BG-02 for timeliness/usability, BG-03 for integrity, BG-05 for protection.

**Dependencies / gates (source):** OD-10,OD-12,OD-13.

**Acceptance — NFR-012.AC1:** T/A/D/I: Open representative files in agreed tools; verify row count, money/units/dates, quoting, missing states, readable formatting and totals. Untrusted text cannot execute as spreadsheet content; the original selection is not presented as fully exported after truncation.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Required CSV/Excel artefacts do not exist; recipient-tool opening, completeness and safe-text acceptance cannot be met. Impact/evidence/direction: [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### ACC-001 — Validate explicit grants

**Authority / maturity:** [SRS ACC-001](SRS.md#acc-001--validate-explicit-grants); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-ACC-001; S07 GUX-15; PRD trace: S01,S02; C22; BG-05.

**Dependencies / gates (source):** OD-02,OD-10; OD-14 for local approvals.

**Acceptance — ACC-001.AC1:** T/I: A title-only account receives no inferred grant. A FOH staffing purpose does not automatically expose kitchen/private-pay records or allow report-definition changes.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Explicit role/resource rows fail closed, but person/purpose/scope/operation/effective/approval grant evidence is absent. Impact/evidence/direction: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### ACC-002 — Enforce scope for every view and export

**Authority / maturity:** [SRS ACC-002](SRS.md#acc-002--enforce-scope-for-every-view-and-export); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-ACC-002; S07 GUX-15; PRD trace: S01,S02; C22; BG-05.

**Dependencies / gates (source):** OD-02,OD-10; OD-14 for local approvals.

**Acceptance — ACC-002.AC1:** T/I: A denied record cannot be recovered through a total drill-down or export. A user allowed only departmental results sees only that authorised scope; a summary cannot leak a restricted small-population value contrary to the validated policy.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Server resource checks and metric render checks exist; operand, row/worker, inference, local-record and export scope are incomplete. Impact/evidence/direction: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G19](IMPLEMENTATION-GAP-ANALYSIS.md#g19).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### ACC-003 — Sensitive worker/customer information

**Authority / maturity:** [SRS ACC-003](SRS.md#acc-003--sensitive-workercustomer-information); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C13, C16, C22.

**Upstream trace:** S06 GOL-ACC-003; S07 GUX-15; PRD trace: S01,S02; C13,C16,C22; BG-05.

**Dependencies / gates (source):** OD-02,OD-10; OD-14 for local approvals.

**Acceptance — ACC-003.AC1:** T/I: Staffing gaps can be shown without unjustified sickness details or individual wages. An ordinary walk-in need not have a customer profile; annotations cannot bypass personal-data restrictions.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Some owner cost gating exists, but validated minimum collection/purpose and sensitive field/row policies are not encoded. Impact/evidence/direction: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### ACC-004 — Operational responsibility and approval separation

**Authority / maturity:** [SRS ACC-004](SRS.md#acc-004--operational-responsibility-and-approval-separation); Mandatory — proposed.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 GOL-ACC-004; S07 GUX-15; PRD trace: S01,S02,S05; C20,C22; BG-05.

**Dependencies / gates (source):** OD-02,OD-10; OD-14 for local approvals.

**Acceptance — ACC-004.AC1:** T/I: If policy requires independent verification, the same person cannot claim that verified status without the necessary role separation/evidence. If no such policy is validated, the PRD does not invent a universal separation rule.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No policy-driven delegated local verifier/approver and independent-verification workflow exists. Impact/evidence/direction: [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### ACC-005 — Changed duties and revoked access

**Authority / maturity:** [SRS ACC-005](SRS.md#acc-005--changed-duties-and-revoked-access); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-ACC-005; S07 GUX-15; PRD trace: S01; C22; BG-05.

**Dependencies / gates (source):** OD-02,OD-10; OD-14 for local approvals.

**Acceptance — ACC-005.AC1:** T/I: After a validated revocation becomes effective, the user cannot newly view/export/revise restricted information. Historical authorship persists; already downloaded files are not claimed to be recalled or access-controlled retroactively.

**Implementation evidence — prior individual assessment:** **6. Cannot verify from repository evidence**; severity **High**. Login active-state and role/resource checks do not verify effective delegation change, existing session/cache or new export denial after revocation. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### ACC-006 — External and supporting participants

**Authority / maturity:** [SRS ACC-006](SRS.md#acc-006--external-and-supporting-participants); Mandatory — proposed.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C16, C18, C22.

**Upstream trace:** S06 GOL-ACC-006; S07 GUX-15; PRD trace: S02; C16,C18,C22; BG-05.

**Dependencies / gates (source):** OD-02,OD-10; OD-14 for local approvals.

**Acceptance — ACC-006.AC1:** T/I: An adviser can obtain only approved information during the agreed interval. Invitation or professional title alone does not establish indefinite full-venue access; electronic distribution is separately agreed.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No purpose/duration-limited adviser/supporting-function grant and sharing model exists. Impact/evidence/direction: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### ACC-007 — Accountability for sensitive local actions

**Authority / maturity:** [SRS ACC-007](SRS.md#acc-007--accountability-for-sensitive-local-actions); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-ACC-007; S07 GUX-15; PRD trace: S01,S02; C22; BG-05.

**Dependencies / gates (source):** OD-02,OD-10; OD-14 for local approvals.

**Acceptance — ACC-007.AC1:** T/I: A sample changed definition/export can be attributed to its actor and authorised scope. An unknown historical source actor remains unknown; local accountability does not fabricate source approval.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Override/rule/dashboard actors/history exist; sensitive export, report-definition and purpose/scoped grant accountability is incomplete. Impact/evidence/direction: [G19](IMPLEMENTATION-GAP-ANALYSIS.md#g19), [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### ACC-008 — Purpose-bound source access and sharing

**Authority / maturity:** [SRS ACC-008](SRS.md#acc-008--purpose-bound-source-access-and-sharing); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S06 GOL-ACC-008; S07 GUX-15; PRD trace: S02; C22; BG-05.

**Dependencies / gates (source):** OD-02,OD-10; OD-14 for local approvals.

**Acceptance — ACC-008.AC1:** T/I: A restricted internal task or staff record is not copied into a general weekly export. Automated report preparation grants no implicit email recipient or external messaging authority.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Token-gated feeds and source adapters exist; purpose-bounded collection and distribution evidence are not represented comprehensively. Impact/evidence/direction: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-001 — Operational value test

**Authority / maturity:** [SRS UX-001](SRS.md#ux-001--operational-value-test); Mandatory — established.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-01; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** GOV-001–GOV-002; OD-01; OD-02; OD-12.

**Acceptance — UX-001.AC1:** I/D: inspect each adopted primary destination and demonstrate the complete chain, or record its unresolved validation gate.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Some summaries and queues exist; navigation prominence and a full decision-to-action-to-outcome chain are not justified by validated responsibility. Impact/evidence/direction: [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-002 — Upper management

**Authority / maturity:** [SRS UX-002](SRS.md#ux-002--upper-management); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-02; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** FR-001–FR-006; FR-041–FR-045; OD-02.

**Acceptance — UX-002.AC1:** D/T: management tasks expose only permitted results and distinguish opening assumptions from actual operations.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Business/owner focus exists; owner/GM reporting and opening evidence are incomplete and duties/grants are title-based. Impact/evidence/direction: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-003 — Front of house

**Authority / maturity:** [SRS UX-003](SRS.md#ux-003--front-of-house); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-03; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** FR-017–FR-036; ACC-001–ACC-003; OD-02.

**Acceptance — UX-003.AC1:** D/T: demonstrate manager and staff tasks and denial of private pay/absence information without a justified grant.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. FOH focus shortcuts exist; assigned service, roster/order evidence and manager-versus-staff privacy/work distinctions are incomplete. Impact/evidence/direction: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-004 — Back of house

**Authority / maturity:** [SRS UX-004](SRS.md#ux-004--back-of-house); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-04; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** FR-017–FR-036; GOV-001; OD-02.

**Acceptance — UX-004.AC1:** D/T: demonstrate separate duty/grant examples; ordering access alone gives no Staffing or pay authority.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Kitchen focus and recipe/invoice screens exist; BOH head/sub-head/staff duties and usable stock/coverage decisions are not separately supported. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-005 — Three responsibility contexts

**Authority / maturity:** [SRS UX-005](SRS.md#ux-005--three-responsibility-contexts); Mandatory — established.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-05; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** ACC-001–ACC-005; OD-02.

**Acceptance — UX-005.AC1:** D/T: demonstrate the three contexts, correct scoped navigation and denied access after context switching.

**Implementation evidence — prior individual assessment:** ; **3. Implemented but semantically incorrect**; severity **High**. The agreed three-context information architecture is replaced by four focus presets and global domain/technical groups. Impact/evidence/direction: [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-006 — Unranked management home alternatives

**Authority / maturity:** [SRS UX-006](SRS.md#ux-006--unranked-management-home-alternatives); Mandatory — established.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-06; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** UX-005; ACC-001; OD-01; OD-12.

**Acceptance — UX-006.AC1:** I/D/T: first entry has neither selected alternative; explicit preference affects only that user and grants nothing.

**Implementation evidence — prior individual assessment:** ; **5. Not implemented**; severity **High**. No equal unranked Weekly review versus Operational overview entry or explicit Use this as my home choice exists. Impact/evidence/direction: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-007 — FOH home

**Authority / maturity:** [SRS UX-007](SRS.md#ux-007--foh-home); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-07; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** UX-003; FR-048–FR-050; OD-01; OD-02.

**Acceptance — UX-007.AC1:** D: complete a FOH quick-check and follow-up with evidence limitations visible.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. FOH preference/filtering exists; decision-specific service/coverage/supply/follow-up/outcome home modules and staff differences are absent. Impact/evidence/direction: [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-008 — BOH home

**Authority / maturity:** [SRS UX-008](SRS.md#ux-008--boh-home); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-08; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** UX-004; FR-023–FR-036; OD-01; OD-02; OD-05.

**Acceptance — UX-008.AC1:** D/A: demonstrate head/sub-head/staff use with recipe/yield gaps and no inferred rostering authority.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Kitchen preference/filtering exists; preparation/usable supply/coverage/follow-up home and ordering-only sub-head role treatment are absent. Impact/evidence/direction: [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-009 — Navigation disposition

**Authority / maturity:** [SRS UX-009](SRS.md#ux-009--navigation-disposition); Mandatory — established.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-09; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** GOV-002; OD-01; OD-12.

**Acceptance — UX-009.AC1:** I/D: each primary item has independent recurring-work evidence; specialist pins remain within existing grants and contexts.

**Implementation evidence — prior individual assessment:** ; **3. Implemented but semantically incorrect**; severity **High**. Dedicated entity/reviews/flow-lab/technical destinations reflect available features rather than the specified operational-value dispositions. Impact/evidence/direction: [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-010 — Shared decision anatomy

**Authority / maturity:** [SRS UX-010](SRS.md#ux-010--shared-decision-anatomy); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-10; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** FR-005–FR-006; FR-054; DR-040–DR-041.

**Acceptance — UX-010.AC1:** I/D: selected surfaces include every applicable element and preserve meaning on filter/revision change.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Shared tables, charts, states and scope controls exist; definition/population/revision, proposal and export behavioural vocabulary is incomplete. Impact/evidence/direction: [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-011 — Decision-to-feedback loop

**Authority / maturity:** [SRS UX-011](SRS.md#ux-011--decision-to-feedback-loop); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-11; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** FR-043–FR-044; FR-049–FR-050; OD-09; OD-14.

**Acceptance — UX-011.AC1:** T/A/D: follow a decision through partial/verified outcome and incompatible or missing actuals.

**Implementation evidence — prior individual assessment:** ; **5. Not implemented**; severity **High**. No retained local decision, accepted ownership, verified closure and matched later-KPI feedback loop exists. Impact/evidence/direction: [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-012 — State meanings

**Authority / maturity:** [SRS UX-012](SRS.md#ux-012--state-meanings); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-12; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** BR-004–BR-008; FR-003; OD-12.

**Acceptance — UX-012.AC1:** T/D: inject each state; verify qualification, safe return, retained revision and no protected disclosure.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Loading/error/denial/null states exist; missing/partial/revision/concurrent-edit/last-known and complete business-specific meanings are incomplete. Impact/evidence/direction: [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-013 — Warnings and interruption

**Authority / maturity:** [SRS UX-013](SRS.md#ux-013--warnings-and-interruption); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-13; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** FR-048–FR-050; OD-09; OD-14.

**Acceptance — UX-013.AC1:** T/D: acknowledging a warning leaves the underlying issue unresolved; urgency requires an adopted criterion and owner.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Inline errors and attention warnings exist; impact/owner/reliance qualification and agreed urgency rules do not consistently govern warnings. Impact/evidence/direction: [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-014 — Predictable exploration and local saves

**Authority / maturity:** [SRS UX-014](SRS.md#ux-014--predictable-exploration-and-local-saves); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-14; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** FR-049–FR-050; NFR-003; OD-12; OD-14.

**Acceptance — UX-014.AC1:** T/D: navigate back, fail/retry/cancel a save and exercise concurrent edits; no external execution or duplicate logical record is claimed.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Labelled navigation, tables and dialogs exist; retained business scope/revision, local Save/Cancel/recovery and exports are incomplete. Impact/evidence/direction: [G22](IMPLEMENTATION-GAP-ANALYSIS.md#g22), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-015 — Personalisation versus permission

**Authority / maturity:** [SRS UX-015](SRS.md#ux-015--personalisation-versus-permission); Mandatory — established.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-15; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** ACC-001–ACC-008; UX-006.

**Acceptance — UX-015.AC1:** T: pin denied work, change contexts and revoke scope; no new access or policy change results.

**Implementation evidence — prior individual assessment:** ; **3. Implemented but semantically incorrect**; severity **High**. Personal focus does not itself widen backend grants, but title-derived defaults, hardcoded owner visibility and incomplete scope/effective grants violate the required separation. Impact/evidence/direction: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-016 — Accessible operation

**Authority / maturity:** [SRS UX-016](SRS.md#ux-016--accessible-operation); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-16; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** NFR-007; OD-12.

**Acceptance — UX-016.AC1:** D/T: complete agreed journeys by keyboard and agreed assistive conditions; check dialog focus restoration, labels, error recovery, text meaning and reduced-motion preference where motion exists. Formal conformance remains unselected.

**Implementation evidence — prior individual assessment:** ; **6. Cannot verify from repository evidence**; severity **Medium**. Responsive/semantic/labelled component groundwork exists; keyboard/assistive/zoom/phone journey completion and critical-caveat interpretation are not verified. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-017 — Responsive web and phone checks

**Authority / maturity:** [SRS UX-017](SRS.md#ux-017--responsive-web-and-phone-checks); Mandatory — established.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-17; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** NFR-006–NFR-008; OD-12; OD-13.

**Acceptance — UX-017.AC1:** D/T: complete reporting on approved website conditions and phone find/read/explain/note/status tasks without lost qualifications or mobile-only reduction of facts.

**Implementation evidence — prior individual assessment:** ; **2. Partially implemented**; severity **High**. Responsive web/sidebar layout is implemented; the specified phone note/follow-up and report/evidence tasks are not all available or tested. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24), [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### UX-018 — Representative UX acceptance

**Authority / maturity:** [SRS UX-018](SRS.md#ux-018--representative-ux-acceptance); Mandatory — proposed.

**Goals (source-explicit):** BG-02, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S07 GUX-18; S06 relevant journeys; BG-02/BG-05.

**Dependencies / gates (source):** GOV-001–GOV-002; NFR-006–NFR-007; OD-01; OD-02; OD-12; OD-13.

**Acceptance — UX-018.AC1:** D/I: retain evaluation evidence and explicit pass/fail/blocked outcomes; absent bounds or sessions cannot pass.

**Implementation evidence — prior individual assessment:** ; **6. Cannot verify from repository evidence**; severity **Medium**. Repository tests cannot establish representative stakeholder acceptance of UX-AC01–10; adopted bounds and role-holder validation are absent. Impact/evidence/direction: [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### INT-001 — Source contract and authorised scope

**Authority / maturity:** [SRS INT-001](SRS.md#int-001--source-contract-and-authorised-scope); Mandatory — established.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S05–S07; BG-03/BG-05.

**Dependencies / gates (source):** BR-001–BR-003; BR-009; ACC-008; OD-10.

**Acceptance — INT-001.AC1:** I/T: inspect the contract and test representative mappings and unsupported fields.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03), [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### INT-002 — Missing and partial integration evidence

**Authority / maturity:** [SRS INT-002](SRS.md#int-002--missing-and-partial-integration-evidence); Mandatory — established.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S05–S07; BG-03/BG-05.

**Dependencies / gates (source):** DR-001–DR-055; BR-004; REL-003.

**Acceptance — INT-002.AC1:** T: supply aggregate-only, missing-unit and missing-field cases; no invented lines, stock, times or zero values appear.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04), [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### INT-003 — Read-only integration boundary

**Authority / maturity:** [SRS INT-003](SRS.md#int-003--read-only-integration-boundary); Mandatory — established.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S05–S07; BG-03/BG-05.

**Dependencies / gates (source):** CON-001; ACC-008.

**Acceptance — INT-003.AC1:** T/I: observe adopted integration calls for ordinary and failed/retry tasks; no external create/update/delete, purchase, payment or task-change request occurs.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### INT-004 — Repeat and correction handling

**Authority / maturity:** [SRS INT-004](SRS.md#int-004--repeat-and-correction-handling); Mandatory — established.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S05–S07; BG-03/BG-05.

**Dependencies / gates (source):** BR-001; BR-005; REL-005.

**Acceptance — INT-004.AC1:** T/A: replay identical evidence, then supply a corrected version; totals change only for the evidenced business change and retain original history.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### INT-005 — Dataset failure isolation and impact

**Authority / maturity:** [SRS INT-005](SRS.md#int-005--dataset-failure-isolation-and-impact); Mandatory — established.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S05–S07; BG-03/BG-05.

**Dependencies / gates (source):** FR-003; BR-007–BR-008; OD-13.

**Acceptance — INT-005.AC1:** T: fail one contributing dataset while another succeeds; readiness and each affected result remain qualified with a recoverable reason.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### INT-006 — Purpose-bound contextual inputs

**Authority / maturity:** [SRS INT-006](SRS.md#int-006--purpose-bound-contextual-inputs); Conditional mandatory — proposed.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S05–S07; BG-03/BG-05.

**Dependencies / gates (source):** FR-046; DR-045; ACC-008; OD-01; OD-10.

**Acceptance — INT-006.AC1:** T/I: test review/article/task examples and unknown publication time; no inferred visits or verified closure.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### INT-007 — Export and distribution boundary

**Authority / maturity:** [SRS INT-007](SRS.md#int-007--export-and-distribution-boundary); Mandatory — established.

**Goals (source-explicit):** BG-03, BG-05. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S05–S07; BG-03/BG-05.

**Dependencies / gates (source):** FR-051–FR-053; ACC-001; ACC-008; OD-10; OD-13.

**Acceptance — INT-007.AC1:** T/I: prepare and download a permitted report with no distribution grant; no email/message/delivery assertion occurs.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### REL-001 — End-to-end explanation

**Authority / maturity:** [SRS REL-001](SRS.md#rel-001--end-to-end-explanation); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S03 shared contracts; S06 SEM/FR acceptance; S07 GUX-10–14; BG-03.

**Dependencies / gates (source):** FR-006; DR-040–DR-041; BR-005–BR-006; NFR-009.

**Acceptance — REL-001.AC1:** A/I/T: reproduce a selected value and inspect a missing-detail path; absent detail stays unavailable.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### REL-002 — Reconciliation differences

**Authority / maturity:** [SRS REL-002](SRS.md#rel-002--reconciliation-differences); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S03 shared contracts; S06 SEM/FR acceptance; S07 GUX-10–14; BG-03.

**Dependencies / gates (source):** FR-004; FR-012–FR-016; FR-021–FR-022; BR-003; BR-010; OD-08; OD-09.

**Acceptance — REL-002.AC1:** A/T: explain commercial, purchase and settlement fixtures; incompatible bases are qualified, not forced equal.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03), [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### REL-003 — Business completeness

**Authority / maturity:** [SRS REL-003](SRS.md#rel-003--business-completeness); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S03 shared contracts; S06 SEM/FR acceptance; S07 GUX-10–14; BG-03.

**Dependencies / gates (source):** BR-007; DR-041; OD-09; OD-13.

**Acceptance — REL-003.AC1:** T/A: evaluate known and unknown expected populations; successful ingestion alone never creates complete business coverage.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### REL-004 — Freshness and eligibility

**Authority / maturity:** [SRS REL-004](SRS.md#rel-004--freshness-and-eligibility); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S03 shared contracts; S06 SEM/FR acceptance; S07 GUX-10–14; BG-03.

**Dependencies / gates (source):** BR-008; OD-04; OD-06; OD-09; OD-13.

**Acceptance — REL-004.AC1:** T: receive an old count today and inspect a forecast with no eligibility policy; old cutoff and unresolved fitness persist.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### REL-005 — Restatement and original issue history

**Authority / maturity:** [SRS REL-005](SRS.md#rel-005--restatement-and-original-issue-history); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S03 shared contracts; S06 SEM/FR acceptance; S07 GUX-10–14; BG-03.

**Dependencies / gates (source):** BR-005; FR-002; FR-043–FR-044; DR-048; NFR-009.

**Acceptance — REL-005.AC1:** T/A: late correction produces a linked new revision; original results remain reproducible under the adopted retention policy.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14), [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### REL-006 — Retry, failure and accountable local persistence

**Authority / maturity:** [SRS REL-006](SRS.md#rel-006--retry-failure-and-accountable-local-persistence); Mandatory — proposed.

**Goals (source-explicit):** BG-03. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S03 shared contracts; S06 SEM/FR acceptance; S07 GUX-10–14; BG-03.

**Dependencies / gates (source):** FR-049–FR-053; UX-014; NFR-003; OD-14.

**Acceptance — REL-006.AC1:** T/D: interrupt/retry local save and export; verify confirmation, no duplicate record, retained permitted draft and agreed restore behaviour.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. Indirect gap evidence through related contracts: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01), [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02), [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G22](IMPLEMENTATION-GAP-ANALYSIS.md#g22), [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24). Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-001 — Net sales and sales mix

**Authority / maturity:** [SRS MET-001](SRS.md#met-001--net-sales-and-sales-mix); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C01, C02, C18.

**Upstream trace:** S04 K01. PRD trace: S01; S05 K01; C01,C02,C18; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-001; DR-040–DR-042; BR-003.

**Acceptance — MET-001.AC1:** A/T: independently recompute from the eligible fixture inputs. Line/modifier/adjustment consideration reconciles without double counting. Unmapped products remain in known totals with mix coverage qualified; zero/missing denominator is unavailable, and negative reversal periods remain negative.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Gross-minus-GST and product amount/quantity metrics exist; eligible adjustment/liability/tip/surcharge rules and mix/unit coverage are incomplete. Impact/evidence/direction: [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-002 — Spend per actual cover and average check

**Authority / maturity:** [SRS MET-002](SRS.md#met-002--spend-per-actual-cover-and-average-check); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C04, C18.

**Upstream trace:** S04 K02. PRD trace: S01; S05 K02; C04,C18; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-002; DR-040–DR-042.

**Acceptance — MET-002.AC1:** A/T: independently recompute from the eligible fixture inputs. Six booked persons, four actual covers and two checks never share a denominator by default. Unlinked sales or unknown cover count qualifies/unavailable spend per cover; missing counts never become booked size.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Gross venue sales divided by party-size-derived covers is not attributed net spend per actual cover; independent average-check population is absent. Impact/evidence/direction: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-003 — Service duration distribution

**Authority / maturity:** [SRS MET-003](SRS.md#met-003--service-duration-distribution); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C05, C20.

**Upstream trace:** S04 K03. PRD trace: S01; S05 K03; C05,C20; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-003; DR-040–DR-042; DR-054.

**Acceptance — MET-003.AC1:** A/T: independently recompute from the eligible fixture inputs. Missing service timestamp is excluded with visible coverage; check closure is not a proxy. Invalid time order is an exception, not negative duration silently averaged away. DR-054 event pairing, per-event/per-unit weighting and remake treatment shall be explicit; verify AT-04.STAGE.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. Reservation duration is not observed order-to-fulfilment duration; no eligible stage distribution exists. Impact/evidence/direction: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-004 — Reservation no-show rate

**Authority / maturity:** [SRS MET-004](SRS.md#met-004--reservation-no-show-rate); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C04.

**Upstream trace:** S04 K04. PRD trace: S01; S05 K04; C04; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-004; DR-040–DR-042.

**Acceptance — MET-004.AC1:** A/T: independently recompute from the eligible fixture inputs. A missing arrival link remains unresolved without assessment evidence. Zero/missing eligible denominator produces unavailable rate; cancelled-ineligible bookings do not inflate it.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. No-shows / all reservation rows includes unvalidated cancellation/walk-in eligibility; source status is preserved but denominator semantics are wrong. Impact/evidence/direction: [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-005 — Stock balance and count variance

**Authority / maturity:** [SRS MET-005](SRS.md#met-005--stock-balance-and-count-variance); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C09, C10.

**Upstream trace:** S04 K05. PRD trace: S01; S05 K05; C09,C10; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-005; DR-040–DR-042.

**Acceptance — MET-005.AC1:** A/T: independently recompute from the eligible fixture inputs. A count eight/book ten gives -2 physical units; absence of a count is not zero. Value variance additionally requires cost method/date; transfer/linked loss counted once.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Count observations exist; movement-explained book stock and matched physical count variance do not. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-006 — Usage variance and recorded waste

**Authority / maturity:** [SRS MET-006](SRS.md#met-006--usage-variance-and-recorded-waste); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C11, C12.

**Upstream trace:** S04 K06. PRD trace: S01; S05 K06; C11,C12; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-006; DR-040–DR-042.

**Acceptance — MET-006.AC1:** A/T: independently recompute from the eligible fixture inputs. Do not sum litres with kilograms or count residual as known waste. Theoretical calculation shows recipe/modifier/yield coverage; known disposal is not subtracted twice in residual reconciliation.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Waste observations exist, but business waste projection and compatible theoretical/residual usage assessment are absent. Impact/evidence/direction: [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06), [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-007 — COGS, gross profit and gross margin

**Authority / maturity:** [SRS MET-007](SRS.md#met-007--cogs-gross-profit-and-gross-margin); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C01, C18.

**Upstream trace:** S04 K07. PRD trace: S01; S05 K07; C01,C18; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-007; DR-040–DR-042; DR-050.

**Acceptance — MET-007.AC1:** A/T: independently recompute from the eligible fixture inputs. Purchases/payments alone cannot supply COGS. Missing valuation/cost coverage makes profit partial/estimated or unavailable; waste is not double-subtracted. Markup uses cost denominator and is separately named. Verify the explicit complete result in AT-09.POS, unsupported result in AT-09.INC and cost allocation restatement in AT-09.REV.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Invoice purchases/food-cost ratios are implemented under a COGS label without consumption valuation and matched net revenue. Impact/evidence/direction: [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-008 — Labour cost and productivity

**Authority / maturity:** [SRS MET-008](SRS.md#met-008--labour-cost-and-productivity); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C14, C15, C16, C18.

**Upstream trace:** S04 K08. PRD trace: S01; S05 K08; C14,C15,C16,C18; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-008; DR-040–DR-042.

**Acceptance — MET-008.AC1:** A/T: independently recompute from the eligible fixture inputs. Wages-only, loaded, forecast, provisional and final results are distinguishable. Accrual/payment and estimate/final replacements do not double cost; overlapped departmental allocations cannot inflate hours.

**Implementation evidence — prior individual assessment:** **3. Implemented but semantically incorrect**; severity **High**. Daily cost/hour ratios exist; cost component/payable scope and matching net-sales productivity contract are not satisfied. Impact/evidence/direction: [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-009 — Prime cost

**Authority / maturity:** [SRS MET-009](SRS.md#met-009--prime-cost); Optional — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C18, C19.

**Upstream trace:** S04 K09. PRD trace: S01; S05 K09; C18,C19; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-009; DR-040–DR-042.

**Acceptance — MET-009.AC1:** A/T: independently recompute from the eligible fixture inputs. Missing one component means incomplete/unavailable prime cost, not the other component alone called total. No universal prime-cost target is adopted.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No independently defined COGS-plus-labour prime-cost measure with complete component basis exists. Impact/evidence/direction: [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-010 — Budget variance and forecast error

**Authority / maturity:** [SRS MET-010](SRS.md#met-010--budget-variance-and-forecast-error); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C19, C20.

**Upstream trace:** S04 K10. PRD trace: S01; S05 K10; C19,C20; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-010; DR-040–DR-042.

**Acceptance — MET-010.AC1:** A/T: independently recompute from the eligible fixture inputs. No approved budget means unavailable budget variance. Outcome evaluation uses the pre-outcome forecast version; zero actual in a relative-error calculation follows the agreed unavailable/alternative rule.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **Medium**. MAE/MAPE on retained forecasts exists; approved budget variance and agreed weighted eligible error basis/disclosure are incomplete. Impact/evidence/direction: [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-011 — Reconciliation exception count, amount and age

**Authority / maturity:** [SRS MET-011](SRS.md#met-011--reconciliation-exception-count-amount-and-age); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C02, C03, C07, C08, C10, C15, C17, C22.

**Upstream trace:** S04 K11. PRD trace: S01; S05 K11; C02,C03,C07,C08,C10,C15,C17,C22; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-011; DR-040–DR-042.

**Acceptance — MET-011.AC1:** A/T: independently recompute from the eligible fixture inputs. Resolved/superseded issues are not counted as current unresolved work. Unknown start/value remains unknown, not zero age/value; invoice, cash and staffing exception quantities are not summed as a single monetary loss.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Open source/reconciliation/invoice issues exist; business-age, remaining compatible amount and partial lifecycle history are incomplete. Impact/evidence/direction: [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21), [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-012 — Applicable control and verified action outcomes

**Authority / maturity:** [SRS MET-012](SRS.md#met-012--applicable-control-and-verified-action-outcomes); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C13, C20, C21, C22.

**Upstream trace:** S04 K12. PRD trace: S01; S05 K12; C13,C20,C21,C22; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-012; DR-040–DR-042; DR-043; DR-055.

**Acceptance — MET-012.AC1:** A/T: independently recompute from the eligible fixture inputs. Unknown applicability is reported separately from pass/fail or overdue. Locally marked complete but unverified work is not counted as verified resolution. The expected DR-055 population includes due occurrences with no observation; AT-11.DUE verifies separate due and outcome states.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No applicable-control or evidence-verified action outcome calculation exists. Impact/evidence/direction: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23), [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-013 — Forecast revenue and product-sales outputs

**Authority / maturity:** [SRS MET-013](SRS.md#met-013--forecast-revenue-and-product-sales-outputs); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C01, C19.

**Upstream trace:** S06 analytical extension. PRD trace: S01; S06; C01,C19; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-013; DR-040–DR-042.

**Acceptance — MET-013.AC1:** A/T: independently recompute from the eligible fixture inputs. A quantity forecast does not appear as money; expected money is not units times an unstated price. Stockout-constrained sales, new products and missing history are disclosed; inadequacy produces qualified scenarios/unavailable results.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Revenue quantiles exist; selected product quantities/money, retained coverage and price/availability assumptions are absent. Impact/evidence/direction: [G16](IMPLEMENTATION-GAP-ANALYSIS.md#g16), [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-014 — Availability, stockout and supplier outcomes

**Authority / maturity:** [SRS MET-014](SRS.md#met-014--availability-stockout-and-supplier-outcomes); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C01, C06, C07, C12.

**Upstream trace:** S06 analytical extension. PRD trace: S01; S02,S05; C01,C06,C07,C12; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-014; DR-040–DR-042; DR-052; DR-053.

**Acceptance — MET-014.AC1:** A/T: independently recompute from the eligible fixture inputs. Zero sales alone is not a stockout; a rejected delivery is not full usable supply. Unknown promised time prevents an on-time score; unknown excess policy prevents authoritative excess classification. DR-053 identifies promised tranches and selected scoring baseline; DR-052 identifies quantity-specific availability and overlaps. Verify AT-06.PROMISE and AT-07.HOLD.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No observed stockout/availability or promised-versus-accepted supplier timeliness/in-full measure exists. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-015 — Report automation outcome

**Authority / maturity:** [SRS MET-015](SRS.md#met-015--report-automation-outcome); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S06 analytical extension. PRD trace: S01; S06; C20,C22; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-015; DR-040–DR-042; DR-044; DR-048.

**Acceptance — MET-015.AC1:** A/T: independently recompute from the eligible fixture inputs. Less preparation time with more hidden gaps or correction work is not unqualified improvement. No baseline or agreed deadline means the corresponding improvement/timeliness conclusion is unavailable. AT-01.EFFORT sums report/revision/stage-linked DR-044 observations in a declared comparison window; elapsed readiness time is not person-time.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No observed report assembly/checking/rework effort and agreed deadline/readiness outcome measurement exists. Impact/evidence/direction: [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### MET-016 — Operating profit and cash commitments

**Authority / maturity:** [SRS MET-016](SRS.md#met-016--operating-profit-and-cash-commitments); Mandatory — proposed.

**Goals (source-explicit):** BG-01, BG-02, BG-03. **Capabilities (source-explicit):** C08, C16, C18, C19.

**Upstream trace:** S06 analytical extension. PRD trace: S01; S01,S02,S05; C08,C16,C18,C19; BG-01–BG-03.

**Dependencies / gates (source):** OD-03,OD-08,OD-09; OD-04/OD-05 for forecasting/stock; OD-10 for controls; OD-13 for report inclusion; S06 GOL-MET-016; DR-040–DR-042; DR-049; DR-050.

**Acceptance — MET-016.AC1:** A/T: independently recompute from the eligible fixture inputs. Missing rent/other included expenses prevents a complete net-profit claim. Open purchase commitments remain commitments rather than paid expenses; cash received cannot replace revenue recognition evidence. DR-049 recognised costs reconcile to the selected expense scope with operational/accounting overlaps counted once. Verify AT-09.POS, AT-09.INC and AT-09.REV; aggregate-only evidence remains explicitly aggregate-only.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **High**. No validated operating expense/profit, cash position, due-payable/liability or future-commitment measurement contract exists. Impact/evidence/direction: [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### GOV-003 — Validation and acceptance evidence

**Authority / maturity:** [SRS GOV-003](SRS.md#gov-003--validation-and-acceptance-evidence); Mandatory — established.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01; S06 section 14; S07 GUX-18.

**Dependencies / gates (source):** GOV-001; OD-01–OD-14 as applicable; QP-01–QP-10.

**Acceptance — GOV-003.AC1:** I: inspect the acceptance register for resolved dependencies and evidence-backed outcomes; absent evidence remains blocked.

**Implementation evidence:** No direct individual assessment exists for this consolidation record. The available assessment does not establish this complete contract. Related findings cannot establish this record's acceptance.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### CON-001 — Read-only connected systems

**Authority / maturity:** [SRS CON-001](SRS.md#con-001--read-only-connected-systems); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C22.

**Upstream trace:** S01; S05 agreed read-only/local-record boundary; S06 GOL-CON-001; BG-05; C22.

**Dependencies / gates (source):** none for the stated boundary.

**Acceptance — CON-001.AC1:** I/T: Reviewing or exporting a roster, order, task, booking, stock fact, payment or payroll result causes no connected-system change. A local proposal is labelled as local and does not claim source correction, external approval, publication, purchase or payment.

**Implementation evidence — prior individual assessment:** **1. Fully implemented**; severity **None**. Inspected connectors collect evidence without a source-system operational write path; local overrides stay inside Goldy's. Purpose/access validation is separately assessed under ACC. Impact/evidence/direction: No gap within the bounded assessment. Implementation evidence: [E02](#e02), [E21](#e21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### CON-002 — Full business coverage independent of source availability

**Authority / maturity:** [SRS CON-002](SRS.md#con-002--full-business-coverage-independent-of-source-availability); Mandatory — established.

**Goals (source-explicit):** BG-03. **Capabilities (source-explicit):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01 charter coverage; S06 GOL-CON-002; BG-03; C01–C22.

**Dependencies / gates (source):** none for the stated boundary.

**Acceptance — CON-002.AC1:** I/T: Each charter domain resolves to a minimum information contract in section 8. An absent source is recorded as an evidence gap with affected decisions and measures, rather than deleting the requirement or fabricating activity.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **High**. Implemented domains cover only a subset of the charter; absent sources and absent models still exclude relevant decisions from the application. Impact/evidence/direction: [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05), [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09), [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12), [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23), [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### CON-003 — Human authority over decisions

**Authority / maturity:** [SRS CON-003](SRS.md#con-003--human-authority-over-decisions); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C20, C22.

**Upstream trace:** S01; S05 human/external authority; S06 GOL-CON-003; BG-05; C20/C22.

**Dependencies / gates (source):** none for the stated boundary.

**Acceptance — CON-003.AC1:** I/T: A forecast is distinguishable from a selected plan. A suggested response remains proposed until the responsible person records a decision; that local decision still does not prove external execution.

**Implementation evidence — prior individual assessment:** **1. Fully implemented**; severity **None**. Forecast outputs and reporting tools do not publish rosters, order stock, pay workers or execute operating decisions; consequential action remains human/external within inspected paths. Impact/evidence/direction: No gap within the bounded assessment. Implementation evidence: [E11](#e11), [E21](#e21).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### CON-004 — Verified applicability before compliance claims

**Authority / maturity:** [SRS CON-004](SRS.md#con-004--verified-applicability-before-compliance-claims); Mandatory — established.

**Goals (source-explicit):** BG-05. **Capabilities (source-explicit):** C13, C16, C21, C22.

**Upstream trace:** S01; S02 applicability limits; S06 GOL-CON-004; BG-05; C13/C16/C21/C22.

**Dependencies / gates (source):** OD-07; OD-08; OD-10 for the corresponding applicability claim.

**Acceptance — CON-004.AC1:** I/T: Unknown jurisdiction, employer coverage, licence, business category or record-class obligation produces “applicability unresolved”. No award, rate, control limit, universal retention period or compliance conclusion is inferred from a pub setting or job title.

**Implementation evidence — prior individual assessment:** **5. Not implemented**; severity **Medium**. No applicability/criterion/validator model or explicit unresolved-applicability workflow exists; absence of a compliance feature is not verified compliance. Impact/evidence/direction: [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### CON-005 — Current pub and opening planning scope

**Authority / maturity:** [SRS CON-005](SRS.md#con-005--current-pub-and-opening-planning-scope); Mandatory — established.

**Goals (source-explicit):** BG-04. **Capabilities (source-explicit):** C19.

**Upstream trace:** S05 current-pub/opening boundary; S06 GOL-CON-005; BG-04; C19.

**Dependencies / gates (source):** OD-11 for opening assumptions and any future scope expansion.

**Acceptance — CON-005.AC1:** I/T: Opening assumptions have a distinct site/scenario scope; they never appear as actual second-pub results. No current-venue total silently incorporates a proposed venue.

**Implementation evidence — prior individual assessment:** **2. Partially implemented**; severity **Medium**. Current-pub scope is supported without live second-pub totals; qualified opening decision evidence and scenarios are absent. Impact/evidence/direction: [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25).

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

### CON-006 — Business-first requirements and bounded work

**Authority / maturity:** [SRS CON-006](SRS.md#con-006--business-first-requirements-and-bounded-work); Mandatory — established.

**Goals (explanatory via capability/semantic coverage):** BG-02, BG-03. **Capabilities (inherited safeguard applicability):** C01–C22, as applicable to the adopted behaviour.

**Upstream trace:** S01 documentation-only/business-first boundary; S06 GOL-CON-006.

**Dependencies / gates (source):** none for the stated boundary.

**Acceptance — CON-006.AC1:** I/T: No requirement mandates a storage schema, platform, vendor, algorithm or framework. The package contains business contracts and verification criteria; no application inspection or downstream workflow is needed to complete it.

**Implementation evidence — prior individual assessment:** **1. Fully implemented**; severity **None**. This is a documentation/governance constraint: the independent baseline is preserved and this inspection writes only the requested report. It is not an application feature or build-readiness claim. Impact/evidence/direction: No gap within the bounded assessment. Evidence: this report’s inspection boundary, the unchanged reference checkout and independent authority cited above.

**Acceptance result:** Not established; no executed requirement-level evidence in this documentation engagement. Resolve the listed gates before an operational acceptance claim.

## Implementation evidence-bundle index

Use the gap analysis as the saved evidence authority; file pointers and observations are inherited at its stated commit. Its classification describes bounded repository findings, not present production behaviour. A source-data gap and a software gap require separate resolution.

| Gap | Prior finding | Inspected evidence bundles |
|---|---|---|
| [G01](IMPLEMENTATION-GAP-ANALYSIS.md#g01) | Weekly reporting is not a retained recurring business report | [E20](IMPLEMENTATION-GAP-ANALYSIS.md#e20), [E21](IMPLEMENTATION-GAP-ANALYSIS.md#e21), [E26](IMPLEMENTATION-GAP-ANALYSIS.md#e26) |
| [G02](IMPLEMENTATION-GAP-ANALYSIS.md#g02) | Business CSV and formatted Excel exports are absent | [E20](IMPLEMENTATION-GAP-ANALYSIS.md#e20), [E25](IMPLEMENTATION-GAP-ANALYSIS.md#e25), [E26](IMPLEMENTATION-GAP-ANALYSIS.md#e26) |
| [G03](IMPLEMENTATION-GAP-ANALYSIS.md#g03) | Purchases are presented as COGS and food cost | [E06](IMPLEMENTATION-GAP-ANALYSIS.md#e06), [E10](IMPLEMENTATION-GAP-ANALYSIS.md#e10), [E25](IMPLEMENTATION-GAP-ANALYSIS.md#e25) |
| [G04](IMPLEMENTATION-GAP-ANALYSIS.md#g04) | Actual covers and no-show denominators are conflated | [E05](IMPLEMENTATION-GAP-ANALYSIS.md#e05), [E06](IMPLEMENTATION-GAP-ANALYSIS.md#e06) |
| [G05](IMPLEMENTATION-GAP-ANALYSIS.md#g05) | Raw-only masters and purchasing commitments prevent useful replenishment | [E02](IMPLEMENTATION-GAP-ANALYSIS.md#e02), [E03](IMPLEMENTATION-GAP-ANALYSIS.md#e03), [E27](IMPLEMENTATION-GAP-ANALYSIS.md#e27) |
| [G06](IMPLEMENTATION-GAP-ANALYSIS.md#g06) | Canonical stock/count/waste stops before the business read model | [E06](IMPLEMENTATION-GAP-ANALYSIS.md#e06), [E07](IMPLEMENTATION-GAP-ANALYSIS.md#e07) |
| [G07](IMPLEMENTATION-GAP-ANALYSIS.md#g07) | Unknown values are converted into false numeric facts | [E04](IMPLEMENTATION-GAP-ANALYSIS.md#e04), [E07](IMPLEMENTATION-GAP-ANALYSIS.md#e07), [E10](IMPLEMENTATION-GAP-ANALYSIS.md#e10) |
| [G08](IMPLEMENTATION-GAP-ANALYSIS.md#g08) | Invoice and product identity is narrower than business identity | [E03](IMPLEMENTATION-GAP-ANALYSIS.md#e03), [E10](IMPLEMENTATION-GAP-ANALYSIS.md#e10), [E30](IMPLEMENTATION-GAP-ANALYSIS.md#e30) |
| [G09](IMPLEMENTATION-GAP-ANALYSIS.md#g09) | Sales, modifiers and payment relationships are incomplete | [E02](IMPLEMENTATION-GAP-ANALYSIS.md#e02), [E04](IMPLEMENTATION-GAP-ANALYSIS.md#e04), [E05](IMPLEMENTATION-GAP-ANALYSIS.md#e05) |
| [G10](IMPLEMENTATION-GAP-ANALYSIS.md#g10) | Recipes do not yet support effective modifier-aware consumption | [E08](IMPLEMENTATION-GAP-ANALYSIS.md#e08), [E27](IMPLEMENTATION-GAP-ANALYSIS.md#e27), [E30](IMPLEMENTATION-GAP-ANALYSIS.md#e30) |
| [G11](IMPLEMENTATION-GAP-ANALYSIS.md#g11) | Menu availability is inferred from positive price | [E08](IMPLEMENTATION-GAP-ANALYSIS.md#e08), [E30](IMPLEMENTATION-GAP-ANALYSIS.md#e30) |
| [G12](IMPLEMENTATION-GAP-ANALYSIS.md#g12) | Labour is a provisional daily aggregate, not independent work/pay evidence | [E02](IMPLEMENTATION-GAP-ANALYSIS.md#e02), [E09](IMPLEMENTATION-GAP-ANALYSIS.md#e09) |
| [G13](IMPLEMENTATION-GAP-ANALYSIS.md#g13) | Catalogue correctness does not propagate consistently to summaries | [E06](IMPLEMENTATION-GAP-ANALYSIS.md#e06), [E10](IMPLEMENTATION-GAP-ANALYSIS.md#e10), [E14](IMPLEMENTATION-GAP-ANALYSIS.md#e14) |
| [G14](IMPLEMENTATION-GAP-ANALYSIS.md#g14) | Temporal revision columns do not establish business-effective history | [E01](IMPLEMENTATION-GAP-ANALYSIS.md#e01), [E03](IMPLEMENTATION-GAP-ANALYSIS.md#e03), [E24](IMPLEMENTATION-GAP-ANALYSIS.md#e24), [E26](IMPLEMENTATION-GAP-ANALYSIS.md#e26) |
| [G15](IMPLEMENTATION-GAP-ANALYSIS.md#g15) | Provenance and decision eligibility are incomplete | [E13](IMPLEMENTATION-GAP-ANALYSIS.md#e13), [E20](IMPLEMENTATION-GAP-ANALYSIS.md#e20), [E29](IMPLEMENTATION-GAP-ANALYSIS.md#e29) |
| [G16](IMPLEMENTATION-GAP-ANALYSIS.md#g16) | Specific-product forecasting is not supported end to end | [E11](IMPLEMENTATION-GAP-ANALYSIS.md#e11), [E12](IMPLEMENTATION-GAP-ANALYSIS.md#e12), [E20](IMPLEMENTATION-GAP-ANALYSIS.md#e20) |
| [G17](IMPLEMENTATION-GAP-ANALYSIS.md#g17) | Revenue forecasts retain outputs but lose qualification and scenario context | [E11](IMPLEMENTATION-GAP-ANALYSIS.md#e11), [E12](IMPLEMENTATION-GAP-ANALYSIS.md#e12), [E13](IMPLEMENTATION-GAP-ANALYSIS.md#e13) |
| [G18](IMPLEMENTATION-GAP-ANALYSIS.md#g18) | Resource grants cannot express the independent access contract | [E09](IMPLEMENTATION-GAP-ANALYSIS.md#e09), [E15](IMPLEMENTATION-GAP-ANALYSIS.md#e15), [E16](IMPLEMENTATION-GAP-ANALYSIS.md#e16) |
| [G19](IMPLEMENTATION-GAP-ANALYSIS.md#g19) | Cross-domain and forecast reads have incomplete operand gates | [E09](IMPLEMENTATION-GAP-ANALYSIS.md#e09), [E10](IMPLEMENTATION-GAP-ANALYSIS.md#e10), [E12](IMPLEMENTATION-GAP-ANALYSIS.md#e12), [E16](IMPLEMENTATION-GAP-ANALYSIS.md#e16) |
| [G20](IMPLEMENTATION-GAP-ANALYSIS.md#g20) | Four focus presets and entity navigation conflict with agreed UX | [E17](IMPLEMENTATION-GAP-ANALYSIS.md#e17), [E25](IMPLEMENTATION-GAP-ANALYSIS.md#e25), [E28](IMPLEMENTATION-GAP-ANALYSIS.md#e28) |
| [G21](IMPLEMENTATION-GAP-ANALYSIS.md#g21) | My work and operative overrides are not local follow-up/proposal workflows | [E18](IMPLEMENTATION-GAP-ANALYSIS.md#e18), [E19](IMPLEMENTATION-GAP-ANALYSIS.md#e19) |
| [G22](IMPLEMENTATION-GAP-ANALYSIS.md#g22) | Business questions are bounded by compiled daily projections | [E20](IMPLEMENTATION-GAP-ANALYSIS.md#e20), [E21](IMPLEMENTATION-GAP-ANALYSIS.md#e21), [E25](IMPLEMENTATION-GAP-ANALYSIS.md#e25) |
| [G23](IMPLEMENTATION-GAP-ANALYSIS.md#g23) | Context, availability and controls have uneven or absent coverage | [E02](IMPLEMENTATION-GAP-ANALYSIS.md#e02), [E05](IMPLEMENTATION-GAP-ANALYSIS.md#e05), [E25](IMPLEMENTATION-GAP-ANALYSIS.md#e25), [E27](IMPLEMENTATION-GAP-ANALYSIS.md#e27) |
| [G24](IMPLEMENTATION-GAP-ANALYSIS.md#g24) | Recovery, access revocation and operating quality remain unverified | [E15](IMPLEMENTATION-GAP-ANALYSIS.md#e15), [E22](IMPLEMENTATION-GAP-ANALYSIS.md#e22), [E28](IMPLEMENTATION-GAP-ANALYSIS.md#e28) |
| [G25](IMPLEMENTATION-GAP-ANALYSIS.md#g25) | Budgets, cash exposure and opening planning have no business model | [E11](IMPLEMENTATION-GAP-ANALYSIS.md#e11), [E20](IMPLEMENTATION-GAP-ANALYSIS.md#e20), [E26](IMPLEMENTATION-GAP-ANALYSIS.md#e26), [E27](IMPLEMENTATION-GAP-ANALYSIS.md#e27) |

## Cross-domain acceptance and journey coverage

These scenarios supplement every individual AC1; fixture numbers are examples, not thresholds or business policies. None was executed against an application for this package. The revision 1.1 documented expected-value arithmetic was checked.

Fixture numbers below are inherited from S06 acceptance examples and are not policy thresholds or production observations.

- **AT-01 — Complete, delayed and failed-source report:** FR-001–FR-006, DR-048, MET-015, REL-003–REL-005, NFR-001. Adopt a sample weekly definition and cadence; verify identical input/definition results, missing-source qualification, explicit readiness/failure and linked reissue. Measure assembly, checking and rework against a comparable observed baseline; missing deadline/baseline blocks the associated benefit assertion.
- **AT-02 — CSV/Excel fidelity and denial:** FR-051–FR-053, NFR-012, ACC-002, INT-007. Export an authorised selection containing known zero, unknown, disputed, quoted/multiline and spreadsheet-like text. Verify row counts/totals, types/units, as-of/revision, explicit omissions, safe text and readability in approved tools. Denied detail stays absent; download claims no distribution.
- **AT-03 — Revenue/product forecast:** FR-041–FR-044, MET-013, NFR-011. Select revenue and one stable product/variant; verify money versus units, horizon, original issue version, price/availability assumptions, insufficient/new-product history and stockout qualification. Compare later eligible actuals with the original forecast; unknown actuals or undefined error denominator are not zero error.
- **AT-04 — Six booked, four attended, two checks:** FR-008–FR-010, DR-037–DR-039, MET-002–MET-004. Preserve six intended, four observed and two check occurrences; moves/splits do not add covers. Missing arrival does not automatically become no-show, and unlinked sales qualify spend per cover.
- **AT-05 — Sale, deposit, part payment and commercial reversal:** FR-012–FR-014, DR-007–DR-013. Preserve unpaid 100 sale separately from 100 deposit/liability under adopted recognition policy; allocate 60 to a compatible 100 charge and obtain 40 outstanding. Amount-only credit creates neither returned stock nor cash refund; zero-price fulfilled output can consume ingredients.
- **AT-06 — Partial receipt and consolidated invoice:** FR-019–FR-022, DR-018–DR-020, INT-002. Ten delivered packs/eight accepted/two rejected yield only eight accepted additions after known conversion. Many receipts and invoice/credit matches count once. Unknown conversion leaves pack facts known and base-unit balance incomplete; service expense creates no stock.
- **AT-07 — Count, transfer, waste and recipe change:** FR-023–FR-030, DR-022–DR-028, MET-005–MET-006. Eight observed/ten book gives −2 count variance with no automatic adjustment or waste/theft claim. Two-bottle internal transfer nets zero venue-wide; missing counterpart is visible. Known waste/disposal enters once. Effective recipes and modifier omissions/substitutions alter theoretical inputs under adopted attribution; incomplete yield/conversion prevents complete usage/cost.
- **AT-08 — Planned, actual, payable and paid:** FR-031–FR-037, DR-029–DR-036, MET-008. Preserve original/amended rosters, unfilled slots, unrostered work, unknown clock-out/break and scoped skill validity. Department allocations do not duplicate work. Unknown terms/on-costs prevent verified complete pay/loaded cost. Use overnight and daylight-saving fixtures to keep elapsed instants, operational periods and payroll calendar meaning distinct.
- **AT-09 — Cash, settlement, incomplete profit and restatement:** FR-015–FR-016, FR-038–FR-040, MET-007, MET-016, REL-002/005. Observed 98/expected 100 cash yields −2 variance; missing expected holding makes variance unavailable. Gross settlement 100 minus fee 3 and bank 97 does not lower product sales by 3. Missing valuation/expenses prevents final profit; late reversal creates an attributable report revision.
- **AT-10 — Proposal/follow-up lifecycle and failure:** FR-049–FR-050, DR-046–DR-047, UX-011/014, REL-006. Retain original and local proposal; demonstrate unassigned/accepted ownership, partial resolution, cancellation, completion claimed, verified closure and reopening under adopted authority. Local done/source-task status without required evidence remains unverified. Retry/concurrent edit does not silently duplicate or overwrite work; no external mutation occurs.
- **AT-11 — Opening and applicable control:** FR-045, FR-047, DR-043, CON-004–CON-005. Keep site scenarios/transfer assumptions/sensitivity separate from live current-pub facts. Missing costs/context prevents guaranteed new-site demand/profit. Unknown criterion/applicability remains unresolved; missing observation is neither pass nor breach.
- **AT-12 — Identity, aggregate-only and access revocation:** BR-001–BR-010, ACC-001–ACC-008, INT-004. Replay a fact without added activity; preserve two same-named products and same-number invoices from different issuers. Aggregate-only input has no fabricated detail; unknown expected population gives no invented coverage percentage. Test direct/search/derived-operand/forecast-list/note/export denial, sensitive aggregate handling and effective revocation, including newly accessed cached content.
- **AT-13 — Three contexts and home preferences:** UX-001–UX-010, UX-015, GOV-002. Validate FOH/BOH/Upper management, head/sub-head/staff distinctions, equal unranked home choices and scoped pins. Each primary destination has operational-value evidence; diagnostics do not displace the decision.
- **AT-14 — Accessibility, responsive use and recovery:** UX-012–UX-018, NFR-002–NFR-003, NFR-006–NFR-008, NFR-012. Complete adopted website reporting and phone quick-check journeys under approved keyboard/touch/assistive/browser conditions. Check focus, reflow, scope, caveats, failure input, lost connection and restoration. Bind quantitative criteria before judging completion, recovery, availability or volume acceptance.

S06 UJ-01 maps AT-01/02; UJ-02 maps AT-03; UJ-03 maps AT-08; UJ-04 maps AT-06/07; UJ-05 maps AT-04; UJ-06 maps AT-05/09; UJ-07 maps AT-11; UJ-08 maps AT-10/12. UJ-01a report-definition and UJ-08a specialist-evidence branches additionally test separate grants. AT-13/14 cross all adopted journeys. These supplement rather than replace requirement-level AC1 evidence.

## Quality-parameter binding and acceptance evidence

QP-01–QP-10 are unresolved parameter sets under SRS section 11.1; [OPEN-DECISIONS.md](OPEN-DECISIONS.md#unbound-quality-parameters) records their stakeholder functions, gates and affected requirements. Do not pass performance, availability, recovery, tolerance, volume, accessibility or export compatibility before the applicable parameter is adopted and measured.

An implementation/acceptance evidence record under GOV-003 identifies requirement/version and AC, maturity, evaluator and grant, adopted decision/parameter versions, fixture/population, method (I/T/A/D), expected/actual outcome, referenced execution evidence, pass/fail/blocked/not-applicable rationale and dated approval/change record. These are the existing SRS governance fields; they are not a new test plan or implementation backlog.

## Identifier crosswalk and change boundary

- PRD GOL-FR/DR/ACC/MET/NFR/CON suffixes map unchanged to SRS FR/DR/ACC/MET/NFR/CON.
- PRD GOL-SEM-001–GOL-SEM-010 map to BR-001–BR-010.
- EXPERIENCE GUX-01–GUX-18 map to UX-001–UX-018.
- E01–E43 map one-for-one to DR-001–DR-043; DR-044–DR-048 are already adopted as SRS proposed extensions, not new E IDs.
- C01–C22 map to BC-001–BC-022. K01–K12 map to MET-001–MET-012; MET-013–MET-016 are existing SRS analytical extensions.
- OD-01–OD-14, AS-01–AS-05, QP-01–QP-10, UJ-01–UJ-08 and AT-01–AT-14 retain existing meanings.

Revision 1.1 adds DR-049–DR-055 as proposed or conditional assertion contracts; no existing requirement was retired or renumbered. These have no PRD/E counterparts. Existing AC1 conditions were extended with named fixture subcases as recorded in REVISION-LOG.md. Future proposed additions stay outside the baseline until a dated scope decision and explicit SRS change preserve rationale, maturity, acceptance criteria and bidirectional trace. Existing implementation findings cannot redefine requirements.

## Revision 1.1 input → result → verification chains

These links supplement the individual acceptance records and preserve the existing AT/UJ namespaces. The [fixture document](SRS-VERIFICATION-FIXTURES.md) provides inputs, explicit fixture policies and expected values; [REVISION-LOG.md](REVISION-LOG.md) records verification and disagreements.

- **R01/R02/R11 → DR-049/DR-050 + DR-019/DR-024/DR-027/DR-036 → FR-028/FR-038 → MET-007/MET-016 → AT-09.POS, AT-09.INC, AT-09.REV → UJ-01/UJ-06.** Entry allocations and operational/accounting overlap reconciliation feed recognised costs; valuation allocations feed COGS, loss and closing value. Positive, missing-input and restatement cases are separate assertions.
- **R03 → DR-011/DR-012/DR-013 → FR-013/FR-014 → AT-05.REDEEM → UJ-06.** Cash receipt, liability release, allocation and outstanding charge conserve independent balances.
- **R04 → DR-051 + DR-029/DR-031–DR-033 → FR-031/FR-033 → AT-08.AVAIL → UJ-03.** Effective declarations and adopted precedence qualify coverage; no new staffing KPI or grant is invented.
- **R05 → DR-052 + DR-021/DR-024/DR-027 → FR-017/FR-018 → MET-014 → AT-07.HOLD → UJ-04.** Conditions/commitments and overlap evidence distinguish physical, usable and uncommitted quantities.
- **R06 → DR-053 + DR-018–DR-020 → FR-017/FR-019 → MET-014 → AT-06.PROMISE → UJ-04.** Quantity/window tranches and receipt allocations retain original and accepted revised baselines.
- **R07 → DR-054 + DR-008 → FR-010 → MET-003 → AT-04.STAGE → UJ-05.** Event quantity partitions, pairing and weighting make stage distributions reproducible.
- **R08 → BR-003 + DR-008/DR-010/DR-019 → MET-001/MET-007 → AT-05.TAX → UJ-01/UJ-06.** Exclusive derivations depend on sufficient tax/allocation evidence while inclusive observations survive.
- **R09 → DR-055 + DR-043 → FR-047 → MET-012 → AT-11.DUE → UJ-07.** Expected occurrence population exists independently of evidence; lateness and pass/fail remain distinct.
- **R10 → DR-044/DR-048 → MET-015 → AT-01.EFFORT → UJ-01/BG-01.** Report/revision/stage-linked person-time includes reissues and shared allocation.
- **R12 → BR-002/DR-003 → AT-08.BOUNDARY → time-dependent journeys.** Candidate boundary convention stays unresolved until an adopted policy/version exists.
