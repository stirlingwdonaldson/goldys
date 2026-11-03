-- Source-reported sale costs, never today's recipe cost or invoice purchases.
CREATE VIEW resolved_reporting_cost AS
WITH observations AS (
 SELECT f.*,
   regexp_replace(btrim(regexp_replace(lower(product_name),'[^a-z0-9]+',' ','g')),'^new ','') name_key,
   -- One cent permits rounding of the retained four-decimal source measures, not identity matching.
   cost_per_unit IS NOT NULL AND quantity_sold IS NOT NULL AND total_food_cost IS NOT NULL
     AND cost_per_unit >= 0
     AND ((quantity_sold <> 0 AND cost_per_unit > 0) OR (quantity_sold=0 AND amount=0 AND total_food_cost=0))
     AND (amount=0 OR sign(amount)=sign(quantity_sold))
     AND (total_food_cost=0 OR sign(total_food_cost)=sign(quantity_sold))
     AND abs(quantity_sold*cost_per_unit-total_food_cost)<=0.01 AS cost_supported
 FROM source_product_sale_fact f WHERE superseded_at IS NULL AND source_system='CTB'
), totals AS (
 SELECT trading_date,name_key,sum(amount) gross,sum(quantity_sold) quantity,
   bool_and(amount IS NOT NULL AND quantity_sold IS NOT NULL AND site_number IS NOT NULL)
     AND count(DISTINCT site_number)=1 AS qualified_scope,
   count(*) cost_lines,count(*) FILTER (WHERE cost_supported) costed_lines,
   sum(total_food_cost) FILTER (WHERE cost_supported) known_cost
 FROM observations GROUP BY trading_date,name_key
)
SELECT s.trading_date,s.product_name_key,t.known_cost,t.cost_lines,t.costed_lines
FROM resolved_product_sales s JOIN totals t ON t.trading_date=s.trading_date AND t.name_key=s.product_name_key
WHERE s.authoritative_source='CTB' AND t.qualified_scope AND t.gross=s.amount AND t.quantity=s.quantity_sold;
