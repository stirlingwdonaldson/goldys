-- T01: Canonical version chains and business-vs-system-time implementation.
WITH v AS (
 SELECT 'daily_sales' AS domain,source_system,source_record_ref,recorded_at,superseded_at,valid_from,valid_to,raw_record_id FROM canonical_daily_sales
 UNION ALL SELECT 'product_sales',source_system,source_record_ref,recorded_at,superseded_at,valid_from,valid_to,raw_record_id FROM canonical_product_sales
 UNION ALL SELECT 'invoice',source_system,source_record_ref,recorded_at,superseded_at,valid_from,valid_to,raw_record_id FROM canonical_invoice
 UNION ALL SELECT 'invoice_line',source_system,source_record_ref,recorded_at,superseded_at,valid_from,valid_to,raw_record_id FROM canonical_invoice_line
), chains AS (SELECT *,lead(recorded_at) OVER(PARTITION BY domain,source_system,source_record_ref ORDER BY recorded_at) AS next_recorded FROM v)
SELECT domain,count(*) AS versions,
 count(*) FILTER(WHERE valid_from=recorded_at) AS valid_equals_recorded,
 count(*) FILTER(WHERE valid_to IS NULL) AS open_valid_to,
 count(*) FILTER(WHERE superseded_at<recorded_at) AS invalid_system_intervals,
 count(*) FILTER(WHERE next_recorded IS NOT NULL AND superseded_at IS DISTINCT FROM next_recorded) AS discontinuous_chains,
 count(*) FILTER(WHERE r.id IS NULL) AS orphan_raw_refs
FROM chains c LEFT JOIN raw_record r ON r.id=c.raw_record_id GROUP BY domain ORDER BY domain;
-- T02: Date-presence coverage; not expected source completeness (venue calendar unknown).
SELECT source_system,count(*) AS current_rows,count(DISTINCT trading_date) AS present_dates,
 max(trading_date)-min(trading_date)+1 AS calendar_span,
 (max(trading_date)-min(trading_date)+1)-count(DISTINCT trading_date) AS absent_calendar_dates
FROM canonical_daily_sales WHERE superseded_at IS NULL GROUP BY 1;
-- T03: Source disagreement hidden by resolution; compare to frontend source-summing algorithm.
WITH c AS (SELECT trading_date,count(*) AS sources,sum(total_sales) AS frontend_trend,
 max(total_sales)-min(total_sales) AS source_delta FROM canonical_daily_sales WHERE superseded_at IS NULL GROUP BY 1)
SELECT count(*) FILTER(WHERE sources>1) AS multisource_dates,
 count(*) FILTER(WHERE sources>1 AND source_delta>0.01) AS differing_source_dates,
 count(*) FILTER(WHERE sources>1 AND frontend_trend IS DISTINCT FROM r.total_sales) AS frontend_resolved_different_dates,
 count(*) FILTER(WHERE r.has_conflict) AS unresolved_conflicts,
 count(*) FILTER(WHERE sources>1 AND r.resolution_type='rule') AS rule_chosen_dates,
 count(*) FILTER(WHERE sources>1 AND r.resolution_type='override') AS override_chosen_dates
FROM c JOIN resolved_daily_sales r USING(trading_date);
-- T04: Recent domain freshness versus unrelated connector activity.
SELECT source_system,connector_name,status,max(started_at) AS last_run,max(completed_at) AS last_complete
FROM ingestion_run GROUP BY 1,2,3 ORDER BY 1,2,3;
-- T05: Read permissions only; no user names or credentials.
SELECT department,seniority,resource,can_read,can_write FROM permission ORDER BY 1,2,3;
-- T06: Stored widget query shapes, not dashboard titles, creators or conversations.
SELECT count(*) AS dashboards,sum(jsonb_array_length(widgets)) AS widgets,
 count(*) FILTER(WHERE filters='{}'::jsonb) AS empty_filter_documents FROM saved_dashboard;
SELECT DISTINCT jsonb_object_keys(w) AS widget_field FROM saved_dashboard d CROSS JOIN LATERAL jsonb_array_elements(d.widgets) w ORDER BY 1;
-- T07: Source fact re-dating versus line/header identities.
WITH changes AS (SELECT source_record_ref,count(DISTINCT invoice_date) AS dates FROM canonical_invoice_line GROUP BY 1)
SELECT count(*) AS line_identities,count(*) FILTER(WHERE dates>1) AS identities_with_date_changes FROM changes;
-- T08: Latest Friday (venue date at audit = 2026-10-09; previous Friday = 2026-10-02).
SELECT '2026-10-02'::date AS previous_friday,
 (SELECT count(*) FROM canonical_daily_sales WHERE trading_date='2026-10-02' AND superseded_at IS NULL) AS daily_sources,
 (SELECT count(*) FROM canonical_product_sales WHERE trading_date='2026-10-02' AND superseded_at IS NULL) AS product_days,
 (SELECT count(*) FROM canonical_sale_item) AS sale_item_rows,
 (SELECT count(*) FROM canonical_shift) AS shift_rows,
 (SELECT count(*) FROM canonical_reservation) AS reservation_rows;
