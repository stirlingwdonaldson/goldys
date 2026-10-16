-- I01: Daily sales gross/net/GST consistency per source.
SELECT source_system,count(*) AS current_rows,count(DISTINCT trading_date) AS dates,
       min(trading_date) AS first,max(trading_date) AS last,
       count(*) FILTER (WHERE abs(total_sales-net_total-gst_total)>0.01) AS gross_net_gst_mismatch,
       count(*) FILTER (WHERE total_sales<0) AS negative_gross,
       count(*) FILTER (WHERE total_sales=0) AS zero_gross
FROM canonical_daily_sales WHERE superseded_at IS NULL GROUP BY 1 ORDER BY 1;
-- I02: Invoice header completeness and tax consistency.
SELECT count(*) AS headers,
       count(*) FILTER (WHERE invoice_date< '2020-01-01') AS old_dates,
       count(*) FILTER (WHERE invoice_date> current_date) AS future_dates,
       count(*) FILTER (WHERE total_amount IS NULL) AS total_missing,
       count(*) FILTER (WHERE amount_ex_tax IS NULL) AS ex_tax_missing,
       count(*) FILTER (WHERE gst_amount IS NULL) AS gst_missing,
       count(*) FILTER (WHERE freight_amount IS NULL) AS freight_missing,
       count(*) FILTER (WHERE account_number IS NULL) AS account_missing,
       count(*) FILTER (WHERE tax_code IS NULL) AS tax_code_missing,
       count(*) FILTER (WHERE pdf_filename IS NULL) AS pdf_missing,
       count(*) FILTER (WHERE abs(total_amount-amount_ex_tax-gst_amount)>0.01) AS header_tax_mismatch
FROM canonical_invoice WHERE superseded_at IS NULL;
-- I03: Invoice line completeness and arithmetic.
SELECT count(*) AS lines,
       count(*) FILTER (WHERE quantity=0) AS quantity_zero,
       count(*) FILTER (WHERE unit_cost=0) AS unit_cost_zero,
       count(*) FILTER (WHERE abs(quantity*unit_cost-line_total)>0.01) AS quantity_cost_mismatch,
       count(*) FILTER (WHERE stock_code IS NULL OR stock_code='') AS stock_missing,
       count(*) FILTER (WHERE category IS NULL) AS category_missing,
       count(*) FILTER (WHERE uom IS NULL) AS uom_missing,
       count(*) FILTER (WHERE unit_quantity IS NULL) AS unit_quantity_missing,
       count(*) FILTER (WHERE pack_size IS NULL) AS pack_size_missing,
       count(*) FILTER (WHERE wet_amount IS NULL) AS wet_missing,
       count(*) FILTER (WHERE line_total<0) AS negative_lines
FROM canonical_invoice_line WHERE superseded_at IS NULL;
-- I04: Header/line join safety (invoice-number key).
WITH h AS (SELECT invoice_number,count(*) AS n FROM canonical_invoice WHERE superseded_at IS NULL GROUP BY 1),
     l AS (SELECT invoice_number,count(*) AS n,sum(line_total) AS t FROM canonical_invoice_line WHERE superseded_at IS NULL GROUP BY 1)
SELECT (SELECT count(*) FROM h WHERE n>1) AS ambiguous_header_keys,
       (SELECT count(*) FROM h LEFT JOIN l USING(invoice_number) WHERE l.invoice_number IS NULL) AS headers_without_lines,
       (SELECT count(*) FROM l LEFT JOIN h USING(invoice_number) WHERE h.invoice_number IS NULL) AS orphan_lines,
       (SELECT count(*) FROM canonical_invoice i JOIN l USING(invoice_number)
         WHERE i.superseded_at IS NULL AND abs(i.amount_ex_tax-l.t)>0.01) AS header_line_ex_tax_mismatch;
-- I05: Payment / sale-item / deleted-sale population and simple consistency.
SELECT count(*) AS payments,
       count(*) FILTER (WHERE amount<0) AS negative_amount,
       count(*) FILTER (WHERE tip<>0) AS tipped,
       count(DISTINCT trading_date) AS dates
FROM canonical_payment WHERE superseded_at IS NULL;
SELECT count(*) AS sale_items,
       count(*) FILTER (WHERE amount<0) AS negative_amount,
       count(*) FILTER (WHERE category_name IS NULL) AS category_missing,
       count(*) FILTER (WHERE sku IS NULL) AS sku_missing,
       count(DISTINCT trading_date) AS dates
FROM canonical_sale_item WHERE superseded_at IS NULL;
SELECT count(*) AS deleted_sales,
       count(*) FILTER (WHERE total_inc_tax<0) AS negative_total,
       count(DISTINCT trading_date) AS dates
FROM canonical_deleted_sale WHERE superseded_at IS NULL;
