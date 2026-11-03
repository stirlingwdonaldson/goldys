SELECT trading_date,count(*) observations,
 sum(net_amount) filter(where sales_nature='FOOD') food_ex_gst,
 sum(net_amount) filter(where sales_nature='BEVERAGE') beverage_ex_gst,
 sum(net_amount) filter(where sales_nature='UNKNOWN') unclassified_ex_gst,
 count(*) filter(where net_amount is null) missing_basis
FROM resolved_reporting_sales WHERE trading_date between '2026-10-26' and '2026-11-03' GROUP BY 1 ORDER BY 1;
SELECT source_record_ref,wastage_date,quantity,source_unit_cost,recipe_id,reason,mapping_scope,sales_nature
FROM resolved_classified_wastage WHERE source_record_ref in ('wastage:171:914','wastage:171:915');
SELECT count(*) unknown_lines,sum(quantity*source_unit_cost) known_value FROM resolved_classified_wastage
WHERE (wastage_date between '2026-11-02' and '2026-11-03' OR wastage_date is null) AND sales_nature='UNKNOWN';
