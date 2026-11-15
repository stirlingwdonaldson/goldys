# UI handoff implementation — 15 November 2026

Local implementation of the 20-item handoff, based on `807a4e74`.
No permission policy, metric definition, schema, provider integration or production operation changed.

## Sorting coverage

All interactive record sorting uses underlying numeric/date/text values, nulls
last in both directions, then a stable identity. Filters and permission checks
precede ordering; ordering precedes paging. Server controls include sort in query
keys and URL state, reset the page on sort changes, and hide superseded rows and
`aria-sort` while the replacement result loads. A single header is active at once.

| Surface | Exposed sorting | Complete scope / implementation |
| --- | --- | --- |
| Sales | Date, Total sales, GST, Net | Authorized filtered daily rows sorted in SalesService before page slicing; source filter runs there too. |
| Reservations | Date, Covers | Entire authorized range returned by getCoversTrend; shared DataTable sorts before client pagination. |
| Invoices | Supplier, Invoice, Date, Due, Total, Purchase, PDF | InvoiceReportingService filters all authorized current headers, sorts then slices; invoice ID breaks ties. |
| Sales detail: payments | Trading date, Sale, Payment type, LS Pay mode, Reconciled, Register, Staff, Amount, Tip, Surcharge, Tendered, Count | CanonicalPaymentListQuery maps fields to typed entity properties; database ordering before offset/limit, UUID ties. |
| Sales detail: deleted orders | Trading date, Sale, Order type, Note, Staff, Deleted by, Register, Table, Total inc tax, Total ex tax, Tax, Cost | CanonicalDeletedSaleListQuery orders all matching current records before offset/limit, UUID ties. |
| Sales detail: items | Trading date, Sale, Receipt line, Item, Product number, SKU, Category, Order type, Quantity, Sold price, Cost, Amount | ResolvedSaleItemListQuery allowlisted SQL ordering before limit, composite source/site/date/line ties. |
| Data: canonical | Every supplied column, enumerated below | CanonicalBrowseQuery explicit field/property maps per entity, database ordering before offset/limit, ID ties. |
| Data: resolved | Every supplied column, enumerated below | DataExplorerQueryImpl explicit field/property maps per domain, database ordering before offset/limit, composite primary-key ties. |
| Kitchen: UOM totals | Measure, Line total, Quantity, Comparable unit cost | Complete inventory-lines aggregate groups; client sort before paging. |
| Kitchen: suppliers | Supplier, Invoice purchases, WET | Complete supplier aggregate groups; client sort before paging. |
| Kitchen: invoice flags | Flag, Invoice, PDF, Detail | Complete ingest-flag list; client sort before paging. |
| Recipes | Recipe, CTB cost / portion, POS price observation | Complete authorized costing list after folder/search filters; client sort before paging. |
| Reviews: explorer | Newest, Oldest, Highest rating, Lowest rating | getReviews returns all matching authorized reviews; client sort before paging; reviewId ties. |
| Reviews: source comparison | Source, Reviews, Avg rating, Positive, Negative | Complete matching source aggregates; shared DataTable. |
| Query builder | Registered selected field / aggregate and direction | QueryCompiler validates registered columns, adds NULLS LAST and group/record identity ties before SQL limit. Result table has no secondary page-local sort controls. |

### Intentionally fixed ordering

No new sort controls were added. Products uses group/category/product order with
search over the full assignment list. Dashboard cards use favourite/title order;
conversations use updated time; My work uses severity/routing rank; top sellers
retain metric ranking; chart data follows series order. Weekly report tables,
invoice lines, recipe ingredients, raw records, logs, stock/waste observations,
reconciliation/audit/rule lists, forecast tables, network lists, and provenance
keep their defined report, event, date or structural order. Plain text table
headers do not expose sorting. Source-label columns are descriptive/filterable,
not interactive sort headers. Capped semantic results are ordered at the query
boundary; they are never re-ranked as if the cap were the complete population.

### Explorer field inventory

The following is extracted from the explicit allowlists, not inferred from display labels.

#### Canonical

- `daily_sales`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `trading_date`, `total_sales`, `gst_total`, `net_total`.
- `product_sales`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `trading_date`, `product_name_key`, `quantity_sold`, `amount`.
- `reservation`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `reservation_at`, `party_size`, `status`.
- `invoice`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `supplier_name`, `invoice_number`, `invoice_date`, `due_date`, `total_amount`, `purchase_number`, `pdf_filename`, `account_number`, `tax_code`, `amount_ex_tax`, `gst_amount`, `freight_amount`, `freight_gst_amount`.
- `invoice_line`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `invoice_number`, `invoice_date`, `product_name_key`, `stock_code`, `quantity`, `unit_cost`, `line_total`, `category`, `uom`, `unit_quantity`, `pack_size`, `wet_amount`.
- `labour_entry`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `staff_ref`, `department`, `labour_date`, `scheduled_hours`, `actual_hours`, `scheduled_cost`, `actual_cost`, `shift_start`, `shift_end`.
- `stock_count`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `counted_date`, `product_name_key`, `quantity_on_hand`.
- `wastage`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `wastage_date`, `product_name_key`, `quantity`, `reason`.
- `sale_item`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `item_name`, `quantity_sold`, `amount`, `sale_number`, `category_name`, `product_number`, `sku`, `sold_price_inc_tax`, `total_tax`, `cost_inc_tax`, `order_type`, `sale_type`, `staff_name`, `register_name`, `table_number`.
- `payment`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `trading_date`, `sale_number`, `payment_type_name`, `amount`, `tip`, `payment_count`.
- `deleted_sale`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `trading_date`, `sale_number`, `total_inc_tax`, `total_tax`.
- `shift`: `id`, `logical_entity_id`, `source_system`, `source_record_ref`, `raw_record_id`, `valid_from`, `valid_to`, `recorded_at`, `superseded_at`, `staff_member_ref`, `shift_start`, `shift_end`.

