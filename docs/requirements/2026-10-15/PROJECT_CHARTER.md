# Goldy's — Independent Business Requirements Project

## Objective

Produce a comprehensive Software Requirements Specification (SRS)
for Goldy's Unified Data Platform.

This is a business requirements and product strategy project,
NOT a software implementation task.

The SRS must establish what the business requires independently
of the application's current implementation.

## Primary objectives

### 1. Canonical business data model

Define the entities, facts, relationships, minimum required
fields, business rules and data semantics needed to represent
a hospitality venue's operations.

Cover, at minimum:
- Sales, transactions, items, modifiers and payments
- Products, menus, prices and discounts
- Inventory, purchasing, stock movements and wastage
- Recipes, ingredients and consumption
- Employees, positions, shifts, rosters and timesheets
- Labour hours, costs and productivity
- Reservations, covers and service periods
- Suppliers, invoices and purchasing costs
- Operational KPIs and financial measurements

Do not assume that existing vendor schemas are correct or
complete. Define the business meaning before source mapping.

### 2. Business-led user experience

Design around end users' responsibilities, decisions and KPIs.

Relevant roles include:
- Owner
- General manager
- Venue manager
- Kitchen manager
- Front-of-house manager
- Operational staff

Validate actual responsibilities and permissions.
Do not infer these solely from a job title.

For every proposed feature, establish:
Role -> Responsibility -> Decision -> Information -> Action -> KPI

Features without demonstrable operational value must be
removed, consolidated, or relocated to secondary interfaces.

### 3. Software Requirements Specification

Produce independently testable requirements with:
- Stable requirement identifiers
- Explicit business justification
- Functional and non-functional requirements
- Minimum data contracts
- Relationships and invariants
- Role-based workflows and user journeys
- Acceptance criteria
- Traceability to business goals
- Assumptions, uncertainties and open decisions

## Governing principles

1. Business requirements precede technical architecture.
2. Current implementation is evidence, not authority.
3. An implemented schema is not proof of a business requirement.
4. A missing data source is not proof that a domain is unnecessary.
5. Atomic facts take priority over precomputed reports.
6. All metrics require unambiguous definitions and units.
7. Distinguish zero, missing, unknown and not applicable.
8. Access permissions and user responsibilities are distinct.
9. A role's primary interface should prioritise actionable work.
10. Diagnostics must not dominate business workflows.
11. Do not invent business policies or regulatory obligations.
12. Cite external standards and record unresolved assumptions.

## Constraints

- No application code changes.
- No production database changes.
- No infrastructure changes.
- No migrations or deployments.
- No automated implementation planning unless requested.
- No wholesale copying of the current Goldy's PRD.
- No unapproved assumptions treated as established facts.

## Work sequence

Phase A: Independent hospitality-domain research
Phase B: Business capabilities and role research
Phase C: Business-first product requirements
Phase D: Role-centred UX requirements
Phase E: Current implementation gap analysis
Phase F: Consolidated SRS and validation

## Final output

A self-contained documentation package under `deliverables/`.

