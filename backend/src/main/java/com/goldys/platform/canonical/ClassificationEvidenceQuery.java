package com.goldys.platform.canonical;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Source observations for classification discovery, never a financial reporting query. */
@Service
public class ClassificationEvidenceQuery {
  public record Evidence(
      String name, String scope, String identity, String text, boolean ambiguous) {
    public Evidence(String name, String scope, String identity, String text) {
      this(name, scope, identity, text, false);
    }
  }

  private final JdbcTemplate jdbc;

  public ClassificationEvidenceQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Evidence> currentEvidence() {
    List<Evidence> result = new ArrayList<>();
    result.addAll(
        jdbc.query(
            """
        select source_system, source_record_ref, name, category, raw_record_id, recorded_at
        from source_product where superseded_at is null
        order by source_system, source_record_ref
        """,
            (rs, n) ->
                new Evidence(
                    rs.getString("name"),
                    "catalog:" + rs.getString("source_system"),
                    rs.getString("source_record_ref"),
                    "Catalog observation: "
                        + rs.getString("source_system")
                        + "/"
                        + rs.getString("source_record_ref")
                        + "; category="
                        + rs.getString("category")
                        + "; observed="
                        + rs.getString("recorded_at")
                        + "; raw="
                        + rs.getString("raw_record_id"))));
    result.addAll(
        jdbc.query(
            """
        select source_system, site_number, stock_code, product_name,
          min(trading_date) first_seen, max(trading_date) last_seen, count(*) observations
        from source_product_sale_fact where superseded_at is null
        group by source_system, site_number, stock_code, product_name
        order by source_system, site_number, stock_code, product_name
        """,
            (rs, n) ->
                new Evidence(
                    rs.getString("product_name"),
                    "sales:" + rs.getString("source_system") + ":" + rs.getString("site_number"),
                    rs.getString("stock_code"),
                    "Source sale observation: "
                        + rs.getString("source_system")
                        + "; site="
                        + rs.getString("site_number")
                        + "; stock="
                        + rs.getString("stock_code")
                        + "; first/last="
                        + rs.getString("first_seen")
                        + "/"
                        + rs.getString("last_seen")
                        + "; observations="
                        + rs.getLong("observations"))));
    result.addAll(
        jdbc.query(
            """
        select source_system, item_name, min(trading_date) first_seen, max(trading_date) last_seen,
          string_agg(distinct category_name, ' | ' order by category_name) categories,
          count(distinct nullif(btrim(product_number),'')) product_ids, min(nullif(btrim(product_number),'')) single_product_id, count(distinct site_number) sites,
          count(distinct register_name) registers
        from canonical_sale_item where superseded_at is null
        group by source_system,item_name order by source_system,item_name
        """,
            (rs, n) ->
                new Evidence(
                    rs.getString("item_name"),
                    "receipt:" + rs.getString("source_system"),
                    rs.getLong("product_ids") == 1 ? rs.getString("single_product_id") : "",
                    "Receipt observations: first/last="
                        + rs.getString("first_seen")
                        + "/"
                        + rs.getString("last_seen")
                        + "; source categories="
                        + rs.getString("categories")
                        + "; product IDs="
                        + rs.getLong("product_ids")
                        + "; sites="
                        + rs.getLong("sites")
                        + "; registers="
                        + rs.getLong("registers"),
                    rs.getLong("product_ids") > 1 || rs.getLong("sites") > 1)));
    result.addAll(
        jdbc.query(
            """
        select source_system, product_name_key, source_product_ref,
          min(trading_date) first_seen, max(trading_date) last_seen
        from canonical_product_sales where superseded_at is null and source_product_ref is not null
        group by source_system, product_name_key, source_product_ref
        order by source_system, product_name_key, source_product_ref
        """,
            (rs, n) ->
                new Evidence(
                    rs.getString("product_name_key"),
                    "qualified:" + rs.getString("source_system"),
                    rs.getString("source_product_ref"),
                    "Qualified source identity="
                        + rs.getString("source_system")
                        + "/"
                        + rs.getString("source_product_ref")
                        + "; first/last="
                        + rs.getString("first_seen")
                        + "/"
                        + rs.getString("last_seen"))));
    result.addAll(
        jdbc.query(
            """
        select distinct l.sale_item_stock_description, l.recipe_id, d.active, d.folder_id,
          d.recorded_at, d.raw_record_id, left(d.menu_description,1000) menu_description,
          (select string_agg(x.description || ' [stock=' || x.stock_id::text || '; supplier=' || coalesce(x.supplier_name,'unknown') || ']', ' | ')
           from (select s.description,s.stock_id,s.supplier_name from canonical_recipe_definition_stocks s
                 where s.snapshot_id=d.id order by s.line_order limit 8) x) ingredients
        from canonical_sale_recipe_link l join canonical_recipe_definition d
          on d.recipe_id=l.recipe_id and d.source_system=l.source_system and d.superseded_at is null
        where l.superseded_at is null and l.recipe_id>0
        order by l.sale_item_stock_description,l.recipe_id
        """,
            (rs, n) ->
                new Evidence(
                    rs.getString("sale_item_stock_description"),
                    "recipe-definition",
                    "",
                    "Recipe="
                        + rs.getInt("recipe_id")
                        + "; observed active="
                        + rs.getBoolean("active")
                        + "; folder ID="
                        + rs.getString("folder_id")
                        + "; observed="
                        + rs.getString("recorded_at")
                        + "; raw="
                        + rs.getString("raw_record_id")
                        + "; menu description="
                        + rs.getString("menu_description")
                        + "; ingredients (first 8 by source order)="
                        + rs.getString("ingredients"))));
    return result;
  }
}
