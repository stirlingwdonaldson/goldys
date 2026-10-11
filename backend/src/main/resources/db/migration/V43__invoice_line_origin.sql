-- Whether this invoice line came from the CSV export ("CSV") or was derived from the PDF
-- ("PDF"). Nullable: null marks a legacy row ingested before this column existed, which is
-- always CSV-sourced. No default so existing populated rows stay distinguishable.
alter table canonical_invoice_line add column origin varchar(16);

comment on column canonical_invoice_line.origin is 'CSV or PDF; null = legacy CSV row';