#### Resolved

- `resolved_daily_sales`: `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at`, `trading_date`, `total_sales`, `net_sales`, `gst`.
- `resolved_product_sales`: `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at`, `trading_date`, `product_name_key`, `quantity_sold`, `amount`.
- `resolved_reservation_day`: `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at`, `trading_date`, `service_period`, `bookings`, `attended`, `covers`, `cancelled`, `no_shows`, `walk_ins`.
- `resolved_labour_day`: `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at`, `trading_date`, `department`, `scheduled_hours`, `actual_hours`, `scheduled_cost`, `actual_cost`.
- `resolved_inventory_day`: `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at`, `trading_date`, `purchases`, `wastage`, `stock_on_hand`.
- `resolved_payment_day`: `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at`, `trading_date`, `payment_type_name`, `amount`, `tip`, `payment_count`.
- `resolved_deleted_sale_day`: `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at`, `trading_date`, `deleted_count`, `total_inc_tax`, `total_tax`.
- `resolved_sale_item_day`: `resolution_type`, `authoritative_source`, `has_conflict`, `resolved_at`, `trading_date`, `category_name`, `quantity`, `amount`.

## Evidence and limitations

Regression coverage includes Melbourne date arithmetic; mixed incompleteness
warnings and real zero; independent loading/failure/denial/stale states; global
ordering over more than two pages with duplicates, missing values and zero;
URL scope and focus restoration; keyboard disclosure; and chart data alternatives.
Changed database paths have PostgreSQL 16 integration coverage. Browser fixtures
intercept all API traffic and use a separate local production-mode frontend.
They establish UI behaviour, not real provider accuracy or manager authorization.
No physical-device or installed screen-reader acceptance is claimed.

## Numbered implementation checklist

| Item | Implementation / source | Verification coverage |
| --- | --- | --- |
| 1 | [Date-only weekday operands](../../frontend/components/dashboard/business-kpi-grid.tsx) | Summer, winter, both DST transitions, adjacent-day distractors, missing values and zero. |
| 2 | [Named issue controls](../../frontend/components/data-display/data-qualifications.tsx), affected aggregate wrappers and Settings | Default hidden values; independent mixed notices; hover/focus summary; clickable summary; Sheet scope/evidence; Back/reload/Escape and focus at 1440/390px. |
| 3 | [Reviews regions](../../frontend/app/(app)/reviews/page.tsx), [QueryRegion](../../frontend/components/states/query-region.tsx) | Each secondary endpoint fails independently; stale values qualified; permission denial suppresses retained data; successful empty remains distinct. |
| 4 | [Rules](../../frontend/components/rules/resolution-rules-content.tsx) | Independent history, recompute and product-option states; targeted retry; shared denied/empty regression. |
| 5 | [Invoice filters](../../frontend/app/(app)/invoices/page.tsx) | Complete supplier typed during delayed requests; focus retained; filter edit recovers from failure. |
| 6 | [Raw records](../../frontend/app/(app)/data/page.tsx), [Logs](../../frontend/app/(app)/logs/page.tsx) | Named buttons, expanded/controls, Enter/Space, equivalent payload/stack evidence. |
| 7 | [DataTable](../../frontend/components/data-table/data-table.tsx) | Actual header aria-sort, ascending/descending, pending rows suppressed; numeric/null/undefined ordering across pages. |
| 8 | [Period selector](../../frontend/components/ui/time-range-selector.tsx) | Preset pressed state, inclusive full-date accessible label, cross-year visible years, enlarged-text wrapping. |
| 9 | [App shell](../../frontend/components/app-shell/app-shell.tsx) | First Tab then Enter focuses route content on Home, Weekly report and Invoices. |
| 10 | [Invoice URL state](../../frontend/app/(app)/invoices/page.tsx), [full-detail return](../../frontend/app/(app)/invoices/[invoiceNumber]/page.tsx) | Supplier/date/page/sort retained through panel, full page, return, reload and history; invoking focus restored. |
| 11 | [Sales detail](../../frontend/app/(app)/sales-detail/page.tsx), [receipt](../../frontend/components/sales-detail/sale-summary-view.tsx) | URL tab/range/sale/filter/sort/page, right Sheet, Back/Forward/reload/close. Receipt, association and costing reads share the selected dates. |
| 12 | [Typed sorting](../../frontend/lib/record-sort.ts), [backend RecordSort](../../backend/src/main/java/com/goldys/platform/semantic/RecordSort.java) and inventory above | Full matching scope before paging; explicit field maps; >2 PostgreSQL pages, stable duplicate ties, nulls/zero, filtering, invalid keys; matching demo behaviour. |
| 13 | [Time series](../../frontend/components/widgets/time-series-widget.tsx) | Opaque markers for single zero and alternating known/null series; no connected null gaps. |
| 14 | [Top sellers](../../frontend/components/dashboard/top-sellers.tsx), [ranked list](../../frontend/components/widgets/ranked-list-widget.tsx) | Explicit sold/revenue labels and middle-dot separator; generic callers retain their own units. |
| 15 | [Focus tokens](../../frontend/app/globals.css) | Ring contrast: white 6.17:1, muted 5.32:1, sidebar 5.57:1, sidebar accent 5.09:1; keyboard overlay/focus screenshots. |
| 16 | [Feature gains](../../frontend/components/forecasting/feature-importance-chart.tsx) | Named feature/gain table, exact decimal and zero, missing values omitted consistently with chart. |
| 17 | [Home hierarchy](../../frontend/app/(app)/dashboard/page.tsx) | Substantive attention block above KPIs; desktop/narrow screenshots and attention-vs-sales positions. |
| 18 | [Sheet](../../frontend/components/ui/sheet.tsx), [Dialog](../../frontend/components/ui/dialog.tsx) | Reduced-motion override preserves visibility and focus; normal overlay navigation also exercised; obsolete Sales smooth-scroll removed. |
| 19 | [Weekly target cells](../../frontend/app/(app)/weekly-report/report-sections.tsx) | Gap token, local table scrolling, target source notes by keyboard at 1440/390px with 200% text. Section actions wrap and hints stay within viewport. |
| 20 | [Work queue](../../frontend/components/my-work/use-work-queue.ts) | One underlying connector read across cold Home consumers and navigation to My work; existing independent-failure/cancellation tests retained. |

