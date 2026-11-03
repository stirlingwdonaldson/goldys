package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.ReportingReviewQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ResolvedReportingReviewQuery implements ReportingReviewQuery {
  private final JdbcTemplate jdbc;

  public ResolvedReportingReviewQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SALES =
      """
      select scope,source_system,entity_key,
        string_agg(distinct observed_name,' | ') names,null::integer recipe_id,
        min(trading_date) first_date,max(trading_date) last_date,count(*) observations,
        sum(abs(net_amount)) filter(where (%s)) known_exposure,
        sum(abs(gross_amount)) filter(where missing_tax or unresolved) missing_basis_gross,
        count(*) filter(where missing_tax) missing_basis,
        count(*) filter(where unresolved) unresolved,
        string_agg(distinct coalesce(mapping_scope || ' #' || mapping_id || ' ' || policy_nature ||
          ' from ' || effective_from || ': ' || policy_reason,'No applicable policy'), E'\\n') policies,
        string_agg(distinct hold_reason,E'\\n') holds,
        left(string_agg(distinct evidence,E'\\n'),16000) || E'\\nEvidence excerpt (16000 character limit); review source references before approval.' evidence
      from resolved_reporting_review_sale
      where trading_date between ? and ?
      group by scope,source_system,entity_key having bool_or(%s)
      order by known_exposure desc nulls last,missing_basis_gross desc nulls last,
        scope,source_system,entity_key limit ? offset ?
      """;

  private static final String WASTE =
      """
      select 'WASTE_LINE' scope,source_system,source_record_ref entity_key,
        string_agg(distinct product_name_key,' | ') names,max(w.recipe_id) recipe_id,
        min(wastage_date) first_date,max(wastage_date) last_date,count(*) observations,
        sum(abs(round(quantity*source_unit_cost,4))) known_exposure,
        null::numeric missing_basis_gross,
        count(*) filter(where quantity is null or source_unit_cost is null) missing_basis,
        count(*) filter(where wastage_date is null) unresolved,
        string_agg(distinct coalesce(mapping_scope || ' #' || mapping_revision_id || ' ' || sales_nature ||
          ' from ' || (select effective_from from product_reporting_mapping p where p.id=w.mapping_revision_id) || ': ' || mapping_reason,'No applicable policy'),E'\\n') policies,
        concat_ws('; ',case when bool_or(sales_nature='UNKNOWN') then 'No policy or explicit UNKNOWN withdrawal' end,
          case when bool_or(source_unit_cost is null or quantity is null) then 'Missing historical quantity/cost' end,
          case when bool_or(wastage_date is null) then 'Date absent: outside range attribution unavailable' end) holds,
        string_agg(concat('Exact line: ',source_record_ref,'; raw: ',raw_record_id,
          '; recipe: ',coalesce(recipe_id::text,'unavailable'),
          '; definition: ',coalesce(d.definition->'header'->>'name','unavailable'),
          '; recorded quantity: ',quantity,'; unit: ',coalesce(unit,'unknown'),'; size: ',unit_size,
          '; historical source unit cost: ',source_unit_cost,'; reason: ',coalesce(reason,'unrecorded'),
          '; supplier: ',supplier_name,
          '; current recipe evidence (not historical cost): ',left(coalesce(d.definition::text,'unavailable'),12000) || ' [current definition excerpt; 12000 character limit]',
          '; cost tax basis unverified; no stock identity inferred'),E'\\n') evidence
      from resolved_classified_wastage w left join resolved_recipe_definition d using(recipe_id)
      where (wastage_date between ? and ? or wastage_date is null)
        and (sales_nature='UNKNOWN' or source_unit_cost is null or quantity is null or wastage_date is null)
      group by source_system,source_record_ref
      order by known_exposure desc nulls last,source_system,source_record_ref limit ? offset ?
      """;

  @Override
  public List<Item> items(Domain domain, LocalDate from, LocalDate to, int offset, int limit) {
    String condition =
        domain == Domain.SALES_BASIS
            ? "missing_tax or unresolved"
            : "sales_nature='UNKNOWN' and not missing_tax and not unresolved";
    return jdbc.query(
        domain == Domain.WASTAGE ? WASTE : SALES.formatted(condition, condition),
        (rs, n) ->
            new Item(
                rs.getString("scope"),
                rs.getString("source_system"),
                rs.getString("entity_key"),
                rs.getString("names"),
                rs.getObject("recipe_id", Integer.class),
                rs.getObject("first_date", LocalDate.class),
                rs.getObject("last_date", LocalDate.class),
                rs.getLong("observations"),
                rs.getBigDecimal("known_exposure"),
                rs.getBigDecimal("missing_basis_gross"),
                rs.getLong("missing_basis"),
                rs.getLong("unresolved"),
                rs.getString("policies"),
                rs.getString("holds"),
                rs.getString("evidence")),
        from,
        to,
        limit,
        offset);
  }

  @Override
  public WasteCoverage wasteCoverage(LocalDate from, LocalDate to) {
    return jdbc.queryForObject(
        """
      select count(*) lines,count(*) filter(where sales_nature<>'UNKNOWN' and wastage_date is not null) classified,
        count(*) filter(where nullif(btrim(reason),'') is not null) reasons,
        count(*) filter(where quantity is null or source_unit_cost is null) missing_cost,
        count(*) filter(where wastage_date is null) missing_date,
        sum(round(quantity*source_unit_cost,4)) known_value,
        case when count(*)=0 then null
          when count(*) filter(where sales_nature='UNKNOWN')=0 then 0
          else sum(round(quantity*source_unit_cost,4)) filter(where sales_nature='UNKNOWN') end unknown_value
      from resolved_classified_wastage where wastage_date between ? and ? or wastage_date is null
      """,
        (rs, n) ->
            new WasteCoverage(
                rs.getLong("lines"),
                rs.getLong("classified"),
                rs.getLong("reasons"),
                rs.getLong("missing_cost"),
                rs.getLong("missing_date"),
                rs.getBigDecimal("known_value"),
                rs.getBigDecimal("unknown_value")),
        from,
        to);
  }
}
