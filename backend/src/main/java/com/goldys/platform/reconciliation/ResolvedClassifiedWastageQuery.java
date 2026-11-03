package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.ClassifiedWastageQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ResolvedClassifiedWastageQuery implements ClassifiedWastageQuery {
  private final JdbcTemplate jdbc;

  public ResolvedClassifiedWastageQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Line> lines(LocalDate from, LocalDate to, int offset, int limit) {
    return jdbc.query(
        """
        select * from resolved_classified_wastage where wastage_date between ? and ?
        order by wastage_date desc, source_system, source_record_ref limit ? offset ?
        """,
        (rs, n) ->
            new Line(
                rs.getString("source_system"),
                rs.getString("source_record_ref"),
                rs.getObject("raw_record_id", UUID.class),
                rs.getObject("wastage_date", LocalDate.class),
                rs.getString("product_name_key"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("source_unit_cost"),
                rs.getString("unit"),
                rs.getString("unit_size"),
                rs.getString("reason"),
                rs.getString("supplier_name"),
                rs.getObject("recipe_id", Integer.class),
                rs.getObject("mapping_revision_id", Long.class),
                rs.getString("mapping_scope"),
                rs.getString("mapping_reason"),
                rs.getString("sales_nature"),
                rs.getString("stock_bucket"),
                rs.getString("item_role"),
                rs.getString("business_area"),
                rs.getString("lifecycle")),
        from,
        to,
        limit,
        offset);
  }
}