Static review: Impeccable found no issues on its changed-file pass. Ripwire's broad
quality delta reported size, parameter-count and clone warnings across this feature,
including domain allowlists, endpoint parameters and unrelated method matches.
Those are not a clean quality-gate result. The table comparator was extracted to
keep null/identity ordering independently reviewable; explicit domain maps and
existing endpoint signatures were retained instead of a speculative framework.
Invoice and daily-sales ordering retain the existing in-memory complete-read model;
ordinary invoice sorts read PDF evidence only for the selected page, while PDF
ordering necessarily checks availability across the matching population;
no measured performance improvement is claimed.

## Final observed verification

All results below are local, on the isolated implementation worktree.

| Check | Observed result |
| --- | --- |
| `bun run typecheck` | Passed after final test edits. |
| `bun run lint` | Passed after final test edits. |
| `bun run test -- --maxWorkers=2` | 143 files, 829 tests passed. Bounded workers avoided a load-sensitive auth-test timeout seen with the default worker count. |
| `bun run build` | Passed, production build. |
| `./gradlew --no-daemon --no-configuration-cache spotlessApply test spotlessCheck` | Passed: 1,995 tests discovered, 1,990 passed, five existing skips; PostgreSQL 16/Testcontainers. Dedicated daemon/cache flags avoided shared-daemon termination and Spotless's stale JVM-local cache. |
| Selected Playwright suites | 62 passed, one skipped, in 2.1 minutes on the final production build. All API traffic used demo or intercepted fixtures. |
| Documentation | All 27 checklist source links resolve; all 20 numbered requirements retained; inventory linked from docs index. |
| Impeccable changed-file detector | No findings on its pass. |
| Ripwire final quality delta | Exit 2: 62 gating size/complexity/parameter/clone findings; not a clean static gate. Findings and scope tradeoffs described above. |
| Git whitespace check | Passed. |

Browser command (from `frontend/`):

```sh
PLAYWRIGHT_PORT=3219 bunx playwright test tests/browser/ui-handoff.spec.ts tests/browser/weekly-report.spec.ts tests/browser/audit-regressions.spec.ts tests/browser/ui-small-fixes.spec.ts tests/browser/ux-navigation.spec.ts tests/browser/ux-recovery.spec.ts tests/browser/ux-trust.spec.ts
```

The skipped browser case requires a retained real-week report artifact. Backend
skips are the live LLM evaluation, two CTB live-access cases, invoice LLM extraction
evaluation and a retained Lightspeed report-coverage measurement. Screen readers,
physical touch devices, production data/provider accuracy, real manager permission
mapping and performance benchmarks were not checked. Desktop/narrow Chromium,
keyboard access, 200% text, normal/reduced motion and missing/error/denied states
were checked. No production operation, push, PR publication, CI run or deployment
was performed. Publication requires the user's explicit approval.
